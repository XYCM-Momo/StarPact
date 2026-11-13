package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 让奖励容器变成「只读」。
 *
 * <p>奖励物品放在一个真实的箱子里（AX 的 Slot 控件只认真实容器槽位），
 * 但那是展示用的，玩家不该能把东西拿走。这里把所有交互事件取消掉。
 *
 * <p>这个类只用 Bukkit API，不引用 ArcartX，所以可以无条件注册。
 */
public final class RewardContainerListener implements Listener {

    private final StarPactPlugin plugin;

    public RewardContainerListener(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    /** 是不是我们的奖励容器。 */
    private boolean isRewardContainer(org.bukkit.inventory.Inventory inv) {
        return inv != null && inv.getHolder() instanceof PanelService.RewardHolder;
    }

    /**
     * 把点击事件转成「什么都没发生」。
     *
     * <p>之前用 {@code setCancelled(true)} + {@code updateInventory()}：
     * 客户端已经先执行了「拿起物品」，服务端才拒绝，再强制刷新背包，
     * 表现为物品闪一下。
     *
     * <p>改成 {@code DENY} 并在 HIGHEST 优先级最后跑，等于告诉服务端
     * 「这次点击什么也不做」，客户端不会产生拿起动作，也就没有回弹闪烁。
     * 悬停提示不受影响——那是客户端自己画 tooltip，不经过点击事件。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!isRewardContainer(event.getInventory())
                && !isRewardContainer(event.getView().getTopInventory())) {
            return;
        }
        event.setResult(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!isRewardContainer(event.getView().getTopInventory())) {
            return;
        }
        event.setResult(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!isRewardContainer(event.getView().getTopInventory())) {
            return;
        }
        if (event.getPlayer() instanceof Player p) {
            plugin.panel().onRewardContainerClose(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.panel().onRewardContainerClose(event.getPlayer());
    }
}
