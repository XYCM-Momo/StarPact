package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * 用反射注册 PanelListener，避免 ArcartX 缺失时类解析失败。
 */
public final class PanelListenerRegistrar {
    private PanelListenerRegistrar() {
    }

    public static boolean register(StarPactPlugin starPactPlugin) {
        if (Bukkit.getPluginManager().getPlugin("ArcartX") == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName("com.asterovie.momo.starpact.server.hud.PanelListener", true, PanelListenerRegistrar.class.getClassLoader());
            Object obj = clazz.getConstructor(StarPactPlugin.class).newInstance(new Object[]{starPactPlugin});
            Bukkit.getPluginManager().registerEvents((Listener)obj, (Plugin)starPactPlugin);
            starPactPlugin.getLogger().info("已注册任务面板交互监听");
            return true;
        }
        catch (Throwable throwable) {
            starPactPlugin.getLogger().warning("注册任务面板监听失败: " + String.valueOf(throwable));
            return false;
        }
    }
}

