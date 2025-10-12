package dev.padrewin.votechecker;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.config.ColdSetting;
import dev.padrewin.colddev.manager.Manager;
import dev.padrewin.colddev.manager.PluginUpdateManager;
import dev.padrewin.votechecker.listeners.VoteCheckListener;
import dev.padrewin.votechecker.manager.CommandManager;
import dev.padrewin.votechecker.manager.LocaleManager;
import dev.padrewin.votechecker.setting.SettingKey;
import dev.padrewin.votechecker.database.VoteDatabaseManager;
import dev.padrewin.votechecker.listeners.VoteListener;

import java.io.File;
import java.util.List;

public final class VoteChecker extends ColdPlugin {

    /**
     * Console colors
     */
    String ANSI_RESET = "\u001B[0m";
    String ANSI_CHINESE_PURPLE = "\u001B[38;5;93m";
    String ANSI_PURPLE = "\u001B[35m";
    String ANSI_GREEN = "\u001B[32m";
    String ANSI_RED = "\u001B[31m";
    String ANSI_AQUA = "\u001B[36m";
    String ANSI_PINK = "\u001B[35m";
    String ANSI_YELLOW = "\u001B[33m";

    private static VoteChecker instance;
    private VoteDatabaseManager database;

    public VoteChecker() {
        super("Cold-Development", "VoteChecker", -1, null, LocaleManager.class, null);
        instance = this;
    }

    @Override
    public void enable() {
        instance = this;
        this.database = new VoteDatabaseManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new VoteCheckListener(), this);
        getServer().getPluginManager().registerEvents(new VoteListener(this), this);
        getManager(PluginUpdateManager.class);

        // Banner
        String name = getDescription().getName();
        getLogger().info("");
        getLogger().info(ANSI_CHINESE_PURPLE + "  ____ ___  _     ____  " + ANSI_RESET);
        getLogger().info(ANSI_PINK + " / ___/ _ \\| |   |  _ \\ " + ANSI_RESET);
        getLogger().info(ANSI_CHINESE_PURPLE + "| |  | | | | |   | | | |" + ANSI_RESET);
        getLogger().info(ANSI_PINK + "| |__| |_| | |___| |_| |" + ANSI_RESET);
        getLogger().info(ANSI_CHINESE_PURPLE + " \\____\\___/|_____|____/ " + ANSI_RESET);
        getLogger().info("    " + ANSI_GREEN + name + ANSI_RED + " v" + getDescription().getVersion() + ANSI_RESET);
        getLogger().info(ANSI_PURPLE + "    Author(s): " + ANSI_PURPLE + getDescription().getAuthors().get(0) + ANSI_RESET);
        getLogger().info(ANSI_AQUA + "    (c) Cold Development ❄" + ANSI_RESET);
        getLogger().info("");

        File configFile = new File(getDataFolder(), "locale/en_US.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        saveDefaultConfig();
        getLogger().info(ANSI_YELLOW + "VoteChecker loaded successfully!" + ANSI_RESET);
    }

    @Override
    public void disable() {
        if (database != null) {
            database.close();
        }
        getLogger().info("");
        getLogger().info(ANSI_PURPLE + "VoteChecker disabled." + ANSI_RESET);
        getLogger().info("");
    }

    @Override
    public void reload() {
        super.reload();

        if (database != null) {
            database.reconnect();
        }
    }

    @Override
    protected List<Class<? extends Manager>> getManagerLoadPriority() {
        return List.of(
                CommandManager.class
        );
    }

    @Override
    protected List<ColdSetting<?>> getColdConfigSettings() {
        return SettingKey.getKeys();
    }

    @Override
    protected String[] getColdConfigHeader() {
        return new String[]{
                "██╗   ██╗ ██████╗ ████████╗███████╗",
                "██║   ██║██╔═══██╗╚══██╔══╝██╔════╝",
                "██║   ██║██║   ██║   ██║   █████╗  ",
                "╚██╗ ██╔╝██║   ██║   ██║   ██╔══╝  ",
                " ╚████╔╝ ╚██████╔╝   ██║   ███████╗",
                "  ╚═══╝   ╚═════╝    ╚═╝   ╚══════╝",
                "                                    "
        };
    }

    public static VoteChecker getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VoteChecker instance is not initialized!");
        }
        return instance;
    }

    public VoteDatabaseManager getDatabase() {
        return database;
    }

}
