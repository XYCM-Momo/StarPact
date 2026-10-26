package com.asterovie.momo.starpact.server.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 插件配置。对应 config.yml。
 *
 * <p>所有 getter 都直接读 Bukkit 的 FileConfiguration，不做缓存 ——
 * /sp reload 之后立刻生效，不用重建对象。
 */
public final class SpConfig {

    private final JavaPlugin plugin;

    public SpConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ── hud ──────────────────────────────────────────────────

    /** 锚点，映射成 ArcartX 的 point 值（如 top_left → ~top_left）。 */
    public String anchor() {
        return cfg().getString("hud.anchor", "top_left");
    }

    /** 交给 ArcartX 的 point 字面量（Aria 里要带 ~ 前缀）。 */
    public String anchorPoint() {
        String a = anchor().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        return switch (a) {
            case "top_right", "topright" -> "~top_right";
            case "bottom_left", "bottomleft" -> "~bottom_left";
            case "bottom_right", "bottomright" -> "~bottom_right";
            case "middle_left", "middleleft" -> "~middle_left";
            case "middle_right", "middleright" -> "~middle_right";
            case "middle_center", "middlecenter", "center" -> "~middle_center";
            case "top_center", "topcenter" -> "~top_center";
            case "bottom_center", "bottomcenter" -> "~bottom_center";
            default -> "~top_left";
        };
    }

    public int offsetX() {
        return cfg().getInt("hud.offset-x", cfg().getInt("hud.offset_x", 40));
    }

    public int offsetY() {
        return cfg().getInt("hud.offset-y", cfg().getInt("hud.offset_y", 420));
    }

    public int width() {
        return cfg().getInt("hud.width", 320);
    }

    /**
     * 任务面板的设计基准宽度。
     *
     * <p>AX 的 Adaptive 按 (实际窗口 / 基准) 缩放；基准越大，内容显示越小。
     * 内容超出边框时把这个值调大即可，改完 /sp reload 生效。
     */
    public int panelBaseWidth() {
        return Math.max(640, cfg().getInt("panel-base-width",
                cfg().getInt("panel_base_width", 1920)));
    }

    /**
     * 面板整体缩放系数（把 1920x1080 设计空间映射到实际 UI 坐标空间）。
     *
     * <p>AX 的 UI 单位与窗口像素不是 1:1，实测差距较大，所以做成配置项：
     * 界面太大就调小，太小就调大。改完 /sp reload 生效，不用重启。
     */
    /**
     * 任务面板内容缩放（玩家可调，0.8 ~ 1.2）。
     * 影响任务列表和详情的字号。
     */
    public double panelContentScale() {
        double v = cfg().getDouble("panel-content-scale", 1.0);
        return Math.max(0.8, Math.min(1.2, v));
    }

    /** 左栏宽度比例（0.20 ~ 0.40，默认 0.30） */
    public double panelLeftRatio() {
        double v = cfg().getDouble("panel-left-ratio", 0.30);
        return Math.max(0.20, Math.min(0.40, v));
    }

    /** 面板背景透明度默认值（0.3 ~ 1.0）。 */
    public double defaultPanelAlpha() {
        double v = cfg().getDouble("panel-alpha", 1.0);
        return Math.max(0.3, Math.min(1.0, v));
    }

    public double panelFit() {
        double v = cfg().getDouble("panel-fit", cfg().getDouble("panel_fit", 0.3));
        return v <= 0 ? 0.3 : v;
    }

    public int panelBaseHeight() {
        return Math.max(400, cfg().getInt("panel-base-height",
                cfg().getInt("panel_base_height", 1080)));
    }

    /** 默认缩放比例。 */
    public double defaultScale() {
        return clampScale(cfg().getDouble("hud.default-scale",
                cfg().getDouble("hud.default_scale", 1.0)));
    }

    public double minScale() {
        return Math.max(0.3, cfg().getDouble("hud.min-scale", 0.6));
    }

    public double maxScale() {
        return Math.min(4.0, cfg().getDouble("hud.max-scale", 2.0));
    }

    /** 把任意 scale 夹到 [min, max]。 */
    public double clampScale(double v) {
        return Math.max(minScale(), Math.min(maxScale(), v));
    }

    public boolean allowPlayerMove() {
        return cfg().getBoolean("hud.allow-player-move", cfg().getBoolean("hud.allow_player_move", true));
    }

    // ── display ──────────────────────────────────────────────

    public int maxTracks() {
        return Math.max(1, Math.min(8, cfg().getInt("display.max-tracks",
                cfg().getInt("display.max_tracks", 3))));
    }

    public boolean hideWhenEmpty() {
        return cfg().getBoolean("display.hide-when-empty",
                cfg().getBoolean("display.hide_when_empty", true));
    }

    public boolean showHeader() {
        return cfg().getBoolean("display.show-header",
                cfg().getBoolean("display.show_header", true));
    }

    public int barLength() {
        return Math.max(3, Math.min(40, cfg().getInt("display.bar-length", cfg().getInt("display.bar_length", 16))));
    }

    public String barFill() {
        return cfg().getString("display.bar-fill", cfg().getString("display.bar_fill", "|"));
    }

    public String barEmpty() {
        return cfg().getString("display.bar-empty", cfg().getString("display.bar_empty", "."));
    }

    public boolean showNumbers() {
        return cfg().getBoolean("display.show-numbers",
                cfg().getBoolean("display.show_numbers", true));
    }

    public boolean completionSound() {
        return cfg().getBoolean("display.completion-sound",
                cfg().getBoolean("display.completion_sound", true));
    }

    // ── refresh ──────────────────────────────────────────────

    /** 轮询间隔（秒）。0 = 关闭自动刷新。 */
    public int intervalSeconds() {
        return Math.max(0, cfg().getInt("refresh.interval-seconds",
                cfg().getInt("refresh.interval_seconds", 2)));
    }

    public long throttleMs() {
        return Math.max(0L, cfg().getLong("refresh.throttle-ms",
                cfg().getLong("refresh.throttle_ms", 250L)));
    }

    // ── compat ───────────────────────────────────────────────

    public boolean fallbackToBossbar() {
        return cfg().getBoolean("compat.fallback-to-bossbar",
                cfg().getBoolean("compat.fallback_to_bossbar", true));
    }

    public boolean fallbackToScoreboard() {
        return cfg().getBoolean("compat.fallback-to-scoreboard",
                cfg().getBoolean("compat.fallback_to_scoreboard", false));
    }

    public boolean debug() {
        return cfg().getBoolean("compat.debug", false);
    }

    // ── 按键 ─────────────────────────────────────────────────

    /**
     * 是否把按键配置写进 ArcartX 目录（key_bind / scripts）。
     *
     * <p>走 ArcartX 的配置文件加载路径，这是实测唯一能让 J 出现在
     * 客户端按键表里的方式（纯 API 注册不会重建客户端按键）。
     */
    public boolean installAxKeyConfig() {
        return cfg().getBoolean("key.install-ax-config",
                cfg().getBoolean("key.install_ax_config", true));
    }

    /** 默认按键（写进 client_key 配置，玩家可在客户端改）。 */
    public String defaultKey() {
        return cfg().getString("key.default", "J");
    }
}
