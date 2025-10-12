package dev.padrewin.votechecker.listeners;

import dev.padrewin.colddev.utils.StringPlaceholders;
import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.LocaleManager;
import dev.padrewin.votechecker.setting.SettingKey;
import dev.padrewin.votechecker.util.VoteUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

public class VoteCheckListener implements Listener {

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!SettingKey.ENABLE_PLUGIN.get()) return;

        Player player = event.getPlayer();
        String raw = event.getMessage().substring(1).toLowerCase();
        List<String> blockedCommands = SettingKey.BLOCKED_COMMANDS.get();

        LocaleManager localeManager = VoteChecker.getInstance().getManager(LocaleManager.class);

        for (String blocked : blockedCommands) {
            if (raw.startsWith(blocked.toLowerCase())) {

                if (player.hasPermission("votechecker.bypass")) {
                    debug("[BYPASS] " + player.getName() + " skipped vote check.");
                    return;
                }

                event.setCancelled(true);

                VoteUtil.hasVotedToday(player).thenAccept(hasVoted -> {
                    if (hasVoted) {
                        debug("[DB] " + player.getName() + " validated (voted today).");
                        Bukkit.getScheduler().runTask(VoteChecker.getInstance(), () -> player.performCommand(raw));
                    } else {
                        Bukkit.getScheduler().runTask(VoteChecker.getInstance(), () -> {
                            StringPlaceholders placeholders = StringPlaceholders.builder()
                                    .add("{player}", player.getName())
                                    .add("{command}", "/" + raw)
                                    .build();

                            localeManager.sendMessage(player, "votechecker-not-voted", placeholders);
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
