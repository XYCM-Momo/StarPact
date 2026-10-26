package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import com.asterovie.momo.starpact.server.util.Texts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * 任务追踪 HUD 的数据渲染。把任务列表拼成文本推给 ArcartX HUD。
 */
public final class HudRenderer {
    public static final String UI_ID = "starpact:quest_tracker";
    private final StarPactPlugin plugin;
    private final Map<UUID, Long> lastPushAt = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, String> lastFingerprint = new ConcurrentHashMap<UUID, String>();

    public HudRenderer(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
    }

    public boolean push(Player player, List<TrackEntry> list, boolean bl) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        SpConfig spConfig = this.plugin.spConfig();
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        int n = Math.min(list.size(), spConfig.maxTracks());
        boolean bl2 = pref.visible && (!spConfig.hideWhenEmpty() || n != 0);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(pref.x).append(',').append(pref.y).append(',').append(bl2).append(',').append(n);
        for (int i = 0; i < n; ++i) {
            TrackEntry trackEntry = list.get(i);
            stringBuilder.append('|').append(trackEntry.taskId()).append(':').append(trackEntry.bar(spConfig.barLength(), spConfig.barFill(), spConfig.barEmpty())).append(':').append(trackEntry.numbers()).append(':').append(trackEntry.title());
        }
        String string = stringBuilder.toString();
        long l = System.currentTimeMillis();
        Long l2 = this.lastPushAt.get(player.getUniqueId());
        if (!bl) {
            if (string.equals(this.lastFingerprint.get(player.getUniqueId()))) {
                return false;
            }
            if (l2 != null && l - l2 < spConfig.throttleMs()) {
                return false;
            }
        }
        this.lastPushAt.put(player.getUniqueId(), l);
        this.lastFingerprint.put(player.getUniqueId(), string);
        double d = pref.scale <= 0.0 ? spConfig.defaultScale() : spConfig.clampScale(pref.scale);
        HashMap<Object, Object> hashMap = new HashMap<Object, Object>();
        hashMap.put("x", pref.custom ? pref.x : spConfig.offsetX());
        hashMap.put("y", pref.custom ? pref.y : spConfig.offsetY());
        boolean bl3 = pref.visible && (!spConfig.hideWhenEmpty() || n != 0);
        hashMap.put("show", bl3 ? 1 : 0);
        hashMap.put("zoom", d);
        int n2 = pref.hudW > 0 ? pref.hudW : spConfig.width();
        hashMap.put("w", Math.round((double)n2 * d));
        int n3 = pref.hudH > 0 ? pref.hudH : this.panelHeight(spConfig, n);
        hashMap.put("h", Math.round((double)n3 * d));
        hashMap.put("fsTitle", Math.round(20.0 * d));
        hashMap.put("fsBody", Math.round(18.0 * d));
        hashMap.put("fsHint", Math.round(15.0 * d));
        hashMap.put("fsLine", Math.round(21.0 * d));
        hashMap.put("padX", Math.round(14.0 * d));
        hashMap.put("rowH", Math.round(54.0 * d));
        hashMap.put("topPad", Math.round(12.0 * d));
        hashMap.put("rowGap", Math.round(30.0 * d));
        for (int i = 0; i < spConfig.maxTracks(); ++i) {
            boolean bl4 = bl3 && i < n;
            String string2 = "§r";
            String string3 = "§r";
            if (i < n) {
                TrackEntry trackEntry = list.get(i);
                string2 = this.line1(spConfig, trackEntry);
                string3 = this.line2(spConfig, trackEntry);
            }
            hashMap.put("t" + i, string2);
            hashMap.put("b" + i, string3);
            hashMap.put("v" + i, bl4 ? 1 : 0);
        }
        if (spConfig.debug()) {
            this.plugin.getLogger().info("[HUD] " + player.getName() + " -> " + String.valueOf(hashMap));
        }
        if (this.plugin.ax().available()) {
            this.plugin.ax().sendPacket(player, UI_ID, "sync", hashMap);
            return true;
        }
        return false;
    }

    private String line1(SpConfig spConfig, TrackEntry trackEntry) {
        Object object = Texts.ellipsis(trackEntry.title(), 20);
        Object object2 = object = trackEntry.completed() ? "§a✔ " + (String)object : "§f" + (String)object;
        if (!spConfig.showNumbers()) {
            return object;
        }
        String string = "§7" + trackEntry.numbers();
        int n = Texts.visualWidth((String)object) + Texts.visualWidth(string);
        int n2 = Math.max(1, this.lineWidthChars(spConfig) - n);
        return (String)object + " ".repeat(n2) + string;
    }

    private int lineWidthChars(SpConfig spConfig) {
        int n = Math.max(100, spConfig.width() - 32);
        return Math.max(10, n / 9);
    }

    private String line2(SpConfig spConfig, TrackEntry trackEntry) {
        String string = trackEntry.bar(spConfig.barLength(), spConfig.barFill(), spConfig.barEmpty());
        String string2 = trackEntry.completed() ? "§a" : (trackEntry.percent() >= 1.0 ? "§a" : "§b");
        return string2 + string;
    }

    private int panelHeight(SpConfig spConfig, int n) {
        int n2 = Math.max(1, Math.min(n, spConfig.maxTracks()));
        return 52 + n2 * 54;
    }

    public void forget(UUID uUID) {
        this.lastPushAt.remove(uUID);
        this.lastFingerprint.remove(uUID);
    }
}

