package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.util.Texts;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务面板的开关与交互。
 *
 * <p>交互链路：
 * <pre>
 * 按 J
 *   → AX simple_key 触发 scripts/星契.yml
 *   → PlayerUtils.dispatchOpCommand(player, "sp panel")
 *   → openPanel()：open UI + 推数据
 *
 * 点图标 / 任务
 *   → UI 里 Aria 执行 Packet.send('starpact:page:xxx' / 'starpact:quest:ID')
 *   → PanelListener 收到 ClientCustomPacketEvent
 *   → 本类处理并重新下发
 * </pre>
 */
public final class PanelService {

    public static final String KEY_ID = "星契任务面板";

    private final StarPactPlugin plugin;
    private final PanelRenderer renderer;

    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();

    public PanelService(StarPactPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new PanelRenderer(plugin);
    }

    public PanelRenderer renderer() {
        return renderer;
    }

    /** 打开面板（按 J 或 /sp panel）。 */
    public void openPanel(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!plugin.ax().available()) {
            player.sendMessage(Texts.colorize(plugin.lang()
                    .getString("panel.no-arcartx", "&c需要安装 ArcartX 客户端才能打开任务面板")));
            return;
        }

        // 不做代码层防抖：按键连发由 ArcartX key_group 的 interval(600ms) 负责拦截。
        // 这里再叠一层会导致「刚关掉面板马上按 J 打不开」。
        UUID uid = player.getUniqueId();

        if (plugin.spConfig().debug()) {
            plugin.getLogger().info("[面板打开] " + player.getName());
        }

        plugin.ax().open(player, PanelRenderer.UI_ID);

        // 立刻推一次（AX 会缓存，UI 就绪时处理），5 tick 后再推一次兜底
        push(player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                push(player);
            }
        }, 5L);
    }

    public void closePanel(Player player) {
        plugin.ax().close(player, PanelRenderer.UI_ID);
    }

    /** 切页（点顶栏图标）。 */
    public void onPage(Player player, String key) {
        clickSound(player);
        renderer.setPage(player, PanelRenderer.Page.of(key));
        // 切到非设置页时清掉选中，避免选中项不属于当前分类
        if (!"setting".equalsIgnoreCase(key)) {
            renderer.setSelected(player, null);
        }
        push(player);
    }

    /**
     * 「打开 HUD 设置」：关闭任务面板，打开独立的 HUD 设置面板。
     *
     * <p>HUD 设置是**独立 HUD 类型 UI**（starpact:hud_settings），
     * 不是任务面板的一部分 —— 所以关掉任务面板后它还在，
     * 玩家能同时看到 HUD 和设置项。
     */
    public void onHudEdit(Player player) {
        clickSound(player);
        closePanel(player);
        plugin.ax().open(player, PanelRenderer.HUD_SETTINGS_UI_ID);
        pushHudSettings(player);
    }

    /** 关闭 HUD 设置面板。 */
    public void onHudPanelClose(Player player) {
        clickSound(player);
        plugin.ax().close(player, PanelRenderer.HUD_SETTINGS_UI_ID);
    }

    /** 把 HUD 设置面板的数据推给客户端。 */
    public void pushHudSettings(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        com.asterovie.momo.starpact.server.storage.PlayerPrefs.Pref pref =
                plugin.prefs().of(player.getUniqueId());
        java.util.Map<String, Object> p = new java.util.HashMap<>();
        p.put("show", 1);
        p.put("v1", "\u00a7f" + Math.round(pref.scale * 100) + "%");
        p.put("v2", "\u00a7f" + (pref.hudW > 0 ? pref.hudW : plugin.spConfig().width()));
        plugin.ax().sendPacket(player, PanelRenderer.HUD_SETTINGS_UI_ID, "sync", p);
    }
    /** HUD 显隐切换。 */
    public void onToggleHud(Player player) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        plugin.prefs().setVisible(player.getUniqueId(), !pref.visible);
        plugin.hud().refresh(player, true);
        pushHudSettings(player);
    }

    /** HUD 缩放。delta 为 +-10 表示 10%。 */
    public void onScaleHud(Player player, int delta) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        plugin.prefs().setScale(player.getUniqueId(),
                plugin.spConfig().clampScale(pref.scale + delta / 100.0));
        plugin.hud().refresh(player, true);
        pushHudSettings(player);
    }

    /** HUD 宽度。delta 为 +-40。 */
    public void onHudWidth(Player player, int delta) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        int base = pref.hudW > 0 ? pref.hudW : plugin.spConfig().width();
        plugin.prefs().setHudWidth(player.getUniqueId(), base + delta);
        plugin.hud().refresh(player, true);
        pushHudSettings(player);
    }

    /** HUD 移动。 */
    public void onMoveHud(Player player, int dx, int dy) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        plugin.prefs().setPosition(player.getUniqueId(), pref.x + dx, pref.y + dy);
        plugin.hud().applyPosition(player);
    }

    /** HUD 位置重置。 */
    public void onResetHud(Player player) {
        clickSound(player);
        plugin.prefs().resetPosition(player.getUniqueId());
        plugin.hud().applyPosition(player);
        pushHudSettings(player);
    }

    /** 面板整体缩放。delta 为 +-10 表示 10%。 */
    public void onScaleUi(Player player, int delta) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        double v = pref.uiScale + delta / 100.0;
        plugin.prefs().setUiScale(player.getUniqueId(), Math.max(0.6, Math.min(1.6, v)));
        push(player);
    }

    /** 面板背景透明度。delta 为 +-10 表示 10%。 */
    public void onPanelAlpha(Player player, int delta) {
        clickSound(player);
        double cur = plugin.prefs().of(player.getUniqueId()).panelAlpha;
        plugin.prefs().setPanelAlpha(player.getUniqueId(), cur + delta / 100.0);
        push(player);
    }

    /** 左侧栏宽度比例。delta 为 +-5 表示 5%。 */
    public void onLeftRatio(Player player, int delta) {
        clickSound(player);
        double cur = plugin.prefs().of(player.getUniqueId()).leftRatio;
        plugin.prefs().setLeftRatio(player.getUniqueId(), cur + delta / 100.0);
        push(player);
    }

    /** 选中左栏某个任务。 */
    public void onSelectQuest(Player player, String questId) {
        clickSound(player);
        renderer.setSelected(player, questId);
        push(player);
    }

    /**
     * ready 包节流：每玩家最多每 2 秒受理一次。
     *
     * <p>面板 UI 的 open 触发器会在重绘时反复触发，不节流会死循环。
     */
    public boolean allowReady(UUID id) {
        long now = System.currentTimeMillis();
        Long last = readyAt.get(id);
        if (last != null && now - last < 2000L) {
            return false;
        }
        readyAt.put(id, now);
        return true;
    }

    /**
     * 播放原版按钮点击音效。
     *
     * <p>用服务端播放而不是客户端 Aria 的 Sound.self：后者要求 .ogg 资源路径。
     */
    public void clickSound(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        } catch (Throwable ignored) {
            // 音效失败不影响功能
        }
    }

    /** 把当前页的数据推给客户端。 */
    public void push(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, Object> packet = renderer.buildPacket(player);
        if (plugin.spConfig().debug()) {
            plugin.getLogger().info("[面板发包] " + player.getName()
                    + " page=" + renderer.pageOf(player)
                    + " selected=" + renderer.selectedOf(player));
        }
        plugin.ax().sendPacket(player, PanelRenderer.UI_ID, "sync", packet);
    }

    public void onQuit(Player player) {
        renderer.forget(player.getUniqueId());
        readyAt.remove(player.getUniqueId());
    }
}
