package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD 渲染层：把 {@link TrackEntry} 列表整理成 ArcartX UI 需要的包，并推送。
 *
 * <p>面板是固定的 3 条槽位（s0/s1/s2），不动态增删控件 ——
 * 这样切任务时不会闪，也避开了 AX 动态控件创建的不确定性。
 * 数据包里多出来的槽位用空字符串 + count 控制可见性。
 */
public final class HudRenderer {

    public static final String UI_ID = "starpact:quest_tracker";

    private final StarPactPlugin plugin;
    /** 用于节流：记录每个玩家上次推送的时间戳与内容指纹 */
    private final Map<UUID, Long> lastPushAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastFingerprint = new ConcurrentHashMap<>();

    public HudRenderer(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 推一次。
     *
     * @param force 忽略节流（玩家刚进服 / 手动刷新时用）
     * @return 是否真的发出去了
     */
    public boolean push(Player player, List<TrackEntry> tracks, boolean force) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        SpConfig cfg = plugin.spConfig();
        PlayerPrefs.Pref pref = plugin.prefs().of(player.getUniqueId());

        int count = Math.min(tracks.size(), cfg.maxTracks());

        // 内容指纹：位置/开关/每条内容都算进去
        boolean willShow = pref.visible && !(cfg.hideWhenEmpty() && count == 0);
        StringBuilder fp = new StringBuilder();
        fp.append(pref.x).append(',').append(pref.y).append(',').append(willShow).append(',')
                .append(count);
        for (int i = 0; i < count; i++) {
            TrackEntry e = tracks.get(i);
            fp.append('|').append(e.taskId()).append(':')
                    .append(e.bar(cfg.barLength(), cfg.barFill(), cfg.barEmpty()))
                    .append(':').append(e.numbers()).append(':').append(e.title());
        }
        String fingerprint = fp.toString();

        long now = System.currentTimeMillis();
        Long last = lastPushAt.get(player.getUniqueId());
        if (!force) {
            if (fingerprint.equals(lastFingerprint.get(player.getUniqueId()))) {
                return false; // 内容没变，不发
            }
            if (last != null && now - last < cfg.throttleMs()) {
                return false; // 节流
            }
        }
        lastPushAt.put(player.getUniqueId(), now);
        lastFingerprint.put(player.getUniqueId(), fingerprint);

        double zoom = pref.scale <= 0 ? cfg.defaultScale() : cfg.clampScale(pref.scale);

        Map<String, Object> packet = new HashMap<>();
        // 位置：直接下发绝对坐标（UI 像素）。
        // HUD 的 UI 不使用任何容器嵌套，所以 val.panel.x/y 能真正生效。
        packet.put("x", pref.custom ? pref.x : cfg.offsetX());
        packet.put("y", pref.custom ? pref.y : cfg.offsetY());
        // 整块面板是否显示：玩家关掉了 HUD、或没有任务且配置要求隐藏时，都不显示
        boolean show = pref.visible && !(cfg.hideWhenEmpty() && count == 0);
        packet.put("show", show ? 1 : 0);
        // 缩放：所有尺寸都乘 zoom，由客户端直接赋值（不在 Aria 里算，便于维护）
        packet.put("zoom", zoom);
        // 宽度：玩家自定义优先，否则用配置默认
        int baseW = pref.hudW > 0 ? pref.hudW : cfg.width();
        packet.put("w", Math.round(baseW * zoom));
        // 高度：玩家自定义优先（0 = 按任务数自动算）
        int baseH = pref.hudH > 0 ? pref.hudH : panelHeight(cfg, count);
        packet.put("h", Math.round(baseH * zoom));
        packet.put("fsTitle", Math.round(20 * zoom));
        packet.put("fsBody", Math.round(18 * zoom));
        packet.put("fsHint", Math.round(15 * zoom));
        packet.put("fsLine", Math.round(21 * zoom));
        packet.put("padX", Math.round(14 * zoom));
        packet.put("rowH", Math.round(54 * zoom));
        packet.put("topPad", Math.round(12 * zoom));
        packet.put("rowGap", Math.round(30 * zoom));

        // 没有任何任务时，第一行显示「暂无任务」提示，
        // 而不是留一个空面板（hide-when-empty 已默认关闭）。
        boolean empty = count == 0;
        if (empty && show) {
            packet.put("t0", "\u00a78暂无追踪中的任务");
            packet.put("b0", "\u00a78按 J 打开任务面板");
            packet.put("v0", 1);
        }

        for (int i = 0; i < cfg.maxTracks(); i++) {
            if (empty && i == 0) {
                continue;
            }
            boolean visible = show && i < count;
            // ⚠️ 必须是非空字符串。ArcartX 把空串序列化成 "[ , ]"，
            // Aria 解析器会在逗号处报 "Unexpected token: COMMA"，
            // 并把整个 attribute 块丢弃 —— HUD 会完全不显示。
            // 用 "§r"（重置格式）作为「空行」的安全占位。
            String t = "\u00a7r";
            String b = "\u00a7r";
            if (i < count) {
                TrackEntry e = tracks.get(i);
                t = line1(cfg, e);
                b = line2(cfg, e);
            }
            packet.put("t" + i, t);
            packet.put("b" + i, b);
            packet.put("v" + i, visible ? 1 : 0);
        }

        if (cfg.debug()) {
            plugin.getLogger().info("[HUD] " + player.getName() + " -> " + packet);
        }

        if (plugin.ax().available()) {
            plugin.ax().sendPacket(player, UI_ID, "sync", packet);
            return true;
        }
        return false;
    }

