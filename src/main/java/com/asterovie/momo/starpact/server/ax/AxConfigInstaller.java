package com.asterovie.momo.starpact.server.ax;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 把插件内置的 ArcartX 配置（simple_key / script）写入 AX 的数据目录。
 * 只在文件不存在时写入，不覆盖玩家自己的修改。
 */
public final class AxConfigInstaller {
    private static final String DIR_SIMPLE_KEY = "key_bind/simple_key";
    private static final String DIR_SCRIPTS = "scripts";
    private static final String[] LEGACY_DIRS = new String[]{"key_bind/client_key", "key_bind/key_group"};
    private final StarPactPlugin plugin;

    public AxConfigInstaller(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
    }

    public boolean install() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (plugin == null) {
            return false;
        }
        File file = plugin.getDataFolder();
        this.cleanupLegacy(file);
        boolean bl = false;
        bl |= this.writeIfAbsent(file, DIR_SIMPLE_KEY, "星契.yml", "ax/simple_key.yml");
        return bl |= this.writeIfAbsent(file, DIR_SCRIPTS, "星契.yml", "ax/script.yml");
    }

    public void uninstall() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (plugin == null) {
            return;
        }
        File file = plugin.getDataFolder();
        this.delete(file, DIR_SIMPLE_KEY, "星契.yml");
        this.delete(file, DIR_SCRIPTS, "星契.yml");
        this.cleanupLegacy(file);
    }

    private void cleanupLegacy(File file) {
        for (String string : LEGACY_DIRS) {
            File file2 = new File(new File(file, string), "星契.yml");
            if (!file2.exists() || !file2.delete()) continue;
            this.plugin.getLogger().info("已清理旧按键配置: " + string + "/星契.yml");
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private boolean writeIfAbsent(File file, String string, String string2, String string3) {
        File file2 = new File(new File(file, string), string2);
        boolean bl = file2.exists();
        try {
            File file3 = file2.getParentFile();
            if (file3 != null && !file3.exists() && !file3.mkdirs()) {
                this.plugin.getLogger().warning("无法创建目录: " + String.valueOf(file3));
                return false;
            }
            try (InputStream inputStream = this.plugin.getResource(string3);){
                String string4;
                if (inputStream == null) {
                    boolean bl2;
                    this.plugin.getLogger().warning("jar 内缺少资源: " + string3);
                    boolean bl3 = bl2 = false;
                    return bl3;
                }
                String string5 = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                if ("ax/simple_key.yml".equals(string3)) {
                    string5 = string5.replace("    - \"J\"", "    - \"" + this.plugin.spConfig().defaultKey() + "\"");
                }
                if (bl && (string4 = new String(Files.readAllBytes(file2.toPath()), StandardCharsets.UTF_8)).equals(string5)) {
                    boolean bl4 = false;
                    return bl4;
                }
                Files.writeString(file2.toPath(), (CharSequence)string5, StandardCharsets.UTF_8, new OpenOption[0]);
            }
            this.plugin.getLogger().info("已写入 ArcartX 配置: " + string + "/" + string2);
            return true;
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("写入 " + String.valueOf(file2) + " 失败: " + iOException.getMessage());
            return false;
        }
    }

    private void delete(File file, String string, String string2) {
        Path path = new File(new File(file, string), string2).toPath();
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }
}

