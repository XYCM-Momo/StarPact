package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import priv.seventeen.artist.arcartx.event.client.ClientCustomPacketEvent;
import priv.seventeen.artist.arcartx.event.client.ClientMenuCloseEvent;

/**
 * 监听 ArcartX 客户端发来的自定义包，驱动面板交互。
 * <b>本类直接 import 了 ArcartX 类</b>，所以只在确认 ArcartX 存在时才注册
 * （见 PanelListenerRegistrar）。
 */
public final class PanelListener
implements Listener {
    public static final String PREFIX = "星契:";
    private final StarPactPlugin plugin;

    public PanelListener(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
    }

    @EventHandler(priority=EventPriority.NORMAL)
    public void onCustomPacket(ClientCustomPacketEvent clientCustomPacketEvent) {
        String string;
        Player player = clientCustomPacketEvent.getPlayer();
        if (player == null) {
            return;
        }
        String string2 = clientCustomPacketEvent.getId();
        if (this.plugin.spConfig().debug()) {
            this.plugin.getLogger().info("[包] " + player.getName() + " id=" + string2 + " data=" + String.valueOf(clientCustomPacketEvent.getData()));
        }
        if (string2 == null || !string2.startsWith(PREFIX)) {
            return;
        }
        String string3 = string = string2.substring(PREFIX.length());
        String string4 = null;
        int n = string.indexOf(58);
        if (n >= 0) {
            string3 = string.substring(0, n);
            string4 = string.substring(n + 1);
        }
        switch (string3) {
            case "ready": {
                this.plugin.panel().allowReady(player.getUniqueId());
                break;
            }
            case "close": {
                this.plugin.panel().clickSound(player);
                this.plugin.panel().closePanel(player);
                break;
            }
            case "page": {
                this.plugin.panel().onPage(player, string4 == null ? "shixu" : string4);
                break;
            }
            case "hud_edit": {
                this.plugin.panel().onHudEdit(player);
                break;
            }
            case "hudset_ready": {
                this.plugin.panel().pushHudSettings(player);
                break;
            }
            case "hudset_close": {
                this.plugin.panel().onHudPanelClose(player);
                break;
            }
            case "hud_toggle": {
                this.plugin.panel().onToggleHud(player);
                break;
            }
            case "hud_scale": {
                int n2 = string4 == null ? 0 : Integer.parseInt(string4.trim());
                this.plugin.panel().onScaleHud(player, n2);
                break;
            }
            case "hud_w": {
                int n3 = string4 == null ? 0 : Integer.parseInt(string4.trim());
                this.plugin.panel().onHudWidth(player, n3);
                break;
            }
            case "hud_reset": {
                this.plugin.panel().onResetHud(player);
                break;
            }
            case "hud_move": {
                String[] stringArray;
                int n4 = 0;
                int n5 = 0;
                if (string4 != null && (stringArray = string4.split(":")).length >= 2) {
                    n4 = Integer.parseInt(stringArray[0].trim());
                    n5 = Integer.parseInt(stringArray[1].trim());
                }
                this.plugin.panel().onMoveHud(player, n4, n5);
                break;
            }
            case "hudpanel_close": {
                this.plugin.panel().onHudPanelClose(player);
                break;
            }
            case "content_scale": {
                int n6 = string4 == null ? 0 : Integer.parseInt(string4.trim());
                this.plugin.panel().onContentScale(player, n6);
                break;
            }
            case "left_ratio": {
                int n7 = string4 == null ? 0 : Integer.parseInt(string4.trim());
                this.plugin.panel().onLeftRatio(player, n7);
                break;
            }
            case "ui_scale": {
                int n8 = string4 == null ? 0 : Integer.parseInt(string4.trim());
                this.plugin.panel().onUiScale(player, n8);
                break;
            }
            case "quest": {
                this.plugin.panel().onSelectQuest(player, string4);
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onMenuClose(ClientMenuCloseEvent clientMenuCloseEvent) {
        Player player = clientMenuCloseEvent.getPlayer();
        if (player != null) {
            this.plugin.panel().renderer().forget(player.getUniqueId());
        }
    }
}

