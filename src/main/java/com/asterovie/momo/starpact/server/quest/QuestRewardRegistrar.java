package com.asterovie.momo.starpact.server.quest;

import com.asterovie.momo.starpact.server.StarPactPlugin;

/**
 * 反射注册 {@link QuestRewardListener}。
 *
 * <p>Chemdah 没装时，事件类不存在，这里直接跳过——绝不能让
 * {@code NoClassDefFoundError} 打断插件启用（那个错误在类加载阶段抛出，catch 不住）。
 */
public final class QuestRewardRegistrar {

    private static final String EVENT_CLASS =
            "ink.ptms.chemdah.api.event.collect.QuestEvents$Complete$Post";

    private QuestRewardRegistrar() {
    }

    public static void register(StarPactPlugin plugin) {
        Class<?> eventClass;
        try {
            eventClass = Class.forName(EVENT_CLASS, false,
                    QuestRewardRegistrar.class.getClassLoader());
        } catch (Throwable t) {
            plugin.getLogger().info("未检测到 Chemdah 任务完成事件，跳过奖励发放");
            return;
        }
        try {
            Object listener = Class.forName(
                            "com.asterovie.momo.starpact.server.quest.QuestRewardListener",
                            true, QuestRewardRegistrar.class.getClassLoader())
                    .getConstructor(StarPactPlugin.class)
                    .newInstance(plugin);
            Object ok = listener.getClass()
                    .getMethod("register", Class.class)
                    .invoke(listener, eventClass);
            if (Boolean.TRUE.equals(ok)) {
                plugin.getLogger().info("已注册任务奖励发放监听（任务完成时自动发奖）");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("注册任务奖励发放失败: " + t);
        }
    }
}
