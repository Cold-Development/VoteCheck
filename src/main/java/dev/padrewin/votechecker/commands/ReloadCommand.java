package dev.padrewin.votechecker.commands;

import java.util.Collections;
import java.util.List;
import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.hook.CommandInterceptor;
import dev.padrewin.votechecker.manager.CommandManager;
import dev.padrewin.votechecker.manager.LocaleManager;
import org.bukkit.command.CommandSender;

public class ReloadCommand extends dev.padrewin.votechecker.commands.BaseCommand {

    public ReloadCommand() {
        super("reload", CommandManager.CommandAliases.RELOAD);
    }

    @Override
    public void execute(VoteChecker plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("votechecker.reload")) {
            plugin.getManager(LocaleManager.class).sendMessage(sender, "no-permission");
            return;
        }

        if (args.length > 0) {
            plugin.getManager(LocaleManager.class).sendMessage(sender, "command-reload-usage");
            return;
        }

        plugin.reloadConfig();
        plugin.reload();
        CommandInterceptor.reloadBlocked();
        plugin.getManager(LocaleManager.class).sendMessage(sender, "command-reload-success");
    }

    @Override
    public List<String> tabComplete(VoteChecker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
