package com.asterovie.momo.starpact.server.quest;

import com.asterovie.momo.starpact.server.StarPactPlugin;

/**
 * 反射注册 {@link QuestAcceptListener}。
 *
 * <p>与 {@link QuestRewardRegistrar} 同样的理由：Chemdah 没装时事件类不存在，
 * 直接跳过，绝不能让 {@code NoClassDefFoundError} 打断插件启用。
 */
public final class QuestAcceptRegistrar {

    private static final String EVENT_CLASS =
            "ink.ptms.chemdah.api.event.collect.QuestEvents$Accept$Post";

    private QuestAcceptRegistrar() {
    }

    public static void register(StarPactPlugin plugin) {
        Class<?> eventClass;
        try {
            eventClass = Class.forName(EVENT_CLASS, false,
                    QuestAcceptRegistrar.class.getClassLoader());
        } catch (Throwable t) {
            plugin.getLogger().info("未检测到 Chemdah 任务接取事件，HUD 仅靠轮询刷新");
            return;
        }
        try {
            Object listener = new QuestAcceptListener(plugin);
            Object ok = listener.getClass()
                    .getMethod("register", Class.class)
                    .invoke(listener, eventClass);
            if (Boolean.TRUE.equals(ok)) {
                plugin.getLogger().info("已注册任务接取监听（接取后立即刷新 HUD）");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("注册任务接取监听失败: " + t);
        }
    }
}
