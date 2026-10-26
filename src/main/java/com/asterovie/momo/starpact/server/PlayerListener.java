package com.asterovie.momo.starpact.server;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.ax.AxHook;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/**
 * 玩家进出服事件。进服后延迟推送 HUD，退服时清理缓存。
 */
public final class PlayerListener
implements Listener {
    private final StarPactPlugin plugin;

    public PlayerListener(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        Player player = playerJoinEvent.getPlayer();
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            if (player.isOnline()) {
                this.plugin.ax().sendSettings(player);
                this.plugin.hud().refresh(player, true);
            }
        }, 40L);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.plugin.hud().onQuit(playerQuitEvent.getPlayer());
        this.plugin.panel().onQuit(playerQuitEvent.getPlayer());
        this.plugin.prefs().flushIfDirty();
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent playerChangedWorldEvent) {
        this.refreshSoon(playerChangedWorldEvent.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onTeleport(PlayerTeleportEvent playerTeleportEvent) {
        this.refreshSoon(playerTeleportEvent.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
        this.refreshSoon(playerRespawnEvent.getPlayer());
    }

    private void refreshSoon(Player player) {
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            if (player.isOnline()) {
                this.plugin.hud().refresh(player, true);
            }
        }, 10L);
    }

    public void onClientPosition(Player player, int n, int n2) {
        if (player == null || !this.plugin.spConfig().allowPlayerMove()) {
            return;
        }
        this.plugin.prefs().setPosition(player.getUniqueId(), n, n2);
        this.plugin.hud().applyPosition(player);
    }

    private static void unused(List<AxHook> list) {
    }
}

