package dev.padrewin.votechecker.placeholders;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.util.VoteUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VoteExpansion extends PlaceholderExpansion {

    private final VoteChecker plugin;
    private final Map<UUID, CachedVote> cache = new HashMap<>();
    private static final long CACHE_TIME = 30000; // 30 seconds cache

    public VoteExpansion(VoteChecker plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "votechecker";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ColdDevelopment";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null) {
            return "0";
        }

        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        if (playerName == null) {
            return "0";
        }

        switch (identifier.toLowerCase()) {
            case "has_voted":
                // Check cache first
                CachedVote cached = cache.get(uuid);
                if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TIME) {
                    return cached.hasVoted ? "1" : "0";
                }

                // Query async and cache result (works for both online and offline players)
                VoteUtil.hasVotedToday(uuid, playerName).thenAccept(voted -> {
                    cache.put(uuid, new CachedVote(voted, System.currentTimeMillis()));
                }).exceptionally(ex -> {
                    plugin.getLogger().warning("Error checking vote status for " + playerName + ": " + ex.getMessage());
                    return null;
                });

                // Return cached value or default
                return cached != null ? (cached.hasVoted ? "1" : "0") : "0";

            default:
                return null;
        }
    }

    private static class CachedVote {
        final boolean hasVoted;
        final long timestamp;

        CachedVote(boolean hasVoted, long timestamp) {
            this.hasVoted = hasVoted;
            this.timestamp = timestamp;
        }
    }
}