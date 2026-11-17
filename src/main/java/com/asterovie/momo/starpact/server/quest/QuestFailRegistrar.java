package com.asterovie.momo.starpact.server.quest;

import com.asterovie.momo.starpact.server.StarPactPlugin;

/**
 * 反射注册 {@link QuestFailListener}。
 *
 * <p>要监听两个事件：
 * <ul>
 *   <li>{@code QuestEvents$Fail$Post} —— 任务自然失败（超时等）</li>
 *   <li>{@code QuestEvents$Unregistered} —— {@code /chq stop} 取消任务</li>
 * </ul>
 * 只监听前者时，玩家用 stop 取消任务，停导航的指令不会执行。
 *
 * <p>Chemdah 没装时事件类不存在，直接跳过，不能让
 * {@code NoClassDefFoundError} 打断插件启用。
 */
public final class QuestFailRegistrar {

    private static final String[] EVENT_CLASSES = {
            "ink.ptms.chemdah.api.event.collect.QuestEvents$Fail$Post",
            "ink.ptms.chemdah.api.event.collect.QuestEvents$Unregistered",
    };

    private QuestFailRegistrar() {
    }

    public static void register(StarPactPlugin plugin) {
        Object listener = new QuestFailListener(plugin);
        int ok = 0;
        for (String name : EVENT_CLASSES) {
            Class<?> eventClass;
            try {
                eventClass = Class.forName(name, false,
                        QuestFailRegistrar.class.getClassLoader());
            } catch (Throwable t) {
                plugin.getLogger().info("未检测到 " + name + "，跳过");
                continue;
            }
            try {
                Object r = listener.getClass()
                        .getMethod("register", Class.class)
                        .invoke(listener, eventClass);
                if (Boolean.TRUE.equals(r)) {
                    ok++;
                    plugin.getLogger().info("已监听 " + name.substring(name.lastIndexOf('$') + 1));
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("注册 " + name + " 失败: " + t);
            }
        }
        if (ok > 0) {
            plugin.getLogger().info("已注册任务失败监听 x" + ok
                    + "（失败/取消时执行 fail-commands）");
        }
    }
}
