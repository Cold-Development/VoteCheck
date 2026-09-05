package dev.padrewin.votechecker.util;

import dev.padrewin.votechecker.setting.SettingKey;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rezolvă nume -> UUID fără să blocheze thread-ul principal.
 *
 * Bukkit.getOfflinePlayer(String) face un request HTTP sincron către Mojang
 * dacă numele nu e în usercache, deci nu are ce căuta pe main thread.
 */
public final class PlayerUUIDResolver {

    private static final Map<String, UUID> CACHE = new ConcurrentHashMap<>();

    // Paper-only, dar plugin-ul trebuie să meargă și pe Spigot.
    private static final Method GET_OFFLINE_PLAYER_IF_CACHED = findIfCachedMethod();

    private PlayerUUIDResolver() {
    }

    /**
     * Întoarce UUID-ul doar dacă îl putem afla fără acces la rețea, altfel null.
     * Safe de apelat pe thread-ul principal.
     */
    public static UUID resolveWithoutNetwork(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        UUID cached = CACHE.get(key(name));
        if (cached != null) {
            return cached;
        }

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return remember(name, online.getUniqueId());
        }

        if (GET_OFFLINE_PLAYER_IF_CACHED != null) {
            try {
                OfflinePlayer offline = (OfflinePlayer) GET_OFFLINE_PLAYER_IF_CACHED.invoke(null, name);
                if (offline != null) {
                    return remember(name, offline.getUniqueId());
                }
            } catch (ReflectiveOperationException ignored) {
                // Cădem pe pașii de mai jos.
            }
        }

        // Pe server cracked UUID-ul e determinist, deci nu are rost niciun lookup.
        if (usesOfflineUuids()) {
            return remember(name, offlineUuid(name));
        }

        return null;
    }

    /**
     * Lookup complet, care poate lovi API-ul Mojang și poate dura secunde întregi.
     * De apelat DOAR de pe alt thread decât cel principal.
     */
    public static UUID resolveBlocking(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        UUID cached = CACHE.get(key(name));
        if (cached != null) {
            return cached;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        UUID uuid = offline != null ? offline.getUniqueId() : null;
        return uuid != null ? remember(name, uuid) : null;
    }

    /**
     * UUID-ul pe care îl primește un player la login pe un server în offline mode.
     */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean usesOfflineUuids() {
        String mode = SettingKey.OFFLINE_UUID_MODE.get();
        if (mode == null) {
            return !Bukkit.getOnlineMode();
        }

        return switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "TRUE" -> true;
            case "FALSE" -> false;
            default -> !Bukkit.getOnlineMode();
        };
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static UUID remember(String name, UUID uuid) {
        CACHE.put(key(name), uuid);
        return uuid;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static Method findIfCachedMethod() {
        try {
            return Bukkit.class.getMethod("getOfflinePlayerIfCached", String.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
