package dev.padrewin.votechecker.manager;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.util.VoteUtil;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoteCacheManager {

    private final VoteChecker plugin;

    private final Set<UUID> votedCache = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> notVotedCache = new ConcurrentHashMap<>();
    private final Set<UUID> checkingCache = ConcurrentHashMap.newKeySet();

    // Cât timp ținem minte că playerul NU a votat.
    // Asta previne spam-ul de query-uri când un nevotant minează mult.
    private final long notVotedTtlMillis = 2 * 60 * 1000L; // 2 minute

    public VoteCacheManager(VoteChecker plugin) {
        this.plugin = plugin;
    }

    public boolean isVoted(UUID uuid) {
        return uuid != null && votedCache.contains(uuid);
    }

    public boolean isChecking(UUID uuid) {
        return uuid != null && checkingCache.contains(uuid);
    }

    public boolean isTemporarilyNotVoted(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        Long time = notVotedCache.get(uuid);
        if (time == null) {
            return false;
        }

        if (System.currentTimeMillis() - time > notVotedTtlMillis) {
            notVotedCache.remove(uuid);
            return false;
        }

        return true;
    }

    public void checkAsync(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }

        if (isVoted(uuid)) {
            return;
        }

        if (isTemporarilyNotVoted(uuid)) {
            return;
        }

        // Dacă deja există o verificare pornită pentru acest player, nu pornim alta.
        if (!checkingCache.add(uuid)) {
            return;
        }

        VoteUtil.hasVotedToday(uuid, name).thenAccept(hasVoted -> {
            if (hasVoted) {
                markVoted(uuid);
            } else {
                markNotVoted(uuid);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[VoteChecker] Could not validate vote for " + name + ": " + ex.getMessage());

            // Fail closed: dacă verificarea pică, îl considerăm nevotat temporar.
            markNotVoted(uuid);
            return null;
        }).whenComplete((result, throwable) -> {
            checkingCache.remove(uuid);
        });
    }

    public void markVoted(UUID uuid) {
        if (uuid == null) {
            return;
        }

        votedCache.add(uuid);
        notVotedCache.remove(uuid);
        checkingCache.remove(uuid);
    }

    public void markNotVoted(UUID uuid) {
        if (uuid == null) {
            return;
        }

        votedCache.remove(uuid);
        notVotedCache.put(uuid, System.currentTimeMillis());
        checkingCache.remove(uuid);
    }

    public void remove(UUID uuid) {
        if (uuid == null) {
            return;
        }

        votedCache.remove(uuid);
        notVotedCache.remove(uuid);
        checkingCache.remove(uuid);
    }

    public void clear() {
        votedCache.clear();
        notVotedCache.clear();
        checkingCache.clear();
    }
}