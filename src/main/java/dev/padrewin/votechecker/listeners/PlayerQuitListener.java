package dev.padrewin.votechecker.listeners;

import dev.padrewin.votechecker.VoteChecker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final VoteChecker plugin;

    public PlayerQuitListener(VoteChecker plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getVoteCacheManager() != null) {
            plugin.getVoteCacheManager().remove(event.getPlayer().getUniqueId());
        }
    }
}