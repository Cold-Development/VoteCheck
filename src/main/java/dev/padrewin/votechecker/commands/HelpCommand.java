package dev.padrewin.votechecker.commands;

import java.util.Collections;
import java.util.List;
import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.CommandManager;
import dev.padrewin.votechecker.manager.LocaleManager;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permissible;

public class HelpCommand extends dev.padrewin.votechecker.commands.BaseCommand {

    private final dev.padrewin.votechecker.commands.CommandHandler commandHandler;

    public HelpCommand(dev.padrewin.votechecker.commands.CommandHandler commandHandler) {
        super("help", CommandManager.CommandAliases.HELP);
        this.commandHandler = commandHandler;
    }

    @Override
    public void execute(VoteChecker plugin, CommandSender sender, String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);

        // Send header
        localeManager.sendMessage(sender, "command-help-title");

        for (NamedExecutor executor : this.commandHandler.getExecutables())
            if (executor.hasPermission(sender))
                localeManager.sendSimpleMessage(sender, "command-" + executor.getName() + "-description");
    }

    @Override
    public List<String> tabComplete(VoteChecker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Permissible permissible) {
        return true;
    }

}