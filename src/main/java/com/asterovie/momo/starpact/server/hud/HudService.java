package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渲染调度：决定用 AX HUD 还是降级方案，并做服务端→客户端的分发。
 *
 * <p>一个玩家只会走一条路径：
 * <ul>
 *   <li>ArcartX 可用 → AX HUD（打开过一次后保持）</li>
 *   <li>否则 → BossBar（可选再叠一层记分板）</li>
 * </ul>
 * 所以不需要「先清 AX 再画 BossBar」的互斥处理，切换时清掉另一条即可。
 */
public final class HudService {

    private final StarPactPlugin plugin;
    private final HudRenderer axRenderer;
    private final BossBarFallback bossBar;
    private final ScoreboardFallback scoreboard;

    /** 已经为这个玩家打开过 AX HUD */
    private final Set<UUID> opened = ConcurrentHashMap.newKeySet();

    public HudService(StarPactPlugin plugin) {
        this.plugin = plugin;
        this.axRenderer = new HudRenderer(plugin);
        this.bossBar = new BossBarFallback(plugin);
        this.scoreboard = new ScoreboardFallback(plugin);
    }

    public HudRenderer axRenderer() {
        return axRenderer;
    }

    /**
     * 刷新一个玩家的 HUD。
     *
     * @param force 忽略节流
     */
    public void refresh(Player player, boolean force) {
        if (player == null || !player.isOnline()) {
            return;
        }
        List<TrackEntry> tracks = plugin.chemdah().readTracks(player, plugin.spConfig().maxTracks());

        if (plugin.ax().available()) {
            // 确保 UI 已经在客户端打开（defaultOpen=false，需要主动 open 一次）
            if (plugin.prefs().of(player.getUniqueId()).visible
                    && (force || !opened.contains(player.getUniqueId()))) {
                plugin.ax().open(player, HudRenderer.UI_ID);
                opened.add(player.getUniqueId());
            }
            axRenderer.push(player, tracks, force);
            // 切到 AX 时清掉降级残留
            bossBar.hide(player);
            if (plugin.spConfig().fallbackToScoreboard()) {
                scoreboard.clear(player);
            }
        } else {
            bossBar.render(player, tracks);
            if (plugin.spConfig().fallbackToScoreboard()) {
                scoreboard.render(player, tracks);
            }
        }
    }

    /**
     * hud_ready 包的节流：每玩家最多每 3 秒受理一次。
     *
     * <p>历史上这个包曾因「UI open 触发器在每次重绘时都触发」而形成死循环
     * （发 ready → 推数据 → 重绘 → 再发 ready），把 HUD 卡成狂闪。
     * 现在 HUD 侧已不再发这个包，这里保留兜底节流。
     */
    public boolean allowReadyPing(java.util.UUID id) {
        long now = System.currentTimeMillis();
        Long last = readyPingAt.get(id);
        if (last != null && now - last < 3000L) {
            return false;
        }
        readyPingAt.put(id, now);
        return true;
    }

    private final java.util.Map<java.util.UUID, Long> readyPingAt =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 玩家显隐切换。 */
    public void setVisible(Player player, boolean visible) {
        plugin.prefs().setVisible(player.getUniqueId(), visible);
        if (!visible) {
            plugin.ax().close(player, HudRenderer.UI_ID);
            opened.remove(player.getUniqueId());
            bossBar.hide(player);
            if (plugin.spConfig().fallbackToScoreboard()) {
                scoreboard.clear(player);
            }
        } else {
            refresh(player, true);
        }
    }

    /** 玩家改完位置后立刻重推一次。 */
    public void applyPosition(Player player) {
        refresh(player, true);
    }

    /** 玩家离开：清缓存，避免 UUID 泄漏。 */
    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        opened.remove(id);
        axRenderer.forget(id);
        bossBar.forget(id);
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.ax().close(p, HudRenderer.UI_ID);
            bossBar.hide(p);
            if (plugin.spConfig().fallbackToScoreboard()) {
                scoreboard.clear(p);
            }
        }
        opened.clear();
    }

    public void shutdown() {
        bossBar.clear();
    }
}
