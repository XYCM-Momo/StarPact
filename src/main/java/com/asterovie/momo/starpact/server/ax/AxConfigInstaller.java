package com.asterovie.momo.starpact.server.ax;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 把按键相关的三份配置写进 ArcartX 的数据目录。
 *
 * <p>为什么要写文件而不是纯 API：
 * 实测 {@code ArcartXKeyBindRegistry.registerClientKeyBind} 只往服务端内存 map 写，
 * 即使随后补发 {@code SPackSettings}，客户端按键表里也不会出现该按键。
 * 走 ArcartX 自己的配置文件加载路径（它启动时会「加载N个客户端按键」）才稳定。
 *
 * <p>写三份：
 * <pre>
 * plugins/ArcartX/key_bind/client_key/星契.yml    注册 J 到客户端按键表
 * plugins/ArcartX/key_bind/key_group/星契.yml     监听该按键
 * plugins/ArcartX/scripts/星契.yml                按键触发时打开面板
 * </pre>
 *
 * <p>只在文件不存在时写，避免覆盖服主的改动。
 */
public final class AxConfigInstaller {

    /*
     * 按键用 client_key + key_group。
     *
     * ⚠️ 不要换回 simple_key：实测 simple_key 只在服务端解析，
     * 客户端根本不会发送按键包，按 J 完全没反应（服务端日志里连包都收不到）。
     *
     * client_key 会把按键注册到客户端（选项→控制 里可见，玩家可改键），
     * key_group 负责监听并触发脚本，interval 控制最短触发间隔。
     * 连发问题由 key_group 的 interval + 代码里的防抖一起解决。
     */
    private static final String DIR_CLIENT_KEY = "key_bind/client_key";
    private static final String DIR_KEY_GROUP = "key_bind/key_group";
    private static final String DIR_SCRIPTS = "scripts";

    /** 旧方案留下的目录（simple_key 不工作，需要清理） */
    private static final String[] LEGACY_DIRS = {
            "key_bind/simple_key"
    };

    private final StarPactPlugin plugin;

    public AxConfigInstaller(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return 是否有文件被新建（用于决定要不要让 AX 重载） */
    public boolean install() {
        Plugin ax = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (ax == null) {
            return false;
        }
        File axDir = ax.getDataFolder();
        // 先清理旧方案的文件，避免 J 被绑定两次
        cleanupLegacy(axDir);
        boolean changed = false;
        changed |= writeIfAbsent(axDir, DIR_CLIENT_KEY, "星契.yml", "ax/client_key.yml");
        changed |= writeIfAbsent(axDir, DIR_KEY_GROUP, "星契.yml", "ax/key_group.yml");
        changed |= writeIfAbsent(axDir, DIR_SCRIPTS, "星契.yml", "ax/script.yml");
        return changed;
    }

    /** 删除本插件写进去的那三份（用于卸载清理）。 */
    public void uninstall() {
        Plugin ax = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (ax == null) {
            return;
        }
        File axDir = ax.getDataFolder();
        delete(axDir, DIR_CLIENT_KEY, "星契.yml");
        delete(axDir, DIR_KEY_GROUP, "星契.yml");
        delete(axDir, DIR_SCRIPTS, "星契.yml");
        cleanupLegacy(axDir);
    }

    /** 删掉旧方案（client_key + key_group）留下的文件。 */
    private void cleanupLegacy(File axDir) {
        for (String dir : LEGACY_DIRS) {
            File f = new File(new File(axDir, dir), "星契.yml");
            if (f.exists()) {
                if (f.delete()) {
                    plugin.getLogger().info("已清理旧按键配置: " + dir + "/星契.yml");
                }
            }
        }
    }

    private boolean writeIfAbsent(File axDir, String dir, String fileName, String resource) {
        File target = new File(new File(axDir, dir), fileName);
        if (target.exists()) {
            return false;
        }
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("无法创建目录: " + parent);
                return false;
            }
            try (InputStream in = plugin.getResource(resource)) {
                if (in == null) {
                    plugin.getLogger().warning("jar 内缺少资源: " + resource);
                    return false;
                }
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                // client_key 的默认键取自 config.yml 的 key.default
                if ("ax/client_key.yml".equals(resource)) {
                    // client_key 的默认键写成 default: "J"
                    content = content.replace("default: \"J\"",
                            "default: \"" + plugin.spConfig().defaultKey() + "\"");
                }
                Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
            }
            plugin.getLogger().info("已写入 ArcartX 配置: " + dir + "/" + fileName);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("写入 " + target + " 失败: " + e.getMessage());
            return false;
        }
    }

    private void delete(File axDir, String dir, String fileName) {
        Path p = new File(new File(axDir, dir), fileName).toPath();
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // 删不掉就算了，不影响功能
        }
    }
}
