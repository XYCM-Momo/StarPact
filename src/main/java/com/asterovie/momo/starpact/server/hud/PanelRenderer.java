package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.model.QuestInfo;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import com.asterovie.momo.starpact.server.util.Texts;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务面板（全屏 Menu）的数据渲染。
 *
 * <p>布局：顶栏（三个分类图标 + 页名 + 设置/关闭）→ 左栏任务列表 →
 * 右栏任务详情 → 底部奖励区。
 *
 * <p>所有文本由服务端拼好下发，Aria 只做赋值 —— 避免在脚本里写循环和条件。
 */
public final class PanelRenderer {

    public static final String UI_ID = "starpact:quest_panel";
    /** HUD 设置面板（HUD 类型，独立于任务面板） */
    public static final String HUD_SETTINGS_UI_ID = "starpact:hud_settings";

    /** 页面分类 */
    public enum Page {
        SHIXU("shixu", "时序", "&b"),
        JIAN("jian", "间章", "&d"),
        CAN("can", "残流", "&e"),
        SETTING("setting", "设置", "&7");

        public final String key;
        public final String title;
        public final String color;

        Page(String key, String title, String color) {
            this.key = key;
            this.title = title;
            this.color = color;
        }

        public static Page of(String key) {
            if (key == null) {
                return SHIXU;
            }
            for (Page p : values()) {
                if (p.key.equalsIgnoreCase(key)) {
                    return p;
                }
            }
            return SHIXU;
        }
    }

    private final StarPactPlugin plugin;
    private final Map<UUID, Page> pages = new ConcurrentHashMap<>();
    /** 玩家在左栏选中的任务 ID；没有 = 未选中 */
    /** 玩家是否打开了 HUD 设置小面板 */
    private final java.util.Set<UUID> hudPanel = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public boolean hudPanelOpen(Player p) {
        return hudPanel.contains(p.getUniqueId());
    }

    public void setHudPanel(Player p, boolean open) {
        if (open) {
            hudPanel.add(p.getUniqueId());
        } else {
            hudPanel.remove(p.getUniqueId());
        }
    }

    private final Map<UUID, String> selected = new ConcurrentHashMap<>();

