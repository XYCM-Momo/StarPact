package com.asterovie.momo.starpact.server;

import com.asterovie.momo.starpact.server.ax.AxHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

/**
 * 玩家生命周期 → HUD 刷新。
 *
 * <p>这些都是「HUD 可能丢」的时机：进服、换世界、传送、重生后客户端 UI 可能被重置，
 * 所以每个时机都强制重推一次。
 */
public final class PlayerListener implements Listener {

    private final StarPactPlugin plugin;

    public PlayerListener(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // ArcartX 的按键表是随 setting 包下发的。插件注册的按键要在这个包之后
        // 才会出现在客户端按键设置里，所以进服后补发一次。
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.ax().sendSettings(p);
                plugin.hud().refresh(p, true);
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.hud().onQuit(event.getPlayer());
        plugin.panel().onQuit(event.getPlayer());
        plugin.prefs().flushIfDirty();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        refreshSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        refreshSoon(event.getPlayer());
    }

    private void refreshSoon(Player p) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.hud().refresh(p, true);
            }
        }, 10L);
    }

    /**
     * 接收客户端拖动回传的位置。
     *
     * <p>AX 的 UI 包走的是 AX 自己的通道，不经过 Bukkit 事件，
     * 所以这里只提供一个入口给「自定义 packet 回调」用（见 AxHook 的说明）。
     * 当前版本通过 /sp move 调整，这个方法是给后续接 AX 回调预留的。
     */
    public void onClientPosition(Player p, int x, int y) {
        if (p == null || !plugin.spConfig().allowPlayerMove()) {
            return;
        }
        plugin.prefs().setPosition(p.getUniqueId(), x, y);
        plugin.hud().applyPosition(p);
    }

    /** 未使用，保留以说明 AX 回调的接入点。 */
    @SuppressWarnings("unused")
    private static void unused(List<AxHook> ignored) {
    }
}
