package com.asterovie.momo.starpact.server.chemdah;

import com.asterovie.momo.starpact.server.model.TrackEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Chemdah 读取层的门面 —— <b>故意不 import 任何 Chemdah 类</b>。
 *
 * <p>为什么这么绕：Chemdah 没装时，任何引用 {@code ink.ptms.chemdah.*} 的类型
 * 都会在类解析阶段抛 {@link NoClassDefFoundError}，而这个错误不能被 try/catch 捕获，
 * 会把插件启用流程直接打断（实测踩到过：{@code NoClassDefFoundError:
 * ink/ptms/chemdah/core/quest/QuestContainer}）。
 *
 * <p>所以这里只用 {@link Class#forName} 判断存在性，确认存在之后才反射调用
 * {@link ChemdahReader}。这样「Chemdah 缺失」退化为「HUD 显示空列表」，
 * 而不是「插件启动失败」。
 */
public final class ChemdahHook {

    private final boolean available;
    private Method readMethod;

    public ChemdahHook() {
        boolean ok = false;
        try {
            // 只探类名，不解析符号
            Class.forName("ink.ptms.chemdah.api.ChemdahAPI", false,
                    ChemdahHook.class.getClassLoader());
            ok = Bukkit.getPluginManager().getPlugin("Chemdah") != null;
        } catch (Throwable ignored) {
            ok = false;
        }
        this.available = ok;

        if (available) {
            try {
                Method m = Class.forName(
                                "com.asterovie.momo.starpact.server.chemdah.ChemdahReader",
                                true, ChemdahHook.class.getClassLoader())
                        .getDeclaredMethod("read", Player.class, int.class);
                m.setAccessible(true);
                this.readMethod = m;
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[StarPact] Chemdah 读取器初始化失败: " + t);
            }
        }
    }

    public boolean available() {
        return available;
    }

    private Method readQuestsMethod;

    /**
     * 读取玩家接取中的所有任务（面板用）。
     *
     * <p>和 {@link #readTracks} 一样走反射 + 惰性加载，避免 Chemdah 未安装时
     * 触发 NoClassDefFoundError。返回空列表表示没有任务或读取失败。
     */
    @SuppressWarnings("unchecked")
    public List<com.asterovie.momo.starpact.server.model.QuestInfo> readQuests(Player player) {
        if (!available || player == null) {
            return List.of();
        }
        try {
            if (readQuestsMethod == null) {
                readQuestsMethod = Class.forName(
                                "com.asterovie.momo.starpact.server.chemdah.ChemdahReader",
                                true, ChemdahHook.class.getClassLoader())
                        .getDeclaredMethod("readQuests", Player.class);
                readQuestsMethod.setAccessible(true);
            }
            Object r = readQuestsMethod.invoke(null, player);
            if (r instanceof List<?> list) {
                return (List<com.asterovie.momo.starpact.server.model.QuestInfo>) list;
            }
        } catch (Throwable ignored) {
            // 读取失败按空列表处理
        }
        return List.of();
    }

    /**
     * 读取玩家当前应显示的追踪条目。永远不会抛异常。
     */
    @SuppressWarnings("unchecked")
    public List<TrackEntry> readTracks(Player player, int max) {
        if (!available || readMethod == null || player == null || max <= 0) {
            return List.of();
        }
        try {
            Object result = readMethod.invoke(null, player, max);
            if (result instanceof List<?> list) {
                return (List<TrackEntry>) list;
            }
            return List.of();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }
}
