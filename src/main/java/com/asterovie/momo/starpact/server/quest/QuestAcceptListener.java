package com.asterovie.momo.starpact.server.quest;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * 监听 Chemdah「任务接取」事件，接取后立刻刷新 HUD。
 *
 * <p>没有它的话，HUD 要等下一次轮询（默认 2 秒）才会更新 ——
 * 玩家能明显感觉到「接了任务但 HUD 半天不出来」。
 *
 * <p>全程反射访问 Chemdah 的类型，本类不 import 任何 Chemdah 类。
 */
public class QuestAcceptListener implements Listener {

    private final StarPactPlugin plugin;

    public QuestAcceptListener(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean register(Class<?> eventClass) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> ec = (Class<? extends Event>) eventClass;
            Bukkit.getPluginManager().registerEvent(
                    ec,
                    this,
                    EventPriority.MONITOR,
                    (EventExecutor) (listener, event) -> handle(event),
                    plugin,
                    false);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("绑定任务接取事件失败: " + t);
            return false;
        }
    }

    private void handle(Event event) {
        try {
            Object profile = event.getClass().getMethod("getPlayerProfile").invoke(event);
            if (profile == null) {
                return;
            }
            Object playerObj = profile.getClass().getMethod("getPlayer").invoke(profile);
            if (!(playerObj instanceof Player player) || !player.isOnline()) {
                return;
            }
            // 下一 tick 再刷：此刻 Chemdah 可能还在写任务数据，
            // 立刻读会读到不完整的状态。
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.hud().refresh(player, true);
                }
            }, 1L);
        } catch (Throwable t) {
            plugin.getLogger().fine("处理任务接取事件失败: " + t);
        }
    }
}
