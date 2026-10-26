package com.asterovie.momo.starpact.server.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * language.yml 的加载与读取。
 */
public final class LanguageManager {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration lang;
    private YamlConfiguration defaults;

    public LanguageManager(JavaPlugin javaPlugin) {
        this.plugin = javaPlugin;
        this.file = new File(javaPlugin.getDataFolder(), "language.yml");
        this.loadDefaults();
        this.reload();
    }

    private void loadDefaults() {
        try (InputStream inputStream = this.plugin.getResource("language.yml");){
            if (inputStream != null) {
                this.defaults = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            }
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("读取内置 language.yml 失败: " + iOException.getMessage());
        }
    }

    public void reload() {
        if (!this.file.exists()) {
            this.plugin.saveResource("language.yml", false);
        }
        this.lang = YamlConfiguration.loadConfiguration((File)this.file);
        if (this.defaults != null) {
            this.lang.setDefaults((Configuration)this.defaults);
        }
    }

    public String getString(String string, String string2) {
        if (this.lang == null) {
            return string2;
        }
        String string3 = this.lang.getString(string);
        return string3 == null ? string2 : string3;
    }

    public List<String> getStringList(String string) {
        if (this.lang == null) {
            return List.of();
        }
        List list = this.lang.getStringList(string);
        return list == null ? List.of() : list;
    }
}

