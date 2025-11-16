package dev.padrewin.votechecker.commands;

import dev.padrewin.colddev.utils.StringPlaceholders;
import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.CommandManager;
import dev.padrewin.votechecker.manager.LocaleManager;
import dev.padrewin.votechecker.setting.SettingKey;
import org.bukkit.command.CommandSender;

/**
 * Handles the commands for the root command.
 *
 * @author padrewin
 */
public class Commander extends CommandHandler {

    public Commander(VoteChecker plugin) {
        super(plugin, "votechecker", CommandManager.CommandAliases.ROOT);

        // Register commands.
        this.registerCommand(new HelpCommand(this));
        this.registerCommand(new ReloadCommand());
        this.registerCommand(new VersionCommand());
        this.registerCommand(new ClearDbCommand());
        this.registerCommand(new InfoCommand());
        this.registerCommand(new MigrateCommand());

    }

    @Override
    public void noArgs(CommandSender sender) {
        try {
            String redirect = SettingKey.BASE_COMMAND_REDIRECT.get().trim().toLowerCase();
            dev.padrewin.votechecker.commands.BaseCommand command = this.registeredCommands.get(redirect);
            CommandHandler handler = this.registeredHandlers.get(redirect);
            if (command != null) {
                if (!command.hasPermission(sender)) {
                    this.plugin.getManager(LocaleManager.class).sendMessage(sender, "no-permission");
                    return;
                }
                command.execute(this.plugin, sender, new String[0]);
            } else if (handler != null) {
                if (!handler.hasPermission(sender)) {
                    this.plugin.getManager(LocaleManager.class).sendMessage(sender, "no-permission");
                    return;
                }
                handler.noArgs(sender);
            } else {
                VersionCommand.sendInfo(this.plugin, sender);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void unknownCommand(CommandSender sender, String[] args) {
        this.plugin.getManager(LocaleManager.class).sendMessage(sender, "unknown-command", StringPlaceholders.of("input", args[0]));
    }

}