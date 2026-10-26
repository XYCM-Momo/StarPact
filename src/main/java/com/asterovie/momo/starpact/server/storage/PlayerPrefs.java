package com.asterovie.momo.starpact.server.storage;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 按玩家 UUID 持久化的 HUD/面板个人设置。
 */
public final class PlayerPrefs {
    private final StarPactPlugin plugin;
    private final File file;
    private final Map<UUID, Pref> prefs = new ConcurrentHashMap<UUID, Pref>();
    private volatile boolean dirty;

    public PlayerPrefs(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
        this.file = new File(starPactPlugin.getDataFolder(), "hud-positions.yml");
        this.load();
    }

    private void load() {
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("players");
        if (configurationSection == null) {
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            UUID uUID;
            try {
                uUID = UUID.fromString(string);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                continue;
            }
            int n = configurationSection.getInt(string + ".x", 0);
            int n2 = configurationSection.getInt(string + ".y", 0);
            boolean bl = configurationSection.getBoolean(string + ".visible", true);
            boolean bl2 = configurationSection.getBoolean(string + ".custom", true);
            double d = configurationSection.getDouble(string + ".scale", this.plugin.spConfig().defaultScale());
            double d2 = configurationSection.getDouble(string + ".uiScale", 1.0);
            int n3 = configurationSection.getInt(string + ".hudW", 0);
            int n4 = configurationSection.getInt(string + ".hudH", 0);
            double d3 = configurationSection.getDouble(string + ".contentScale", this.plugin.spConfig().panelContentScale());
            double d4 = configurationSection.getDouble(string + ".leftRatio", this.plugin.spConfig().panelLeftRatio());
            this.prefs.put(uUID, new Pref(n, n2, bl, bl2, d, d2, n3, n4, d3, d4));
        }
        this.plugin.getLogger().info("已加载 " + this.prefs.size() + " 名玩家的 HUD 个人设置");
    }

    public Pref of(UUID uUID2) {
        return this.prefs.computeIfAbsent(uUID2, uUID -> new Pref(this.plugin.spConfig().offsetX(), this.plugin.spConfig().offsetY(), true, false, this.plugin.spConfig().defaultScale(), 1.0, 0, 0, this.plugin.spConfig().panelContentScale(), this.plugin.spConfig().panelLeftRatio()));
    }

    public void setPosition(UUID uUID, int n, int n2) {
        Pref pref = this.of(uUID);
        pref.x = n;
        pref.y = n2;
        pref.custom = true;
        this.dirty = true;
    }

    public void setScale(UUID uUID, double d) {
        Pref pref = this.of(uUID);
        pref.scale = this.plugin.spConfig().clampScale(d);
        this.dirty = true;
    }

    public void setUiScale(UUID uUID, double d) {
        Pref pref = this.of(uUID);
        pref.uiScale = Math.max(0.5, Math.min(2.5, d));
        this.dirty = true;
    }

    public void setHudWidth(UUID uUID, int n) {
        Pref pref = this.of(uUID);
        pref.hudW = Math.max(200, Math.min(900, n));
        this.dirty = true;
    }

    public void setContentScale(UUID uUID, double d) {
        Pref pref = this.of(uUID);
        pref.contentScale = Math.max(0.15, Math.min(1.0, d));
        this.dirty = true;
    }

    public void setLeftRatio(UUID uUID, double d) {
        Pref pref = this.of(uUID);
        pref.leftRatio = Math.max(0.15, Math.min(0.55, d));
        this.dirty = true;
    }

    public void setHudHeight(UUID uUID, int n) {
        Pref pref = this.of(uUID);
        pref.hudH = Math.max(0, Math.min(600, n));
        this.dirty = true;
    }

    public void setVisible(UUID uUID, boolean bl) {
        Pref pref = this.of(uUID);
        pref.visible = bl;
        this.dirty = true;
    }

    public void resetPosition(UUID uUID) {
        Pref pref = this.of(uUID);
        pref.x = this.plugin.spConfig().offsetX();
        pref.y = this.plugin.spConfig().offsetY();
        pref.custom = false;
        this.dirty = true;
    }

    public synchronized void flushIfDirty() {
        if (!this.dirty) {
            return;
        }
        this.flush();
    }

    public synchronized void flush() {
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        for (Map.Entry<UUID, Pref> entry : this.prefs.entrySet()) {
            Pref pref = entry.getValue();
            if (!pref.custom && pref.visible) continue;
            String string = "players." + String.valueOf(entry.getKey());
            yamlConfiguration.set(string + ".x", (Object)pref.x);
            yamlConfiguration.set(string + ".y", (Object)pref.y);
            yamlConfiguration.set(string + ".visible", (Object)pref.visible);
            yamlConfiguration.set(string + ".custom", (Object)pref.custom);
            yamlConfiguration.set(string + ".scale", (Object)pref.scale);
            yamlConfiguration.set(string + ".uiScale", (Object)pref.uiScale);
            yamlConfiguration.set(string + ".hudW", (Object)pref.hudW);
            yamlConfiguration.set(string + ".hudH", (Object)pref.hudH);
            yamlConfiguration.set(string + ".contentScale", (Object)pref.contentScale);
            yamlConfiguration.set(string + ".leftRatio", (Object)pref.leftRatio);
        }
        try {
            if (!this.plugin.getDataFolder().exists() && !this.plugin.getDataFolder().mkdirs()) {
                this.plugin.getLogger().warning("无法创建数据目录: " + String.valueOf(this.plugin.getDataFolder()));
            }
            yamlConfiguration.save(this.file);
            this.dirty = false;
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("保存 hud-positions.yml 失败: " + iOException.getMessage());
        }
    }

    public static final class Pref {
        public volatile int x;
        public volatile int y;
        public volatile boolean visible = true;
        public volatile double scale = 1.0;
        public volatile double uiScale = 1.0;
        public volatile int hudW = 0;
        public volatile int hudH = 0;
        public volatile double contentScale = 1.0;
        public volatile double leftRatio = 0.3;
        public volatile boolean custom;

        Pref(int n, int n2, boolean bl, boolean bl2, double d, double d2, int n3, int n4, double d3, double d4) {
            this.x = n;
            this.y = n2;
            this.visible = bl;
            this.custom = bl2;
            this.scale = d;
            this.uiScale = d2;
            this.hudW = n3;
            this.hudH = n4;
            this.contentScale = d3;
            this.leftRatio = d4;
        }
    }
}

