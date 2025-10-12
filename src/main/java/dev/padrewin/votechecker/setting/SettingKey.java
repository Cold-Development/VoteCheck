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

    public static final ColdSetting<Boolean> ENABLE_PLUGIN = create("enable-plugin", BOOLEAN, true,
            "Enable or disable the VoteChecker plugin entirely.",
            "If set to false, no commands will be blocked even if listed in 'blocked-commands'.");

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

    private static <T> ColdSetting<T> create(String key, ColdSettingSerializer<T> serializer, T defaultValue, String... comments) {
        ColdSetting<T> setting = ColdSetting.backed(VoteChecker.getInstance(), key, serializer, defaultValue, comments);
        KEYS.add(setting);
        return setting;
    }

    private static ColdSetting<CommentedConfigurationSection> create(String key, String... comments) {
        ColdSetting<CommentedConfigurationSection> setting = ColdSetting.backedSection(VoteChecker.getInstance(), key, comments);
        KEYS.add(setting);
        return setting;
    }

    public static List<ColdSetting<?>> getKeys() {
        return Collections.unmodifiableList(KEYS);
    }

    private SettingKey() {}
}
