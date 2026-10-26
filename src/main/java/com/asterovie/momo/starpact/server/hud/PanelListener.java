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
 *
 * <p>⚠️ 这个类直接 import 了 ArcartX 的类，所以**只在确认 ArcartX 存在时才注册**
 * （见 StarPactPlugin）。否则会像 Chemdah 那样在类解析阶段抛
 * NoClassDefFoundError。注册逻辑用反射包一层，见 {@link PanelListenerRegistrar}。
 */
public final class PanelListener implements Listener {

    /**
     * UI 里发的包 ID 前缀。
     *
     * <p>动作一律编码在 ID 里（如 {@code starpact:page:shixu}），不依赖 data 参数 ——
     * 实测 {@code Packet.send(id, arg)} 的第二个参数在服务端未必能稳定取到，
     * 而 {@code event.getId()} 是可靠的。
     */
    public static final String PREFIX = "starpact:";

    private final StarPactPlugin plugin;

    public PanelListener(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCustomPacket(ClientCustomPacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        String id = event.getId();
        if (plugin.spConfig().debug()) {
            plugin.getLogger().info("[包] " + player.getName()
                    + " id=" + id
                    + " data=" + event.getData());
        }
        if (id == null || !id.startsWith(PREFIX)) {
            return;
        }
        String action = id.substring(PREFIX.length());

        String head = action;
        String arg = null;
        int colon = action.indexOf(':');
        if (colon >= 0) {
            head = action.substring(0, colon);
            arg = action.substring(colon + 1);
        }

        switch (head) {
            // UI 就绪通知。
            // ⚠️ 这里**不推数据**。open 触发器在每次重绘时都会触发，
            // 一旦在这里 push 就会 ready → push → 重绘 → ready 死循环
            // （HUD 踩过，表现为界面狂闪）。数据由 openPanel() 推。
            case "ready" -> {
                // 只做节流记录，不推数据
                plugin.panel().allowReady(player.getUniqueId());
            }
            // 点 × 关闭
            case "close" -> {
                plugin.panel().clickSound(player);
                plugin.panel().closePanel(player);
            }
            // 顶栏图标切页：starpact:page:shixu / jian / can / setting
            case "page" -> plugin.panel().onPage(player, arg == null ? "shixu" : arg);
            // 设置页「打开 HUD 设置」：关面板，让玩家看到 HUD
            case "hud_edit" -> plugin.panel().onHudEdit(player);
            case "hudset_ready" -> plugin.panel().pushHudSettings(player);
            case "hudset_close" -> plugin.panel().onHudPanelClose(player);
            case "hud_toggle" -> plugin.panel().onToggleHud(player);
            case "hud_scale" -> {
                int d = (arg == null) ? 0 : Integer.parseInt(arg.trim());
                plugin.panel().onScaleHud(player, d);
            }
            case "hud_w" -> {
                int d = (arg == null) ? 0 : Integer.parseInt(arg.trim());
                plugin.panel().onHudWidth(player, d);
            }
            case "hud_reset" -> plugin.panel().onResetHud(player);
            case "hud_move" -> {
                int dx = 0, dy = 0;
                if (arg != null) {
                    String[] parts = arg.split(":");
                    if (parts.length >= 2) {
                        dx = Integer.parseInt(parts[0].trim());
                        dy = Integer.parseInt(parts[1].trim());
                    }
                }
                plugin.panel().onMoveHud(player, dx, dy);
            }
            case "hudpanel_close" -> plugin.panel().onHudPanelClose(player);
            case "ui_scale" -> {
                int d = (arg == null) ? 0 : Integer.parseInt(arg.trim());
                plugin.panel().onScaleUi(player, d);
            }
            case "panel_alpha" -> {
                int d = (arg == null) ? 0 : Integer.parseInt(arg.trim());
                plugin.panel().onPanelAlpha(player, d);
            }
            case "left_ratio" -> {
                int d = (arg == null) ? 0 : Integer.parseInt(arg.trim());
                plugin.panel().onLeftRatio(player, d);
            }
            // 左栏选中任务：starpact:quest:<任务ID>
            case "quest" -> plugin.panel().onSelectQuest(player, arg);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuClose(ClientMenuCloseEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            // 只清理面板相关缓存，不影响 HUD
            plugin.panel().renderer().forget(player.getUniqueId());
        }
    }
}
