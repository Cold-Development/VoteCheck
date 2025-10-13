package dev.padrewin.votechecker.util;

import dev.padrewin.votechecker.VoteChecker;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class VoteUtil {

    public static CompletableFuture<Boolean> hasVotedToday(Player player) {
    return VoteChecker.getInstance().getDatabase().hasVotedTodayAsync(player.getUniqueId(), player.getName());
    }
}