    public PanelRenderer(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    public Page pageOf(Player p) {
        return pages.getOrDefault(p.getUniqueId(), Page.SHIXU);
    }

    public void setPage(Player p, Page page) {
        pages.put(p.getUniqueId(), page);
    }

    public String selectedOf(Player p) {
        return selected.get(p.getUniqueId());
    }

    public void setSelected(Player p, String questId) {
        if (questId == null) {
            selected.remove(p.getUniqueId());
        } else {
            selected.put(p.getUniqueId(), questId);
        }
    }

    public void forget(UUID id) {
        pages.remove(id);
        selected.remove(id);
        hudPanel.remove(id);
    }

    /**
     * 构造推送包。
     */
    public Map<String, Object> buildPacket(Player player) {
        SpConfig cfg = plugin.spConfig();
        PlayerPrefs.Pref pref = plugin.prefs().of(player.getUniqueId());
        Page page = pageOf(player);
        List<QuestInfo> quests = plugin.chemdah().readQuests(player);

        Map<String, Object> p = new HashMap<>();

        // 面板整体缩放 = fit * ui_scale（两者都在客户端相乘）。
        //
        // fit 来自 config.yml 的 panel-fit，是把 1920x1080 的设计空间
        // 映射到实际 UI 坐标空间的系数。AX 的 UI 单位跟窗口像素不是 1:1
        // （实测差得挺多），所以这个值需要按机器调，做成配置项最省事。
        double uiScale = pref.uiScale <= 0 ? 1.0 : pref.uiScale;
        p.put("fit", cfg.panelFit());
        p.put("ui_scale", uiScale);
        p.put("hud_panel", hudPanelOpen(player) ? 1 : 0);
        p.put("hud_scale_text", "\u00a7f" + Math.round(pref.scale * 100) + "%");
        p.put("hud_w_text", "\u00a7f" + (pref.hudW > 0 ? pref.hudW : cfg.width()));
        p.put("set_scale_text", "\u00a7f" + Math.round(pref.uiScale * 100) + "%");
        p.put("set_content_text", "\u00a7f" + Math.round(pref.panelAlpha * 100) + "%");
        p.put("set_left_text", "\u00a7f" + Math.round(pref.leftRatio * 100) + "%");
        p.put("left_ratio", pref.leftRatio);
        p.put("panel_alpha", pref.panelAlpha);

        // 页名
        p.put("page_name", "\u00a7l" + page.title
                + "  \u00a78- " + (page == Page.SETTING ? "HUD 与面板设置" : page.title + "任务"));

        // 页签高亮
        p.put("tab_shixu_on", page == Page.SHIXU ? 1 : 0);
        p.put("tab_jian_on", page == Page.JIAN ? 1 : 0);
        p.put("tab_can_on", page == Page.CAN ? 1 : 0);

        if (page == Page.SETTING) {
            p.put("quest_lines", List.of("\u00a77设置项"));
            // 设置页不显示摘要：所有设置项都是可交互控件，发文本会残留在界面上
            p.put("detail_lines", List.of("\u00a7r"));
            p.put("reward_lines", List.of("\u00a78-"));
            p.put("has_sel", 0);
            // 设置页显示可交互的设置行
            p.put("show_setting", 1);
            p.put("val_scale", "\u00a7f" + Math.round(pref.scale * 100) + "%");
            p.put("val_w", "\u00a7f" + (pref.hudW > 0 ? pref.hudW : cfg.width()));
            p.put("val_uiz", "\u00a7f" + Math.round(uiScale * 100) + "%");
            return p;
        }
        p.put("show_setting", 0);

        // 左栏：按分类过滤
        List<QuestInfo> filtered = new ArrayList<>();
        for (QuestInfo q : quests) {
            if (page.key.equals(q.category())) {
                filtered.add(q);
            }
        }

        // 左栏文本
        List<String> listLines = new ArrayList<>();
        if (filtered.isEmpty()) {
            listLines.add("\u00a78暂无" + page.title + "任务");
        } else {
            for (QuestInfo q : filtered) {
                String sel = q.id().equals(selectedOf(player))
                        ? "\u00a7b\u25b8 " : "\u00a78\u25b8 ";
                listLines.add(sel + "\u00a7f" + Texts.ellipsis(q.name(), 20)
                        + "  \u00a77" + q.progressText());
            }
        }
        p.put("quest_lines", listLines);

        // 右栏：选中任务详情
        QuestInfo cur = null;
        for (QuestInfo q : filtered) {
            if (q.id().equals(selectedOf(player))) {
                cur = q;
                break;
            }
        }
        if (cur == null) {
            p.put("has_sel", 0);
            p.put("detail_lines", List.of("\u00a78从左侧选择一个任务"));
            p.put("reward_lines", List.of("\u00a78-"));
        } else {
            p.put("has_sel", 1);
            p.put("detail_lines", detailLines(cur));
            p.put("reward_lines", rewardLines(cur));
        }
        return p;
    }

    /** 详情区：名称 + 描述 + 各目标进度 */
    private List<String> detailLines(QuestInfo q) {
        List<String> out = new ArrayList<>();
        out.add("\u00a7f\u00a7l" + Texts.ellipsis(q.name(), 40));
        out.add("");
        for (String d : q.description()) {
            out.add("\u00a77" + d);
        }
        if (!q.description().isEmpty()) {
            out.add("");
        }
        out.add("\u00a78目标");
        for (QuestInfo.Objective o : q.objectives()) {
            String mark = o.completed() ? "\u00a7a\u2714" : "\u00a7b\u25aa";
            out.add(mark + " \u00a7f" + Texts.ellipsis(o.name(), 28));
            out.add("   " + bar(o.percent(), 14, o.completed()) + " \u00a77" + o.numbers());
        }
        return out;
    }

    /** 奖励区 */
    private List<String> rewardLines(QuestInfo q) {
        List<String> out = new ArrayList<>();
        if (q.rewards().isEmpty()) {
            out.add("\u00a78该任务没有配置奖励预览");
            out.add("\u00a78（实际奖励由任务脚本发放）");
            return out;
        }
        for (QuestInfo.Reward r : q.rewards()) {
            String amt = r.amount() == null || r.amount().isEmpty()
                    ? "" : " \u00a7e" + r.amount();
            out.add("\u00a7f\u25aa " + r.name() + amt);
        }
        return out;
    }

    /** 设置页内容 */
    private List<String> settingLines(SpConfig cfg, PlayerPrefs.Pref pref) {
        List<String> out = new ArrayList<>();
        out.add("\u00a7fHUD 状态：  " + (pref.visible ? "\u00a7a已开启" : "\u00a78已关闭"));
        out.add("\u00a7fHUD 缩放：  \u00a7f" + Math.round(pref.scale * 100) + "%");
        out.add("\u00a7fHUD 宽度：  \u00a7f" + (pref.hudW > 0 ? pref.hudW : cfg.width()));
        out.add("\u00a7f面板缩放：  \u00a7f" + Math.round(pref.uiScale * 100) + "%");
        out.add("");
        out.add("\u00a78用 /sp 命令调整：toggle / move / reset / scale");
        return out;
    }

    /** 进度条 */
    private String bar(double percent, int len, boolean completed) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, percent)) * len);
        if (completed) {
            filled = len;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(completed ? "\u00a7a" : "\u00a7b");
        for (int i = 0; i < len; i++) {
            sb.append(i < filled ? '|' : '.');
        }
        return sb.toString();
    }
}
