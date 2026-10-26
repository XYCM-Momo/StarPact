package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import com.asterovie.momo.starpact.server.util.Texts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * ArcartX 不可用时的 BossBar 降级显示。
 */
public final class BossBarFallback {
    private final StarPactPlugin plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<UUID, BossBar>();

    public BossBarFallback(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
    }

    public void render(Player player, List<TrackEntry> list) {
        if (player == null || !player.isOnline()) {
            return;
        }
        SpConfig spConfig = this.plugin.spConfig();
        if (list.isEmpty()) {
            this.hide(player);
            return;
        }
        TrackEntry trackEntry = list.get(0);
        BossBar bossBar = this.bars.get(player.getUniqueId());
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar((String)"", (BarColor)this.progressColor(trackEntry), (BarStyle)BarStyle.SEGMENTED_10, (BarFlag[])new BarFlag[0]);
            bossBar.addPlayer(player);
            this.bars.put(player.getUniqueId(), bossBar);
        }
        bossBar.setTitle(this.titleOf(spConfig, trackEntry));
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, trackEntry.percent())));
        bossBar.setColor(this.progressColor(trackEntry));
        if (!bossBar.isVisible()) {
            bossBar.setVisible(true);
        }
    }

    private String titleOf(SpConfig spConfig, TrackEntry trackEntry) {
        String string = Texts.ellipsis(trackEntry.title(), 30);
        if (spConfig.showNumbers()) {
            return "§b" + string + " §7" + trackEntry.numbers();
        }
        return "§b" + string;
    }

    private BarColor progressColor(TrackEntry trackEntry) {
        if (trackEntry.completed()) {
            return BarColor.GREEN;
        }
        if (trackEntry.percent() >= 0.99) {
            return BarColor.GREEN;
        }
        if (trackEntry.percent() >= 0.5) {
            return BarColor.BLUE;
        }
        return BarColor.PURPLE;
    }

    public void hide(Player player) {
        BossBar bossBar = this.bars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }

    public void forget(UUID uUID) {
        BossBar bossBar = this.bars.remove(uUID);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    public void clear() {
        for (BossBar bossBar : this.bars.values()) {
            bossBar.removeAll();
        }
        this.bars.clear();
    }
}

