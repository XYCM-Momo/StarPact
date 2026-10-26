package com.asterovie.momo.starpact.server.storage;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家 HUD 个人位置 + 开关的持久化。
 *
 * <p>存成 {@code plugins/StarPact/hud-positions.yml}，结构：
 * <pre>
 * players:
 *   &lt;uuid&gt;:
 *     x: -5
 *     y: 168
 *     visible: true
 * </pre>
 *
 * <p>写入是异步合并 + 定时落盘的：拖动不会每帧刷盘，最多每秒合并写一次，
 * 服务端关闭时强制 flush。
 */
public final class PlayerPrefs {

    public static final class Pref {
        public volatile int x;
        public volatile int y;
        public volatile boolean visible = true;
        /** HUD 缩放比例，1.0 = 100% */
        public volatile double scale = 1.0;
        /** 任务面板（GUI）缩放比例，1.0 = 100% */
        public volatile double uiScale = 1.0;
        /** HUD 宽度（UI 像素）；0 = 用 config 默认 */
        public volatile int hudW = 0;
        /** HUD 高度（UI 像素）；0 = 按任务数自动 */
        public volatile int hudH = 0;
        /** 面板背景透明度，1.0 = 完全不透明 */
        public volatile double panelAlpha = 1.0;
        /** 面板左栏宽度比例，0.30 = 30% */
        public volatile double leftRatio = 0.30;
        /** 玩家是否自定义过位置（否则用 config 的默认值） */
        public volatile boolean custom;

        Pref(int x, int y, boolean visible, boolean custom, double scale, double uiScale, int hudW, int hudH,
             double panelAlpha, double leftRatio) {
            this.x = x;
            this.y = y;
            this.visible = visible;
            this.custom = custom;
            this.scale = scale;
            this.uiScale = uiScale;
            this.hudW = hudW;
            this.hudH = hudH;
            this.panelAlpha = panelAlpha;
            this.leftRatio = leftRatio;
        }
    }

    private final StarPactPlugin plugin;
    private final File file;
    private final Map<UUID, Pref> prefs = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public PlayerPrefs(StarPactPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hud-positions.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var section = yml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            int x = section.getInt(key + ".x", 0);
            int y = section.getInt(key + ".y", 0);
            boolean visible = section.getBoolean(key + ".visible", true);
            boolean custom = section.getBoolean(key + ".custom", true);
            double scale = section.getDouble(key + ".scale", plugin.spConfig().defaultScale());
            double uiScale = section.getDouble(key + ".uiScale", 1.0);
            int hudW = section.getInt(key + ".hudW", 0);
            int hudH = section.getInt(key + ".hudH", 0);
            double panelAlpha = section.getDouble(key + ".panelAlpha", 1.0);
            double leftRatio = section.getDouble(key + ".leftRatio",
                    plugin.spConfig().panelLeftRatio());
            prefs.put(id, new Pref(x, y, visible, custom, scale, uiScale, hudW, hudH, panelAlpha, leftRatio));
        }
        plugin.getLogger().info("已加载 " + prefs.size() + " 名玩家的 HUD 个人设置");
    }

    /** 取玩家设置；没有就按配置默认值生成一份内存对象（不落盘，除非玩家改过）。 */
    public Pref of(UUID id) {
        return prefs.computeIfAbsent(id, k ->
                new Pref(plugin.spConfig().offsetX(), plugin.spConfig().offsetY(), true, false,
                        plugin.spConfig().defaultScale(), 1.0,
                        plugin.spConfig().width(), 0,
                        plugin.spConfig().defaultPanelAlpha(), plugin.spConfig().panelLeftRatio()));
    }

    public void setPosition(UUID id, int x, int y) {
        Pref p = of(id);
        p.x = x;
        p.y = y;
        p.custom = true;
        dirty = true;
    }

    public void setScale(UUID id, double scale) {
        Pref p = of(id);
        p.scale = plugin.spConfig().clampScale(scale);
        dirty = true;
    }

    public void setUiScale(UUID id, double v) {
        Pref p = of(id);
        p.uiScale = Math.max(0.5, Math.min(2.5, v));
        dirty = true;
    }

    public void setHudWidth(UUID id, int w) {
        Pref p = of(id);
        p.hudW = Math.max(200, Math.min(900, w));
        dirty = true;
    }

    public void setPanelAlpha(UUID id, double v) {
        Pref p = of(id);
        p.panelAlpha = Math.max(0.3, Math.min(1.0, v));
        dirty = true;
    }

    public void setLeftRatio(UUID id, double v) {
        Pref p = of(id);
        p.leftRatio = Math.max(0.20, Math.min(0.45, v));
        dirty = true;
    }

    public void setHudHeight(UUID id, int h) {
        Pref p = of(id);
        p.hudH = Math.max(0, Math.min(600, h));
        dirty = true;
    }

    public void setVisible(UUID id, boolean visible) {
        Pref p = of(id);
        p.visible = visible;
        dirty = true;
    }

    public void resetPosition(UUID id) {
        Pref p = of(id);
        p.x = plugin.spConfig().offsetX();
        p.y = plugin.spConfig().offsetY();
        p.custom = false;
        dirty = true;
    }

    /** 有改动才落盘。由插件每秒调用一次，或关服时调用。 */
    public synchronized void flushIfDirty() {
        if (!dirty) {
            return;
        }
        flush();
    }

    public synchronized void flush() {
        YamlConfiguration yml = new YamlConfiguration();
        for (var e : prefs.entrySet()) {
            // 只存玩家真正改过的，避免文件随玩家数膨胀
            Pref p = e.getValue();
            if (!p.custom && p.visible) {
                continue;
            }
            String base = "players." + e.getKey();
            yml.set(base + ".x", p.x);
            yml.set(base + ".y", p.y);
            yml.set(base + ".visible", p.visible);
            yml.set(base + ".custom", p.custom);
            yml.set(base + ".scale", p.scale);
            yml.set(base + ".uiScale", p.uiScale);
            yml.set(base + ".hudW", p.hudW);
            yml.set(base + ".hudH", p.hudH);
            yml.set(base + ".panelAlpha", p.panelAlpha);
            yml.set(base + ".leftRatio", p.leftRatio);
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录: " + plugin.getDataFolder());
            }
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 hud-positions.yml 失败: " + ex.getMessage());
        }
    }
}
