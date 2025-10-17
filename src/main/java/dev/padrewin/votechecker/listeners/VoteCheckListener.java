package dev.padrewin.votechecker.listeners;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.LocaleManager;
import dev.padrewin.votechecker.setting.SettingKey;
import dev.padrewin.votechecker.util.VoteUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoteCheckListener implements Listener {

    private static final Set<UUID> PASSTHROUGH_ONCE = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!SettingKey.ENABLE_PLUGIN.get()) return;

        Player player = event.getPlayer();

        if (PASSTHROUGH_ONCE.remove(player.getUniqueId())) {
            return;
        }

        String originalMsg = event.getMessage();
        String noSlash = originalMsg.startsWith("/") ? originalMsg.substring(1) : originalMsg;
        String rawLower = noSlash.toLowerCase();

        List<String> blockedCommands = SettingKey.BLOCKED_COMMANDS.get();
        if (blockedCommands == null) blockedCommands = Collections.emptyList();

        LocaleManager localeManager = VoteChecker.getInstance().getManager(LocaleManager.class);

        for (String blocked : blockedCommands) {
            if (rawLower.startsWith(blocked.toLowerCase())) {

                if (player.hasPermission("votechecker.bypass")) {
                    debug("[BYPASS] " + player.getName() + " skipped vote check.");
                    return;
                }

                event.setCancelled(true);

                VoteUtil.hasVotedToday(player).thenAccept(hasVoted -> {
                    if (hasVoted) {
                        debug("[DB] " + player.getName() + " validated (voted today).");
                        Bukkit.getScheduler().runTask(VoteChecker.getInstance(), () -> {
                            boolean ok = player.performCommand(noSlash);

                            if (!ok) {
                                PASSTHROUGH_ONCE.add(player.getUniqueId());
                                try {
                                    player.chat(originalMsg);
                                } finally {
                                    Bukkit.getScheduler().runTask(VoteChecker.getInstance(),
                                            () -> PASSTHROUGH_ONCE.remove(player.getUniqueId()));
                                }
                            }
                        });
                    } else {
                        Bukkit.getScheduler().runTask(VoteChecker.getInstance(), () -> {
                            String message = localeManager.getLocaleMessage("votechecker-not-voted")
                                    .replace("{player}", player.getName())
                                    .replace("{command}", originalMsg);
                            String prefix = localeManager.getLocaleMessage("prefix");
                            player.sendMessage(prefix + message);
                        });
                        debug("[BLOCK] " + player.getName() + " tried to use blocked command without voting.");
                    }
                });
                return;
            }
        }
    }

    private void debug(String msg) {
        if (SettingKey.DEBUG.get()) {
            VoteChecker.getInstance().getLogger().info("[DEBUG] " + msg);
        }
    }
}
