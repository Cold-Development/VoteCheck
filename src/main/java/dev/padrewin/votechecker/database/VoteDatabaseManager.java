package dev.padrewin.votechecker.database;

import dev.padrewin.votechecker.VoteChecker;
import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VoteDatabaseManager {

    private final VoteChecker plugin;
    private Connection connection;

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
        });
    }

    public CompletableFuture<Boolean> hasVotedTodayAsync(UUID uuid, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT vote_time FROM votes WHERE player_uuid = ? AND player_name = ? ORDER BY vote_time DESC LIMIT 1";

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, playerName);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    Timestamp voteTimestamp = Timestamp.valueOf(rs.getString("vote_time"));
                    long voteMillis = voteTimestamp.toInstant().toEpochMilli();
                    long nowMillis = System.currentTimeMillis();

                    long diff = nowMillis - voteMillis;

                    return diff <= 86_400_000L;
                }

                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check vote for " + playerName + " (" + uuid + "): " + e.getMessage());
                return false;
            }
        });
    }

    public boolean isEmpty() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM votes")) {
            return rs.next() && rs.getInt("count") == 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check database state: " + e.getMessage());
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
        });
    }

    public void reconnect() {
        try {
            if (connection != null && !connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException ignored) {}

        close();
        connect();
        createTable();
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}