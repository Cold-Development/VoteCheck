package dev.padrewin.votechecker.commands;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.CommandManager;
import dev.padrewin.votechecker.manager.LocaleManager;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class ClearDbCommand extends BaseCommand {

    public ClearDbCommand() {
        super("cleardb", CommandManager.CommandAliases.CLEARDB);
    }

    @Override
    public void execute(VoteChecker plugin, CommandSender sender, String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);

        if (!sender.hasPermission("votechecker.wipe")) {
            localeManager.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length == 0) {
            localeManager.sendMessage(sender, "command-cleardb-warning");
            return;
        }

        if (!args[0].equalsIgnoreCase("confirm") || args.length > 1) {
            localeManager.sendMessage(sender, "invalid-command-usage");
            return;
        }

        plugin.getDatabase().wipeVotesAsync().thenRun(() ->
                localeManager.sendMessage(sender, "command-cleardb-success")
        );
    }

    @Override
    public List<String> tabComplete(VoteChecker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
