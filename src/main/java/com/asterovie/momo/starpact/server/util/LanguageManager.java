package com.asterovie.momo.starpact.server.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * language.yml 的读取与热重载。
 *
 * <p>首次启动从 jar 里复制一份到数据目录；之后优先读数据目录那份，
 * 缺键时回落到 jar 内置的默认值（这样升级插件新增文案不会变成空串）。
 */
public final class LanguageManager {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration lang;
    private YamlConfiguration defaults;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "language.yml");
        loadDefaults();
        reload();
    }

    private void loadDefaults() {
        try (InputStream in = plugin.getResource("language.yml")) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("读取内置 language.yml 失败: " + e.getMessage());
        }
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("language.yml", false);
        }
        lang = YamlConfiguration.loadConfiguration(file);
        if (defaults != null) {
            lang.setDefaults(defaults);
        }
    }

    public String getString(String path, String fallback) {
        if (lang == null) {
            return fallback;
        }
        String v = lang.getString(path);
        return v == null ? fallback : v;
    }

    public java.util.List<String> getStringList(String path) {
        if (lang == null) {
            return java.util.List.of();
        }
        java.util.List<String> v = lang.getStringList(path);
        return v == null ? java.util.List.of() : v;
    }
}
