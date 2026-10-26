package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.Bukkit;

/**
 * {@link PanelListener} 的隔离注册器。
 *
 * <p>{@link PanelListener} 直接 import 了 ArcartX 的事件类。如果 ArcartX 没装，
 * JVM 在解析 PanelListener 时会抛 NoClassDefFoundError，而这个错误
 * **catch 不住**（和 Chemdah 那个坑一样）。
 *
 * <p>所以本类不 import 任何 AX 类，只在确认 ArcartX 存在后
 * 用反射 new 出 PanelListener 并注册为 Bukkit 监听器。
 */
public final class PanelListenerRegistrar {

    private PanelListenerRegistrar() {
    }

    /** 尝试注册。返回是否成功。 */
    public static boolean register(StarPactPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ArcartX") == null) {
            return false;
        }
        try {
            Class<?> cls = Class.forName(
                    "com.asterovie.momo.starpact.server.hud.PanelListener",
                    true, PanelListenerRegistrar.class.getClassLoader());
            Object listener = cls.getConstructor(StarPactPlugin.class)
                    .newInstance(plugin);
            Bukkit.getPluginManager().registerEvents(
                    (org.bukkit.event.Listener) listener, plugin);
            plugin.getLogger().info("已注册任务面板交互监听");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("注册任务面板监听失败: " + t);
            return false;
        }
    }
}
