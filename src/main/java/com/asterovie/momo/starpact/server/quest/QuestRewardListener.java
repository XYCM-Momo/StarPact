package com.asterovie.momo.starpact.server.quest;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

/**
 * 任务完成时发放 quests/ 里配置的奖励。
 *
 * <p>为什么由 StarPact 发、而不是写在 Chemdah 的 agent 里：
 * <ul>
 *   <li>奖励配置本来就在 StarPact 的 {@code quests/} 里（含 MM/NI 物品库解析）</li>
 *   <li>Chemdah 的 agent 是 Kether 脚本，给物品还要额外套一层 MM/NI 解析，
 *       两处配置容易不一致</li>
 *   <li>「Chemdah 管进度、StarPact 管奖励与展示」职责更清晰</li>
 * </ul>
 *
 * <p><b>为什么用 {@link EventExecutor} 动态注册、而不是 {@code @EventHandler}：</b>
 * Chemdah 的事件类继承自 TabooLib 的 {@code BukkitProxyEvent}，那个基类
 * 不在编译期 classpath 里，无法在方法签名里写死类型；而用 {@code Event}
 * 当参数又会被 Bukkit 拒绝（{@code Static getHandlerList method required}）。
 * 所以运行期拿到具体事件 Class，再 {@code registerEvent} 精确绑定。
 */
public final class QuestRewardListener implements Listener {

    private final StarPactPlugin plugin;

    public QuestRewardListener(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 把本监听器绑到「Chemdah 任务完成」这一个具体事件上。
     *
     * @return 成功返回 true
     */
    @SuppressWarnings("unchecked")
    public boolean register(Class<?> eventClass) {
        try {
            Class<? extends Event> evt = (Class<? extends Event>) eventClass;
            // MONITOR：只观察，不参与取消
            Bukkit.getPluginManager().registerEvent(
                    evt,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> {
                        if (evt.isInstance(event)) {
                            handleComplete(event);
                        }
                    },
                    plugin,
                    false);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("绑定任务完成事件失败: " + t);
            return false;
        }
    }

    private void handleComplete(Event event) {
        // 同 QuestFailListener：必须先校验事件类型，
        // registerEvent 不会帮我们过滤，实测会收到大量不相关事件。
        if (!"ink.ptms.chemdah.api.event.collect.QuestEvents$Complete$Post"
                .equals(event.getClass().getName())) {
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

            // 任务完成音效：放在「有没有奖励配置」判断之前，
            // 这样即使这个任务没配奖励，完成时也有反馈。
            try {
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 1.5f);
            } catch (Throwable ignored) {
                // 音效失败不影响发奖
            }

            // 完成指令（比如停掉导航）：必须在「有没有奖励」判断之前执行，
            // 否则没配奖励的任务就跳过这一段了。
            plugin.panel().runCompleteCommands(player, questId);

            var loader = plugin.quests();
            var items = plugin.items();
            if (loader == null || items == null) {
                return;
            }
            QuestConfig cfg = loader.get(questId);
            if (cfg == null || cfg.rewards().isEmpty()) {
                return;
            }

            int given = 0;
            for (QuestConfig.Reward r : cfg.rewards()) {
                // 指令型奖励（经验、点券、权限…）：以控制台身份执行
                if (r.isCommand()) {
                    String cmd = r.key().replace("{player}", player.getName());
                    if (cmd.startsWith("/")) {
                        cmd = cmd.substring(1);
                    }
                    try {
                        boolean ok = org.bukkit.Bukkit.dispatchCommand(
                                org.bukkit.Bukkit.getConsoleSender(), cmd);
                        if (!ok) {
                            plugin.getLogger().warning("[奖励发放] 指令执行失败: " + cmd);
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[奖励发放] 指令异常: " + cmd + " -> " + t);
                    }
                    given++;
                    continue;
                }
                ItemStack stack = items.resolve(r);
                if (stack == null) {
                    plugin.getLogger().warning("[奖励发放] 无法解析 "
                            + r.provider() + ":" + r.key());
                    continue;
                }
                // 背包满了就掉在脚下，不让玩家白做任务
                var leftover = player.getInventory().addItem(stack);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                given++;
            }

            if (given > 0) {
                player.sendMessage(com.asterovie.momo.starpact.server.util.Texts.colorize(
                        "&b[星契] &7任务奖励已发放 &8(" + given + " 项)"));

            }
        } catch (Throwable t) {
            plugin.getLogger().warning("发放任务奖励失败: " + t);
        }
    }
}
