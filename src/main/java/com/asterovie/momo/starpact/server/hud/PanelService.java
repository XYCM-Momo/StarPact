package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.hud.PanelRenderer;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import com.asterovie.momo.starpact.server.util.Texts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 任务面板的开关与交互处理。
 */
public final class PanelService {
    public static final String KEY_ID = "星契任务面板";
    private static final long OPEN_DEBOUNCE_MS = 400L;
    private final StarPactPlugin plugin;
    private final PanelRenderer renderer;
    private final Map<UUID, Long> lastOpenAt = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<UUID, Long>();

    public PanelService(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
        this.renderer = new PanelRenderer(starPactPlugin);
    }

    public PanelRenderer renderer() {
        return this.renderer;
    }

    public void openPanel(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!this.plugin.ax().available()) {
            player.sendMessage(Texts.colorize(this.plugin.lang().getString("panel.no-arcartx", "&c需要安装 ArcartX 客户端才能打开任务面板")));
            return;
        }
        UUID uUID = player.getUniqueId();
        long l = System.currentTimeMillis();
        Long l2 = this.lastOpenAt.get(uUID);
        if (l2 != null && l - l2 < 400L) {
            return;
        }
        this.lastOpenAt.put(uUID, l);
        if (this.plugin.spConfig().debug()) {
            this.plugin.getLogger().info("[面板打开] " + player.getName());
        }
        this.plugin.ax().open(player, "menu/星契面板");
        this.push(player);
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            if (player.isOnline()) {
                this.push(player);
            }
        }, 5L);
    }

    public void closePanel(Player player) {
        this.plugin.ax().close(player, "menu/星契面板");
    }

    public void onPage(Player player, String string) {
        this.clickSound(player);
        this.renderer.setPage(player, PanelRenderer.Page.of(string));
        if (!"setting".equalsIgnoreCase(string)) {
            this.renderer.setSelected(player, null);
        }
        this.push(player);
    }

    public void onHudEdit(Player player) {
        this.clickSound(player);
        this.renderer.setHudPanel(player, true);
        this.closePanel(player);
        this.plugin.ax().open(player, "starpact:hud_settings");
        this.pushHudSettings(player);
    }

    public void onHudPanelClose(Player player) {
        this.clickSound(player);
        this.plugin.ax().close(player, "starpact:hud_settings");
        this.renderer.setHudPanel(player, false);
    }

    public void pushHudSettings(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("show", 1);
        hashMap.put("v1", "§f" + Math.round(pref.scale * 100.0) + "%");
        hashMap.put("v2", "§f" + (pref.hudW > 0 ? pref.hudW : this.plugin.spConfig().width()));
        this.plugin.ax().sendPacket(player, "starpact:hud_settings", "sync", hashMap);
        UUID uUID = player.getUniqueId();
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            Player player = this.plugin.getServer().getPlayer(uUID);
            if (player != null && player.isOnline()) {
                PlayerPrefs.Pref pref = this.plugin.prefs().of(uUID);
                HashMap<String, Object> hashMap = new HashMap<String, Object>();
                hashMap.put("show", 1);
                hashMap.put("v1", "§f" + Math.round(pref.scale * 100.0) + "%");
                hashMap.put("v2", "§f" + (pref.hudW > 0 ? pref.hudW : this.plugin.spConfig().width()));
                this.plugin.ax().sendPacket(player, "starpact:hud_settings", "sync", hashMap);
            }
        }, 5L);
    }

    public void onToggleHud(Player player) {
        this.clickSound(player);
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        this.plugin.prefs().setVisible(player.getUniqueId(), !pref.visible);
        this.plugin.hud().refresh(player, true);
        this.pushHudSettings(player);
    }

    public void onScaleHud(Player player, int n) {
        this.clickSound(player);
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        this.plugin.prefs().setScale(player.getUniqueId(), this.plugin.spConfig().clampScale(pref.scale + (double)n / 100.0));
        this.plugin.hud().refresh(player, true);
        this.pushHudSettings(player);
    }

    public void onHudWidth(Player player, int n) {
        this.clickSound(player);
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        int n2 = pref.hudW > 0 ? pref.hudW : this.plugin.spConfig().width();
        this.plugin.prefs().setHudWidth(player.getUniqueId(), n2 + n);
        this.plugin.hud().refresh(player, true);
        this.pushHudSettings(player);
    }

    public void onMoveHud(Player player, int n, int n2) {
        this.clickSound(player);
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        this.plugin.prefs().setPosition(player.getUniqueId(), pref.x + n, pref.y + n2);
        this.plugin.hud().applyPosition(player);
    }

    public void onResetHud(Player player) {
        this.clickSound(player);
        this.plugin.prefs().resetPosition(player.getUniqueId());
        this.plugin.hud().applyPosition(player);
        this.pushHudSettings(player);
    }

    public void onContentScale(Player player, int n) {
        this.clickSound(player);
        double d = this.plugin.prefs().of((UUID)player.getUniqueId()).contentScale;
        this.plugin.prefs().setContentScale(player.getUniqueId(), d + (double)n / 100.0);
        this.push(player);
    }

    public void onLeftRatio(Player player, int n) {
        this.clickSound(player);
        double d = this.plugin.prefs().of((UUID)player.getUniqueId()).leftRatio;
        this.plugin.prefs().setLeftRatio(player.getUniqueId(), d + (double)n / 100.0);
        this.push(player);
    }

    public void onUiScale(Player player, int n) {
        this.clickSound(player);
        double d = this.plugin.prefs().of((UUID)player.getUniqueId()).uiScale;
        this.plugin.prefs().setUiScale(player.getUniqueId(), d + (double)n / 100.0);
        this.push(player);
    }

    public void onSelectQuest(Player player, String string) {
        this.clickSound(player);
        this.renderer.setSelected(player, string);
        this.push(player);
    }

    public boolean allowReady(UUID uUID) {
        long l = System.currentTimeMillis();
        Long l2 = this.readyAt.get(uUID);
        if (l2 != null && l - l2 < 2000L) {
            return false;
        }
        this.readyAt.put(uUID, l);
        return true;
    }

    public void clickSound(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void push(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, Object> map = this.renderer.buildPacket(player);
        if (this.plugin.spConfig().debug()) {
            this.plugin.getLogger().info("[面板发包] " + player.getName() + " page=" + String.valueOf((Object)this.renderer.pageOf(player)) + " selected=" + this.renderer.selectedOf(player));
        }
        this.plugin.ax().sendPacket(player, "menu/星契面板", "sync", map);
    }

    public void onQuit(Player player) {
        this.renderer.forget(player.getUniqueId());
        this.lastOpenAt.remove(player.getUniqueId());
        this.readyAt.remove(player.getUniqueId());
    }
}

