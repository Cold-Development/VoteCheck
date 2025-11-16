package dev.padrewin.votechecker.setting;

import dev.padrewin.colddev.config.CommentedConfigurationSection;
import dev.padrewin.colddev.config.ColdSetting;
import dev.padrewin.colddev.config.ColdSettingSerializer;
import dev.padrewin.votechecker.VoteChecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static dev.padrewin.colddev.config.ColdSettingSerializers.*;

public class SettingKey {

    private static final List<ColdSetting<?>> KEYS = new ArrayList<>();

    public static final ColdSetting<String> DATABASE_TYPE = create(
            "database.type", STRING, "SQLITE"
    );

    public static final ColdSetting<Boolean> MYSQL_ENABLED = create(
            "database.mysql.enabled", BOOLEAN, true
    );
    public static final ColdSetting<String> MYSQL_HOST = create(
            "database.mysql.hostname", STRING, "127.0.0.1"
    );
    public static final ColdSetting<Integer> MYSQL_PORT = create(
            "database.mysql.port", INTEGER, 3306
    );
    public static final ColdSetting<String> MYSQL_DATABASE = create(
            "database.mysql.database-name", STRING, "votechecker"
    );
    public static final ColdSetting<String> MYSQL_USER = create(
            "database.mysql.user-name", STRING, "root"
    );
    public static final ColdSetting<String> MYSQL_PASSWORD = create(
            "database.mysql.user-password", STRING, "password"
    );
    public static final ColdSetting<Boolean> MYSQL_USE_SSL = create(
            "database.mysql.use-ssl", BOOLEAN, false
    );
    public static final ColdSetting<Integer> MYSQL_POOL_SIZE = create(
            "database.mysql.connection-pool-size", INTEGER, 3
    );
    public static final ColdSetting<String> MYSQL_TABLE_PREFIX = create(
            "database.mysql.table-prefix", STRING, "vc_"
    );

    public static final ColdSetting<Boolean> ENABLE_PLUGIN = create("enable-plugin", BOOLEAN, true,
            "Enable or disable the VoteChecker plugin entirely.",
            "If set to false, no commands will be blocked even if listed in 'blocked-commands'.");

    public static final ColdSetting<String> VOTE_RESET = create(
            "vote-reset", STRING, "06:59",
            "Either a rolling duration (e.g. '24h', '12h', '1d')",
            "or a daily reset time in 'HH:mm' (e.g. '07:00').",
            "Examples: 24h  |  07:00"
    );

    public static final ColdSetting<String> VOTE_RESET_TIMEZONE = create(
            "vote-reset-timezone", STRING, "Europe/Bucharest",
            "IANA timezone for vote window calculation (e.g. 'Europe/Bucharest', 'UTC').",
            "Used for both rolling duration anchors and daily reset cutoffs."
    );

    public static final ColdSetting<String> BASE_COMMAND_REDIRECT = create("base-command-redirect", STRING, "", "Which command should we redirect to when using '/votechecker' with no subcommand specified?", "You can use a value here such as 'version' to show the output of '/votechecker version'", "If you have any aliases defined, do not use them here", "If left as blank, the default behavior of showing '/votechecker version' with bypassed permissions will be used");

    public static final ColdSetting<Boolean> DEBUG = create("debug", BOOLEAN, false,
            "Enable or disable debug logging for VoteChecker.");

    public static final ColdSetting<List<String>> BLOCKED_COMMANDS = create("blocked-commands", STRING_LIST,
            Arrays.asList(
                    "rewards",
                    "spawn"
            ),
            "List of commands that are blocked until the player votes today.",
            "Supports partial matches and subcommands.");

    public static final ColdSetting<Boolean> REQUIRE_ALL_SITES = create("require-all-sites", BOOLEAN, false,
            "If true, the player must vote on all sites today to unlock commands.",
            "If false, one vote is enough.");

    private static <T> ColdSetting<T> create(String key, ColdSettingSerializer<T> serializer, T def, String... comments) {
        ColdSetting<T> setting = ColdSetting.backed(VoteChecker.getInstance(), key, serializer, def, comments);
        KEYS.add(setting);
        return setting;
    }

    private static ColdSetting<CommentedConfigurationSection> createSection(String key, String... comments) {
        ColdSetting<CommentedConfigurationSection> setting = ColdSetting.backedSection(VoteChecker.getInstance(), key, comments);
        KEYS.add(setting);
        return setting;
    }

    private static void setDefault(CommentedConfigurationSection section, String key, Object value) {
        if (!section.contains(key)) {
            section.set(key, value);
        }
    }

    public static List<ColdSetting<?>> getKeys() {
        return Collections.unmodifiableList(KEYS);
    }

    private SettingKey() {}
}