    /**
     * 第一行：目标名（左）+ 进度数字（右，用空格推到右边缘）。
     *
     * <p>宽度按设计基准换算：fontSize 30 时 1 个 ASCII 字符约占 15 设计单位，
     * 面板 320 宽、左右各留 16 → 可用 288 ≈ 19 个字符位。
     */
    private String line1(SpConfig cfg, TrackEntry e) {
        // 中文占 2 位，标题最多 ~20 位（约 10 个汉字）
        String title = com.asterovie.momo.starpact.server.util.Texts.ellipsis(e.title(), 20);
        if (e.completed()) {
            title = "\u00a7a\u2714 " + title;
        } else {
            title = "\u00a7f" + title;
        }
        if (!cfg.showNumbers()) {
            return title;
        }
        String nums = "\u00a77" + e.numbers();
        int used = com.asterovie.momo.starpact.server.util.Texts.visualWidth(title)
                + com.asterovie.momo.starpact.server.util.Texts.visualWidth(nums);
        int pad = Math.max(1, lineWidthChars(cfg) - used);
        return title + " ".repeat(pad) + nums;
    }

    /**
     * 一行能放多少个「字符位」（中文算 2）。
     *
     * <p>fontSize 18 下，1 个 ASCII 字符约占 9 个 UI 单位。
     * 面板宽度来自 config.yml 的 hud.width（默认 380）。
     */
    private int lineWidthChars(SpConfig cfg) {
        int usable = Math.max(100, cfg.width() - 32);
        return Math.max(10, usable / 9);
    }

    /** 第二行：细进度条。 */
    private String line2(SpConfig cfg, TrackEntry e) {
        String bar = e.bar(cfg.barLength(), cfg.barFill(), cfg.barEmpty());
        String color = e.completed() ? "\u00a7a" : (e.percent() >= 1 ? "\u00a7a" : "\u00a7b");
        return color + bar;
    }

    /**
     * 根据条数算面板高度，推给客户端调底板尺寸。
     *
     * <p>布局基准（见 ui/quest-tracker.yml）：
     * 标题 y=182、第一条 y=214、每条占 54（两行 × lineSpace 21 + 12 间隙），
     * 面板顶部 y=170。所以高度 = 52 + 条数 × 54。
     */
    private int panelHeight(SpConfig cfg, int count) {
        int n = Math.max(1, Math.min(count, cfg.maxTracks()));
        return 52 + n * 54;
    }

    public void forget(UUID id) {
        lastPushAt.remove(id);
        lastFingerprint.remove(id);
    }
}
