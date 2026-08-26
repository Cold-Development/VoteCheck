package dev.padrewin.votechecker.listeners;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.VoteCacheManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final VoteChecker plugin;

    public PlayerJoinListener(VoteChecker plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        VoteCacheManager cache = plugin.getVoteCacheManager();
        if (cache == null) {
            return;
        }

        cache.checkAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
