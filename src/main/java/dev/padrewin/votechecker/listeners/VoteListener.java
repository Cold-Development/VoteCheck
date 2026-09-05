package dev.padrewin.votechecker.listeners;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.util.PlayerUUIDResolver;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class VoteListener implements Listener {

    private final VoteChecker plugin;

    public VoteListener(VoteChecker plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVote(VotifierEvent event) {
        Vote vote = event.getVote();
        String name = vote.getUsername();

        if (name == null || name.isBlank()) {
            return;
        }

        String service = vote.getServiceName() != null
                ? vote.getServiceName()
                : "unknown";

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Playerul e online, în usercache, sau serverul e offline mode: fără rețea.
        UUID uuid = PlayerUUIDResolver.resolveWithoutNetwork(name);
        if (uuid != null) {
            handleVote(uuid, name, service, timestamp);
            return;
        }

        // Nume necunoscut pe un server online mode: rezolvarea lovește API-ul
        // Mojang și blochează tick-ul, așa că o mutăm de pe thread-ul principal.
        if (!plugin.isEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID resolved = PlayerUUIDResolver.resolveBlocking(name);

            if (resolved == null) {
                plugin.getLogger().warning("[VoteChecker] Could not resolve UUID for voter " + name + ", vote not logged.");
                return;
            }

            handleVote(resolved, name, service, timestamp);
        });
    }

    private void handleVote(UUID uuid, String name, String service, String timestamp) {
        // Salvăm votul în DB async.
        plugin.getDatabase().addVoteAsync(uuid, name, service, timestamp);

        // Actualizăm cache-ul imediat, pentru ca playerul să poată progresa instant.
        if (plugin.getVoteCacheManager() != null) {
            plugin.getVoteCacheManager().markVoted(uuid);
        }

        if (plugin.getConfig().getBoolean("debug")) {
            plugin.getLogger().info("[DEBUG] Logged vote for "
                    + name + " (" + uuid + ") from "
                    + service + " and updated vote cache.");
        }
    }
}
