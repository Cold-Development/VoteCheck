package dev.padrewin.votechecker.commands;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.CommandManager;
import dev.padrewin.votechecker.manager.LocaleManager;
import dev.padrewin.votechecker.setting.SettingKey;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class InfoCommand extends BaseCommand {

    public InfoCommand() {
        super("info", CommandManager.CommandAliases.INFO);
    }

    @Override
    public void execute(VoteChecker plugin, CommandSender sender, String[] args) {
        LocaleManager locale = plugin.getManager(LocaleManager.class);

        if (!sender.hasPermission("votechecker.info")) {
            locale.sendMessage(sender, "no-permission");
            return;
        }

        boolean votifierHooked = Bukkit.getPluginManager().isPluginEnabled("Votifier");
        boolean hasVotes = !plugin.getDatabase().isEmpty();
        boolean pluginEnabled = SettingKey.ENABLE_PLUGIN.get();
        int blockedCount = SettingKey.BLOCKED_COMMANDS.get().size();

        String baseColor = locale.getLocaleMessage("base-command-color");

        locale.sendCustomMessage(sender, baseColor + "");
        locale.sendCustomMessage(sender, baseColor + "<g:#635AA7:#E6D4F8:#9E48F6>VoteChecker");
        locale.sendCustomMessage(sender, baseColor + "Votifier hook: " + (votifierHooked ? "&a✔" : "&c✘"));
        locale.sendCustomMessage(sender, baseColor + "Database: " + (hasVotes ? "&aActive" : "&eEmpty"));
        locale.sendCustomMessage(sender, baseColor + "Plugin status: " + (pluginEnabled ? "&aenabled" : "&cdisabled"));
        locale.sendCustomMessage(sender, baseColor + "Blocked commands: &f" + blockedCount);

        if (blockedCount > 0) {
            locale.sendCustomMessage(sender, baseColor + " ");
            for (String cmd : SettingKey.BLOCKED_COMMANDS.get()) {
                locale.sendCustomMessage(sender, baseColor + "  &7- &f" + cmd);
            }
        }

        locale.sendCustomMessage(sender, baseColor + "");
    }

    @Override
    public List<String> tabComplete(VoteChecker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
