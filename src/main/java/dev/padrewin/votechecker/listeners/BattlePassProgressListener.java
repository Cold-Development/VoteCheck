package dev.padrewin.votechecker.listeners;

import dev.padrewin.votechecker.VoteChecker;
import dev.padrewin.votechecker.setting.SettingKey;
import dev.padrewin.votechecker.util.VoteUtil;
import io.github.battlepass.api.events.user.UserQuestProgressionEvent;
import io.github.battlepass.entity.base.UserEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BattlePassProgressListener implements Listener {

    private final VoteChecker plugin;

    public BattlePassProgressListener(VoteChecker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBattlePassProgress(UserQuestProgressionEvent event) {
        if (!SettingKey.ENABLE_PLUGIN.get()) {
            return;
        }

        UserEntity user = event.getUser();

        Player player = extractPlayer(user);
        if (player != null && player.hasPermission("votechecker.bypass")) {
            return;
        }

        UUID uuid = extractUuid(user);
        String name = extractName(user);

        if (uuid == null || name == null || name.isBlank()) {
            event.setCancelled(true);
            return;
        }

        var cache = plugin.getVoteCacheManager();

        if (cache.isVoted(uuid)) {
            return;
        }

        event.setCancelled(true);

        if (!cache.isChecking(uuid) && !cache.isTemporarilyNotVoted(uuid)) {
            cache.checkAsync(uuid, name);
        }
    }

    private UUID extractUuid(UserEntity user) {
        Object direct = invokeGetter(user, "getUniqueId", "getUUID", "getUuid");
        if (direct instanceof UUID uuid) {
            return uuid;
        }

        Object playerObj = invokeGetter(user, "getPlayer");
        if (playerObj instanceof Player player) {
            return player.getUniqueId();
        }

        return null;
    }

    private Player extractPlayer(UserEntity user) {
        Object playerObj = invokeGetter(user, "getPlayer");
        if (playerObj instanceof Player player) {
            return player;
        }
        return null;
    }

    private String extractName(UserEntity user) {
        Object direct = invokeGetter(user, "getName", "getPlayerName", "getUsername");
        if (direct instanceof String name && !name.isBlank()) {
            return name;
        }

        Object playerObj = invokeGetter(user, "getPlayer");
        if (playerObj instanceof Player player) {
            return player.getName();
        }

        return null;
    }

    private Object invokeGetter(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (Exception ignored) {
                // Try next method name.
            }
        }
        return null;
    }
}
