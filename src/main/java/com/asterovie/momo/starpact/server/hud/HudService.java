package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.hud.BossBarFallback;
import com.asterovie.momo.starpact.server.hud.HudRenderer;
import com.asterovie.momo.starpact.server.hud.ScoreboardFallback;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 决定用 ArcartX HUD 还是降级 BossBar，并管理定时刷新与节流。
 */
public final class HudService {
    private final StarPactPlugin plugin;
    private final HudRenderer axRenderer;
    private final BossBarFallback bossBar;
    private final ScoreboardFallback scoreboard;
    private final Set<UUID> opened = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> readyPingAt = new ConcurrentHashMap<UUID, Long>();

    public HudService(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
        this.axRenderer = new HudRenderer(starPactPlugin);
        this.bossBar = new BossBarFallback(starPactPlugin);
        this.scoreboard = new ScoreboardFallback(starPactPlugin);
    }

    public HudRenderer axRenderer() {
        return this.axRenderer;
    }

    public void refresh(Player player, boolean bl) {
        if (player == null || !player.isOnline()) {
            return;
        }
        List<TrackEntry> list = this.plugin.chemdah().readTracks(player, this.plugin.spConfig().maxTracks());
        if (this.plugin.ax().available()) {
            if (this.plugin.prefs().of((UUID)player.getUniqueId()).visible && (bl || !this.opened.contains(player.getUniqueId()))) {
                this.plugin.ax().open(player, "starpact:quest_tracker");
                this.opened.add(player.getUniqueId());
            }
            this.axRenderer.push(player, list, bl);
            this.bossBar.hide(player);
            if (this.plugin.spConfig().fallbackToScoreboard()) {
                this.scoreboard.clear(player);
            }
        } else {
            this.bossBar.render(player, list);
            if (this.plugin.spConfig().fallbackToScoreboard()) {
                this.scoreboard.render(player, list);
            }
        }
    }

    public boolean allowReadyPing(UUID uUID) {
        long l = System.currentTimeMillis();
        Long l2 = this.readyPingAt.get(uUID);
        if (l2 != null && l - l2 < 3000L) {
            return false;
        }
        this.readyPingAt.put(uUID, l);
        return true;
    }

    public void setVisible(Player player, boolean bl) {
        this.plugin.prefs().setVisible(player.getUniqueId(), bl);
        if (!bl) {
            this.plugin.ax().close(player, "starpact:quest_tracker");
            this.opened.remove(player.getUniqueId());
            this.bossBar.hide(player);
            if (this.plugin.spConfig().fallbackToScoreboard()) {
                this.scoreboard.clear(player);
            }
        } else {
            this.refresh(player, true);
        }
    }

    public void applyPosition(Player player) {
        this.refresh(player, true);
    }

    public void onQuit(Player player) {
        UUID uUID = player.getUniqueId();
        this.opened.remove(uUID);
        this.axRenderer.forget(uUID);
        this.bossBar.forget(uUID);
    }

    public void hideAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.plugin.ax().close(player, "starpact:quest_tracker");
            this.bossBar.hide(player);
            if (!this.plugin.spConfig().fallbackToScoreboard()) continue;
            this.scoreboard.clear(player);
        }
        this.opened.clear();
    }

    public void shutdown() {
        this.bossBar.clear();
    }
}

