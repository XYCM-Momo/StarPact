package com.asterovie.momo.starpact.server.quest;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * 监听 Chemdah「任务失败」事件，执行 quests/*.yml 里配的 {@code fail-commands}。
 *
 * <p>典型用途是停掉导航（{@code startrail nav stop {player}}）——
 * 任务都失败了还留着一条导航线很奇怪。
 *
 * <p>全程反射访问 Chemdah 的类型，本类不 import 任何 Chemdah 类。
 */
public class QuestFailListener implements Listener {

    private final StarPactPlugin plugin;

    public QuestFailListener(StarPactPlugin plugin) {
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
            plugin.getLogger().warning("绑定任务失败事件失败: " + t);
            return false;
        }
    }

    /**
     * 任务「失败」或「被取消」时要处理的事件类型。
     *
     * <p>实测结论（用诊断日志逐个确认过）：
     * <ul>
     *   <li>{@code /chq accept} → {@code QuestEvents$Accept$Post}</li>
     *   <li>{@code /chq stop}   → <b>{@code QuestEvents$Unregistered}</b>（不是 Fail！）</li>
     *   <li>任务自然失败/超时   → {@code QuestEvents$Fail$Post}</li>
     * </ul>
     * 之前只监听 Fail$Post，所以 {@code /ch quest stop} 取消任务时停导航完全不生效。
     */
    private static final java.util.Set<String> HANDLED = java.util.Set.of(
            "ink.ptms.chemdah.api.event.collect.QuestEvents$Fail$Post",
            "ink.ptms.chemdah.api.event.collect.QuestEvents$Unregistered");

    private void handle(Event event) {
        // ⚠️ 必须先校验事件类型。
        // Bukkit 的 registerEvent 只按注册的 Class 决定「是否调用」，
        // 但实测同一个监听器会收到几乎全部 Chemdah 事件
        // （Accept / DataSet / PlayerEvents / 甚至 PluginReloadEvent）。
        // 不校验就会把「接任务」当成「任务失败」。
        String type = event.getClass().getName();
        if (!HANDLED.contains(type)) {
            return;
        }
        try {
            Object profile = event.getClass().getMethod("getPlayerProfile").invoke(event);
            if (profile == null) {
                return;
            }
            Object playerObj = profile.getClass().getMethod("getPlayer").invoke(profile);
            if (!(playerObj instanceof Player player) || !player.isOnline()) {
                return;
            }
            Object quest = event.getClass().getMethod("getQuest").invoke(event);
            if (quest == null) {
                return;
            }
            Object idObj = quest.getClass().getMethod("getId").invoke(quest);
            if (!(idObj instanceof String questId) || questId.isBlank()) {
                return;
            }
            // 只有「自然失败」才检查任务状态：
            // Fail$Post 在接任务/完成任务时也会触发，需要靠 isValid/isCompleted 过滤掉。
            // Unregistered 是「被取消」，本身就是终态，不检查。
            if (type.endsWith("$Fail$Post")) {
                boolean valid = (Boolean) quest.getClass().getMethod("isValid").invoke(quest);
                boolean completed = (Boolean) quest.getClass()
                        .getMethod("isCompleted").invoke(quest);
                if (valid || completed) {
                    return;
                }
            }
            plugin.getLogger().info("[任务结束] " + player.getName() + " " + questId
                    + " (" + type.substring(type.lastIndexOf('$') + 1) + ")");
            plugin.panel().runFailCommands(player, questId);
        } catch (Throwable t) {
            plugin.getLogger().fine("处理任务失败事件失败: " + t);
        }
    }
}
