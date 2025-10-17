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

    private void connect() {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) folder.mkdirs();

            String path = folder.getAbsolutePath() + File.separator + "votes.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);

            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA busy_timeout=5000");
            }

            plugin.getLogger().info("Connected to SQLite database (votes.db).");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTable() {
        String create = "CREATE TABLE IF NOT EXISTS votes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "service_name TEXT NOT NULL," +
                "vote_time TEXT NOT NULL" +
                ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(create);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create votes table: " + e.getMessage());
        }
    }

    public void addVoteAsync(UUID uuid, String playerName, String serviceName, String timestamp) {
        CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO votes (player_uuid, player_name, service_name, vote_time) VALUES (?, ?, ?, ?)";
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
            String query = "SELECT vote_time FROM votes WHERE player_uuid = ? ORDER BY vote_time DESC LIMIT 1";

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (!rs.next()) {
                    if (plugin.getConfig().getBoolean("debug")) {
                        plugin.getLogger().info("[DEBUG] " + playerName + " has no votes on record ❌");
                    }
                    return false;
                }

                String rawTime = rs.getString("vote_time");
                java.time.LocalDateTime voteDateTime;
                try {
                    voteDateTime = java.time.LocalDateTime.parse(rawTime);
                } catch (Exception ex) {
                    if (plugin.getConfig().getBoolean("debug")) {
                        plugin.getLogger().warning("[DEBUG] Bad vote_time format: " + rawTime + " → treating as not voted.");
                    }
                    return false;
                }
                ZoneId zone = resolveZoneIdSafe(SettingKey.VOTE_RESET_TIMEZONE.get());
                long voteMillis = voteDateTime.atZone(zone).toInstant().toEpochMilli();
                long nowMillis = java.time.ZonedDateTime.now(zone).toInstant().toEpochMilli();

                String resetRule = SettingKey.VOTE_RESET.get();
                boolean isDuration = isDurationFormat(resetRule);
                boolean isDailyTime = isDailyTimeFormat(resetRule);

                boolean valid;

                if (isDuration) {
                    long windowMs = parseDurationMillis(resetRule);
                    long diff = nowMillis - voteMillis;
                    valid = diff <= windowMs;

                    if (plugin.getConfig().getBoolean("debug")) {
                        long hours = diff / 3_600_000;
                        long minutes = (diff % 3_600_000) / 60_000;
                        long seconds = (diff % 60_000) / 1000;
                        String timeAgo = String.format("%dh %dm %ds", hours, minutes, seconds);
                        plugin.getLogger().info("[DEBUG] mode=ROLLING(" + resetRule + ", tz=" + zone + ") " + playerName +
                                " last vote: " + rawTime + " (" + timeAgo + " ago) → " + (valid ? "VALID ✅" : "EXPIRED ❌"));
                    }
                } else if (isDailyTime) {
                    java.time.LocalTime resetAt = java.time.LocalTime.parse(padDailyTime(resetRule));
                    long lastResetMillis = computeLastResetMillis(resetAt, zone);
                    valid = voteMillis >= lastResetMillis;

                    if (plugin.getConfig().getBoolean("debug")) {
                        java.time.Instant lastResetInstant = java.time.Instant.ofEpochMilli(lastResetMillis);
                        plugin.getLogger().info("[DEBUG] mode=DAILY(" + resetAt + ", tz=" + zone + ") lastReset=" + lastResetInstant +
                                " | " + playerName + " last vote: " + rawTime + " → " + (valid ? "VALID ✅" : "EXPIRED ❌"));
                    }
                } else {
                    long diff = nowMillis - voteMillis;
                    valid = diff <= 86_400_000L;
                    if (plugin.getConfig().getBoolean("debug")) {
                        plugin.getLogger().warning("[DEBUG] vote-reset='" + resetRule + "' invalid. Falling back to 24h (tz=" + zone + ").");
                    }
                }

                return valid;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to check vote for " + playerName + " (" + uuid + "): " + e.getMessage());
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
