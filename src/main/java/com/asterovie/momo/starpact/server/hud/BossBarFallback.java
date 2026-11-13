package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 降级渲染：玩家没装 ArcartX 客户端时，用原版 BossBar 显示第一条追踪目标。
 *
 * <p>这是「插件永远不会让玩家什么都看不到」的保底。只有一个条，
 * 所以只显示最靠前的那一条；多任务列表要 AX HUD 才有。
 */
public final class BossBarFallback {

    private final StarPactPlugin plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public BossBarFallback(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    public void render(Player player, List<TrackEntry> tracks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        SpConfig cfg = plugin.spConfig();
        if (tracks.isEmpty()) {
            hide(player);
            return;
        }
        TrackEntry top = tracks.get(0);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar("", progressColor(top), BarStyle.SEGMENTED_10);
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        }
        bar.setTitle(titleOf(cfg, top));
        bar.setProgress(Math.max(0, Math.min(1, top.percent())));
        bar.setColor(progressColor(top));
        if (!bar.isVisible()) {
            bar.setVisible(true);
        }
    }

    private String titleOf(SpConfig cfg, TrackEntry e) {
        String name = com.asterovie.momo.starpact.server.util.Texts.ellipsis(e.title(), 30);
        if (cfg.showNumbers()) {
            return "\u00a7b" + name + " \u00a77" + e.numbers();
        }
        return "\u00a7b" + name;
    }

    private BarColor progressColor(TrackEntry e) {
        if (e.completed()) {
            return BarColor.GREEN;
        }
        if (e.percent() >= 0.99) {
            return BarColor.GREEN;
        }
        if (e.percent() >= 0.5) {
            return BarColor.BLUE;
        }
        return BarColor.PURPLE;
    }

    /**
     * 清掉该玩家的降级 BossBar。
     *
     * <p>除了移除 map 里登记的那条，还要兜底扫一遍 {@link Bukkit#getBossBars()}：
     * 如果 bar 因为 reload / 重连等原因脱管（map 已清空但玩家身上还挂着），
     * 只靠 map 会漏掉，表现为「AX HUD 正常显示，顶部却多出一条进度条」。
     */
    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            // 先摘玩家再隐藏：只 setVisible(false) 有时不会立刻同步到客户端
            bar.removePlayer(player);
            bar.removeAll();
            bar.setVisible(false);
        }
        // 兜底：把所有仍挂着该玩家的 BossBar 都摘掉
        java.util.Iterator<org.bukkit.boss.KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            BossBar b = it.next();
            if (b.getPlayers().contains(player)) {
                b.removePlayer(player);
                if (b.getPlayers().isEmpty()) {
                    b.setVisible(false);
                }
            }
        }
    }

    public void forget(UUID id) {
        BossBar bar = bars.remove(id);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    public void clear() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        bars.clear();
    }
}
