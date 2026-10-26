package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import com.asterovie.momo.starpact.server.util.Texts;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * ArcartX 与 BossBar 都不可用时的计分板降级。
 */
public final class ScoreboardFallback {
    private static final String OBJ = "starpact";
    private static final String CRIT = "dummy";
    private final StarPactPlugin plugin;

    public ScoreboardFallback(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
    }

    public void render(Player player, List<TrackEntry> list) {
        Objective objective;
        if (player == null || !player.isOnline()) {
            return;
        }
        if (list.isEmpty()) {
            this.clear(player);
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        try {
            objective = scoreboard.registerNewObjective(OBJ, Criteria.DUMMY, "§b任务追踪");
        }
        catch (Throwable throwable) {
            objective = scoreboard.registerNewObjective(OBJ, CRIT, "§b任务追踪");
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int n = list.size();
        for (TrackEntry trackEntry : list) {
            String string = "§f" + Texts.ellipsis(trackEntry.title(), 18) + " §b" + trackEntry.numbers();
            objective.getScore(string).setScore(n--);
        }
        player.setScoreboard(scoreboard);
    }

    public void clear(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJ);
        if (objective != null) {
            objective.unregister();
        }
    }

    private static Team unused(Scoreboard scoreboard) {
        return null;
    }
}

