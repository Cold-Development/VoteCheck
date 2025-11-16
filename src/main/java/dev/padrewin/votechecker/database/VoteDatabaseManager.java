package dev.padrewin.votechecker.database;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.setting.SettingKey;

import java.io.File;
import java.sql.*;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class VoteDatabaseManager {

    private final VoteChecker plugin;
    private Connection connection;

    private final ExecutorService dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VoteDB-Worker");
        t.setDaemon(true);
        return t;
    });

    public VoteDatabaseManager(VoteChecker plugin) {
        this.plugin = plugin;
        connect();
        createTable();
    }

    private String getTableName() {
        boolean isMySQL = SettingKey.DATABASE_TYPE.get().equalsIgnoreCase("MYSQL");
        String prefix = isMySQL ? SettingKey.MYSQL_TABLE_PREFIX.get() : "";
        return prefix + "votes";
    }

    private void connect() {
        String type = SettingKey.DATABASE_TYPE.get().trim().toUpperCase();

        try {
            if (type.equals("MYSQL")) {
                String host = SettingKey.MYSQL_HOST.get();
                int port = SettingKey.MYSQL_PORT.get();
                String db = SettingKey.MYSQL_DATABASE.get();
                String user = SettingKey.MYSQL_USER.get();
                String pass = SettingKey.MYSQL_PASSWORD.get();
                boolean ssl = SettingKey.MYSQL_USE_SSL.get();

                String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + ssl + "&autoReconnect=true";
                connection = DriverManager.getConnection(url, user, pass);
                plugin.getLogger().info("Connected to MySQL database (" + db + ") ✅");
            } else {
                File folder = plugin.getDataFolder();
                if (!folder.exists()) folder.mkdirs();

                String path = folder.getAbsolutePath() + File.separator + "votes.db";
                connection = DriverManager.getConnection("jdbc:sqlite:" + path);

                try (Statement s = connection.createStatement()) {
                    s.execute("PRAGMA journal_mode=WAL");
                    s.execute("PRAGMA busy_timeout=5000");
                }

                plugin.getLogger().info("Connected to SQLite database (votes.db) ✅");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to " + type + " database: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void createTable() {
        boolean isMySQL = SettingKey.DATABASE_TYPE.get().equalsIgnoreCase("MYSQL");
        String table = getTableName();

        String idColumn = isMySQL
                ? "id INT AUTO_INCREMENT PRIMARY KEY"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";

        String create = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                idColumn + "," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(64) NOT NULL," +
                "service_name VARCHAR(128) NOT NULL," +
                "vote_time VARCHAR(64) NOT NULL" +
                (isMySQL ? ", INDEX (player_uuid), INDEX (player_name)" : "") +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(create);
            plugin.getLogger().info("Table '" + table + "' checked/created ✅");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create votes table: " + e.getMessage());
        }
    }


    public void addVoteAsync(UUID uuid, String playerName, String serviceName, String timestamp) {
        CompletableFuture.runAsync(() -> {
            String table = getTableName();
            String query = "INSERT INTO " + table + " (player_uuid, player_name, service_name, vote_time) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, playerName);
                stmt.setString(3, serviceName);
                stmt.setString(4, timestamp);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert vote for " + playerName + ": " + e.getMessage());
            }
        }, dbExecutor);
    }

    public CompletableFuture<Boolean> hasVotedTodayAsync(UUID uuid, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String table = getTableName();
            String query = "SELECT vote_time FROM " + table +
                    " WHERE player_uuid = ? OR LOWER(player_name) = LOWER(?) ORDER BY vote_time DESC LIMIT 1";

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, playerName);
                ResultSet rs = stmt.executeQuery();

                if (!rs.next()) return false;

                String rawTime = rs.getString("vote_time");
                java.time.LocalDateTime voteDateTime = java.time.LocalDateTime.parse(rawTime);

                ZoneId zone = resolveZoneIdSafe(SettingKey.VOTE_RESET_TIMEZONE.get());
                long voteMillis = voteDateTime.atZone(zone).toInstant().toEpochMilli();
                long nowMillis = java.time.ZonedDateTime.now(zone).toInstant().toEpochMilli();

                String resetRule = SettingKey.VOTE_RESET.get();
                boolean isDuration = isDurationFormat(resetRule);
                boolean isDailyTime = isDailyTimeFormat(resetRule);

                if (isDuration) {
                    long windowMs = parseDurationMillis(resetRule);
                    return (nowMillis - voteMillis) <= windowMs;
                } else if (isDailyTime) {
                    java.time.LocalTime resetAt = java.time.LocalTime.parse(padDailyTime(resetRule));
                    long lastResetMillis = computeLastResetMillis(resetAt, zone);
                    return voteMillis >= lastResetMillis;
                } else {
                    return (nowMillis - voteMillis) <= 86_400_000L;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to check vote for " + playerName + ": " + e.getMessage());
                return false;
            }
        }, dbExecutor);
    }

    public CompletableFuture<Boolean> isEmptyAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM votes")) {
                return rs.next() && rs.getInt("count") == 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check database state: " + e.getMessage());
                return false;
            }
        }, dbExecutor);
    }

    public boolean isEmpty() {
        try {
            return isEmptyAsync().join();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to check database state (sync): " + e.getMessage());
            return false;
        }
    }

    public CompletableFuture<Void> wipeVotesAsync() {
        return CompletableFuture.runAsync(() -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DELETE FROM votes");
                plugin.getLogger().info("[VoteChecker] All vote records have been wiped.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to wipe votes: " + e.getMessage());
            }
        }, dbExecutor);
    }

    public CompletableFuture<Integer> migrateAsync(String from, String to) {
        return CompletableFuture.supplyAsync(() -> {
            int migrated = 0;
            try {
                String fromType = from.trim().toUpperCase();
                String toType = to.trim().toUpperCase();

                if (fromType.equals(toType)) {
                    plugin.getLogger().warning("[VoteChecker] Migration skipped: same database type (" + fromType + ")");
                    return 0;
                }

                plugin.getLogger().info("[VoteChecker] Starting migration: " + fromType + " → " + toType + " ...");

                File folder = plugin.getDataFolder();
                String sqlitePath = folder.getAbsolutePath() + File.separator + "votes.db";

                String host = SettingKey.MYSQL_HOST.get();
                int port = SettingKey.MYSQL_PORT.get();
                String db = SettingKey.MYSQL_DATABASE.get();
                String user = SettingKey.MYSQL_USER.get();
                String pass = SettingKey.MYSQL_PASSWORD.get();
                boolean ssl = SettingKey.MYSQL_USE_SSL.get();
                String tablePrefix = SettingKey.MYSQL_TABLE_PREFIX.get();

                Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
                Connection mysql = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db +
                        "?useSSL=" + ssl + "&autoReconnect=true", user, pass);

                String sqliteTable = "votes";
                String mysqlTable = tablePrefix + "votes";

                if (fromType.equals("SQLITE")) {
                    plugin.getLogger().info("→ Migrating local (SQLite) → global (MySQL) ...");
                    migrated = copyTable(sqlite, sqliteTable, mysql, mysqlTable);
                } else {
                    plugin.getLogger().info("→ Migrating global (MySQL) → local (SQLite) ...");
                    migrated = copyTable(mysql, mysqlTable, sqlite, sqliteTable);
                }

                sqlite.close();
                mysql.close();
                plugin.getLogger().info("[VoteChecker] Migration complete → " + migrated + " entries ✅");
                return migrated;

            } catch (Exception e) {
                plugin.getLogger().severe("[VoteChecker] Migration failed: " + e.getMessage());
                e.printStackTrace();
                return 0;
            }
        }, dbExecutor);
    }

    private int copyTable(Connection src, String srcTable, Connection dest, String destTable) throws SQLException {
        int count = 0;
        String select = "SELECT player_uuid, player_name, service_name, vote_time FROM " + srcTable;
        String insert = "INSERT INTO " + destTable + " (player_uuid, player_name, service_name, vote_time) VALUES (?, ?, ?, ?)";

        try (PreparedStatement selectStmt = src.prepareStatement(select);
             ResultSet rs = selectStmt.executeQuery();
             PreparedStatement insertStmt = dest.prepareStatement(insert)) {

            while (rs.next()) {
                String uuid = rs.getString("player_uuid");
                String time = rs.getString("vote_time");

                try (PreparedStatement check = dest.prepareStatement(
                        "SELECT 1 FROM " + destTable + " WHERE player_uuid=? AND vote_time=? LIMIT 1")) {
                    check.setString(1, uuid);
                    check.setString(2, time);
                    if (check.executeQuery().next()) continue;
                }

                insertStmt.setString(1, uuid);
                insertStmt.setString(2, rs.getString("player_name"));
                insertStmt.setString(3, rs.getString("service_name"));
                insertStmt.setString(4, time);
                insertStmt.executeUpdate();
                count++;
            }
        }
        return count;
    }


    public void reconnect() {
        try {
            if (connection != null && !connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException ignored) {}

        closeConnectionOnly();
        connect();
        createTable();
    }

    private void closeConnectionOnly() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    /** De apelat la onDisable() */
    public void shutdown() {
        closeConnectionOnly();
        dbExecutor.shutdown();
    }


    private static boolean isDurationFormat(String s) {
        if (s == null) return false;
        return s.trim().matches("(?i)^\\s*\\d+\\s*(ms|s|m|h|d)\\s*$");
    }

    private static boolean isDailyTimeFormat(String s) {
        if (s == null) return false;
        return s.trim().matches("^\\s*\\d{1,2}:\\d{2}\\s*$");
    }

    private static String padDailyTime(String s) {
        String t = s.trim();
        String[] parts = t.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);
        return String.format("%02d:%02d", hh, mm);
    }

    private static long parseDurationMillis(String s) {
        String t = s.trim().toLowerCase();
        String digits = t.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 86_400_000L; // fallback 24h
        long num = Long.parseLong(digits);

        if (t.endsWith("ms")) return num;
        if (t.endsWith("s"))  return num * 1_000L;
        if (t.endsWith("m"))  return num * 60_000L;
        if (t.endsWith("h"))  return num * 3_600_000L;
        if (t.endsWith("d"))  return num * 86_400_000L;

        return 86_400_000L; // fallback 24h
    }

    private static long computeLastResetMillis(java.time.LocalTime resetAt, ZoneId zone) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zone);
        java.time.ZonedDateTime todayReset = now.toLocalDate().atTime(resetAt).atZone(zone);
        java.time.ZonedDateTime lastReset = now.isBefore(todayReset) ? todayReset.minusDays(1) : todayReset;
        return lastReset.toInstant().toEpochMilli();
    }

    private ZoneId resolveZoneIdSafe(String tzId) {
        try {
            if (tzId != null && !tzId.trim().isEmpty()) {
                return ZoneId.of(tzId.trim());
            }
        } catch (Exception ignored) {
            if (plugin.getConfig().getBoolean("debug")) {
                plugin.getLogger().warning("[DEBUG] Invalid vote-reset-timezone='" + tzId + "'. Using systemDefault().");
            }
        }
        return ZoneId.systemDefault();
    }
}
