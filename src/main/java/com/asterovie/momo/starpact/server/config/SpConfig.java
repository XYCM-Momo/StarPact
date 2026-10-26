package com.asterovie.momo.starpact.server.config;

import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * config.yml 的强类型访问器。
 */
public final class SpConfig {
    private final JavaPlugin plugin;

    public SpConfig(JavaPlugin javaPlugin) {
        this.plugin = javaPlugin;
        this.reload();
    }

    public void reload() {
        this.plugin.reloadConfig();
    }

    private FileConfiguration cfg() {
        return this.plugin.getConfig();
    }

    public String anchor() {
        return this.cfg().getString("hud.anchor", "top_left");
    }

    public String anchorPoint() {
        String string;
        return switch (string = this.anchor().toLowerCase(Locale.ROOT).replace('-', '_')) {
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
        return this.cfg().getInt("hud.offset-x", this.cfg().getInt("hud.offset_x", 40));
    }

    public int offsetY() {
        return this.cfg().getInt("hud.offset-y", this.cfg().getInt("hud.offset_y", 420));
    }

    public int width() {
        return this.cfg().getInt("hud.width", 320);
    }

    public int panelBaseWidth() {
        return Math.max(640, this.cfg().getInt("panel-base-width", this.cfg().getInt("panel_base_width", 1920)));
    }

    public double panelContentScale() {
        double d = this.cfg().getDouble("panel-content-scale", 1.0);
        return Math.max(0.8, Math.min(1.2, d));
    }

    public double panelLeftRatio() {
        double d = this.cfg().getDouble("panel-left-ratio", 0.3);
        return Math.max(0.2, Math.min(0.4, d));
    }

    public double panelFit() {
        double d = this.cfg().getDouble("panel-fit", this.cfg().getDouble("panel_fit", 0.3));
        return d <= 0.0 ? 0.3 : d;
    }

    public int panelBaseHeight() {
        return Math.max(400, this.cfg().getInt("panel-base-height", this.cfg().getInt("panel_base_height", 1080)));
    }

    public double defaultScale() {
        return this.clampScale(this.cfg().getDouble("hud.default-scale", this.cfg().getDouble("hud.default_scale", 1.0)));
    }

    public double minScale() {
        return Math.max(0.3, this.cfg().getDouble("hud.min-scale", 0.6));
    }

    public double maxScale() {
        return Math.min(4.0, this.cfg().getDouble("hud.max-scale", 2.0));
    }

    public double clampScale(double d) {
        return Math.max(this.minScale(), Math.min(this.maxScale(), d));
    }

    public boolean allowPlayerMove() {
        return this.cfg().getBoolean("hud.allow-player-move", this.cfg().getBoolean("hud.allow_player_move", true));
    }

    public int maxTracks() {
        return Math.max(1, Math.min(8, this.cfg().getInt("display.max-tracks", this.cfg().getInt("display.max_tracks", 3))));
    }

    public boolean hideWhenEmpty() {
        return this.cfg().getBoolean("display.hide-when-empty", this.cfg().getBoolean("display.hide_when_empty", true));
    }

    public boolean showHeader() {
        return this.cfg().getBoolean("display.show-header", this.cfg().getBoolean("display.show_header", true));
    }

    public int barLength() {
        return Math.max(3, Math.min(40, this.cfg().getInt("display.bar-length", this.cfg().getInt("display.bar_length", 16))));
    }

    public String barFill() {
        return this.cfg().getString("display.bar-fill", this.cfg().getString("display.bar_fill", "|"));
    }

    public String barEmpty() {
        return this.cfg().getString("display.bar-empty", this.cfg().getString("display.bar_empty", "."));
    }

    public boolean showNumbers() {
        return this.cfg().getBoolean("display.show-numbers", this.cfg().getBoolean("display.show_numbers", true));
    }

    public boolean completionSound() {
        return this.cfg().getBoolean("display.completion-sound", this.cfg().getBoolean("display.completion_sound", true));
    }

    public int intervalSeconds() {
        return Math.max(0, this.cfg().getInt("refresh.interval-seconds", this.cfg().getInt("refresh.interval_seconds", 2)));
    }

    public long throttleMs() {
        return Math.max(0L, this.cfg().getLong("refresh.throttle-ms", this.cfg().getLong("refresh.throttle_ms", 250L)));
    }

    public boolean fallbackToBossbar() {
        return this.cfg().getBoolean("compat.fallback-to-bossbar", this.cfg().getBoolean("compat.fallback_to_bossbar", true));
    }

    public boolean fallbackToScoreboard() {
        return this.cfg().getBoolean("compat.fallback-to-scoreboard", this.cfg().getBoolean("compat.fallback_to_scoreboard", false));
    }

    public boolean debug() {
        return this.cfg().getBoolean("compat.debug", false);
    }

    public boolean installAxKeyConfig() {
        return this.cfg().getBoolean("key.install-ax-config", this.cfg().getBoolean("key.install_ax_config", true));
    }

    public String defaultKey() {
        return this.cfg().getString("key.default", "J");
    }
}

