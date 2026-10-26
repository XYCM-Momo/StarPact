package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;

/**
 * 可选降级：把任务列表写到记分板侧边栏（多行）。
 *
 * <p>默认关闭 —— 因为很多服务器侧边栏已经被别的插件占了。
 * config.yml 的 compat.fallback-to-scoreboard 打开才启用。
 */
public final class ScoreboardFallback {

    private static final String OBJ = "starpact";
    private static final String CRIT = "dummy";

    private final StarPactPlugin plugin;

    public ScoreboardFallback(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    public void render(Player player, List<TrackEntry> tracks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (tracks.isEmpty()) {
            clear(player);
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj;
        try {
            obj = board.registerNewObjective(OBJ, Criteria.DUMMY, "\u00a7b\u4efb\u52a1\u8ffd\u8e2a");
        } catch (Throwable t) {
            obj = board.registerNewObjective(OBJ, CRIT, "\u00a7b\u4efb\u52a1\u8ffd\u8e2a");
        }
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = tracks.size();
        for (TrackEntry e : tracks) {
            String text = "\u00a7f" + com.asterovie.momo.starpact.server.util.Texts.ellipsis(e.title(), 18)
                    + " \u00a7b" + e.numbers();
            obj.getScore(text).setScore(line--);
        }
        player.setScoreboard(board);
    }

    public void clear(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Scoreboard board = player.getScoreboard();
        Objective obj = board.getObjective(OBJ);
        if (obj != null) {
            obj.unregister();
        }
    }

    /** 给 Team 前缀预留（当前实现直接用整行文本，不需要）。 */
    @SuppressWarnings("unused")
    private static Team unused(Scoreboard b) {
        return null;
    }
}
