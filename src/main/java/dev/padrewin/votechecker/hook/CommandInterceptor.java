package dev.padrewin.votechecker.hook;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.LocaleManager;
import dev.padrewin.votechecker.setting.SettingKey;
import dev.padrewin.votechecker.util.VoteUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class CommandInterceptor implements Listener {

    private static final List<String> BLOCKED_COMMANDS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Boolean> IS_DELUXE_OPEN = new ThreadLocal<>(); // Flag

    public static void register() {
        Bukkit.getPluginManager().registerEvents(new CommandInterceptor(), VoteChecker.getInstance());
        reloadBlocked();
        VoteChecker.getInstance().getLogger().info("[VoteChecker] Command interceptor activated (DeluxeMenus open_command fixed).");
    }

    public static void reloadBlocked() {
        BLOCKED_COMMANDS.clear();
        List<String> blocked = SettingKey.BLOCKED_COMMANDS.get();
        if (blocked != null) {
            for (String cmd : blocked) {
                if (cmd == null || cmd.trim().isEmpty()) continue;
                String clean = cmd.trim();
                if (clean.startsWith("/")) clean = clean.substring(1);
                BLOCKED_COMMANDS.add(clean.split(" ")[0].toLowerCase(Locale.ROOT));
            }
        }
        if (SettingKey.DEBUG.get()) {
            VoteChecker.getInstance().getLogger().info("[DEBUG] Blocked commands: " + BLOCKED_COMMANDS);
        }
    }

    // === 1. Comenzi din chat + open_command (DeluxeMenus) ===
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        String baseCmd = message.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);

        if (!BLOCKED_COMMANDS.contains(baseCmd)) return;
        if (!SettingKey.ENABLE_PLUGIN.get()) return;
        if (player.hasPermission("votechecker.bypass")) return;

        boolean isDeluxeOpen = Boolean.TRUE.equals(IS_DELUXE_OPEN.get());
        IS_DELUXE_OPEN.remove(); // Reset flag

        debug("[INTERCEPT] " + player.getName() + " → " + message + (isDeluxeOpen ? " [DeluxeMenus open_command]" : ""));

        event.setCancelled(true);

        VoteUtil.hasVotedToday(player).thenAccept(hasVoted -> {
            if (hasVoted) {
                debug("[ALLOW] " + player.getName() + " has voted → " + (isDeluxeOpen ? "opening menu" : "executing") + " /" + baseCmd);
                runLater(() -> {
                    if (isDeluxeOpen) {
                        // Re-executăm ca DeluxeMenus open_command
                        IS_DELUXE_OPEN.set(true);
                        player.chat(message);
                    } else {
                        player.performCommand(message.substring(1));
                    }
                });
            } else {
                sendNotVotedMessage(player, baseCmd);
                debug("[BLOCK] " + player.getName() + " tried " + (isDeluxeOpen ? "to open menu" : "command") + " /" + baseCmd + " without voting");
            }
        });
    }

    // === 2. click_commands: [player] cmd (DeluxeMenus, ItemsAdder, etc.) ===
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().trim();
        if (!command.startsWith("[player] ")) return;

        Player player = extractPlayerFromDeluxeMenu(command);
        if (player == null) return;

        String menuCmd = command.substring(command.indexOf(']') + 1).trim();
        if (menuCmd.isEmpty()) return;

        String baseCmd = menuCmd.split(" ")[0].toLowerCase(Locale.ROOT);
        if (!BLOCKED_COMMANDS.contains(baseCmd)) return;
        if (!SettingKey.ENABLE_PLUGIN.get()) return;
        if (player.hasPermission("votechecker.bypass")) return;

        event.setCancelled(true);
        debug("[INTERCEPT] " + player.getName() + " → [DeluxeMenus click] /" + menuCmd);

        VoteUtil.hasVotedToday(player).thenAccept(hasVoted -> {
            if (hasVoted) {
                debug("[ALLOW] " + player.getName() + " has voted → executing click /" + menuCmd);
                runLater(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "[player] " + player.getName() + " " + menuCmd));
            } else {
                sendNotVotedMessage(player, baseCmd);
                debug("[BLOCK] " + player.getName() + " tried click /" + menuCmd + " without voting");
            }
        });
    }

    private Player extractPlayerFromDeluxeMenu(String command) {
        String rest = command.substring(9).trim(); // "[player] ".length() = 9
        int space = rest.indexOf(' ');
        if (space == -1) return null;
        return Bukkit.getPlayerExact(rest.substring(0, space));
    }

    private void runLater(Runnable task) {
        Bukkit.getScheduler().runTask(VoteChecker.getInstance(), task);
    }

    private void sendNotVotedMessage(Player player, String command) {
        LocaleManager lm = VoteChecker.getInstance().getManager(LocaleManager.class);
        String msg = lm.getLocaleMessage("votechecker-not-voted")
                .replace("{player}", player.getName())
                .replace("{command}", "/" + command);
        player.sendMessage(lm.getLocaleMessage("prefix") + msg);
    }

    private void debug(String msg) {
        if (SettingKey.DEBUG.get()) {
            VoteChecker.getInstance().getLogger().info("[DEBUG] " + msg);
        }
    }

    // === UTIL: Mark as DeluxeMenus open_command ===
    public static void markAsDeluxeOpen() {
        IS_DELUXE_OPEN.set(true);
    }
}