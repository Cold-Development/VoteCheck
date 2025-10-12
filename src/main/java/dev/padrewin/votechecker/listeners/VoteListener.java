package dev.padrewin.votechecker.listeners;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import dev.padrewin.votechecker.VoteChecker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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

        if (name == null || name.isBlank()) return;

        Player player = Bukkit.getPlayerExact(name);
        UUID uuid = player != null ? player.getUniqueId() : Bukkit.getOfflinePlayer(name).getUniqueId();

        String service = vote.getServiceName() != null ? vote.getServiceName() : "unknown";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        plugin.getDatabase().addVoteAsync(uuid, name, service, timestamp);

        if (plugin.getConfig().getBoolean("debug")) {
            plugin.getLogger().info("[DEBUG] Logged vote for " + name + " (" + uuid + ") from " + service);
        }
    }
}
