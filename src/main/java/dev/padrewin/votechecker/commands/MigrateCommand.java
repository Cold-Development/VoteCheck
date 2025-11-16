package dev.padrewin.votechecker.commands;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.manager.CommandManager;
import dev.padrewin.votechecker.manager.LocaleManager;
import dev.padrewin.colddev.utils.StringPlaceholders;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MigrateCommand extends BaseCommand {

    public MigrateCommand() {
        super("migrate", CommandManager.CommandAliases.MIGRATE);
    }

    @Override
    public void execute(VoteChecker plugin, CommandSender sender, String[] args) {
        LocaleManager locale = plugin.getManager(LocaleManager.class);

        if (!sender.hasPermission("votechecker.migrate")) {
            locale.sendMessage(sender, "command-migrate-no-permission");
            return;
        }

        if (args.length < 2) {
            locale.sendMessage(sender, "command-migrate-usage");
            return;
        }

        String from = args[0].toLowerCase();
        String to = args[1].toLowerCase();

        if (from.equals(to)) {
            locale.sendMessage(sender, "command-migrate-same-db");
            return;
        }

        locale.sendMessage(sender, "command-migrate-start",
                StringPlaceholders.of("%from%", from.toUpperCase(), "%to%", to.toUpperCase())
        );

        plugin.getDatabase().migrateAsync(from, to).thenAccept(count -> {
            locale.sendMessage(sender, "command-migrate-success",
                    StringPlaceholders.of("%count%", String.valueOf(count))
            );
        }).exceptionally(ex -> {
            locale.sendMessage(sender, "command-migrate-failed",
                    StringPlaceholders.of("%error%", ex.getMessage())
            );
            ex.printStackTrace();
            return null;
        });
    }

    @Override
    public List<String> tabComplete(VoteChecker plugin, CommandSender sender, String[] args) {
        if (args.length == 1 || args.length == 2) {
            return Arrays.asList("sqlite", "mysql");
        }
        return Collections.emptyList();
    }
}
