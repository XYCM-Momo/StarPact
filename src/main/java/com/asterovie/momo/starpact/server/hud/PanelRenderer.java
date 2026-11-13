package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.model.QuestInfo;
import com.asterovie.momo.starpact.server.quest.ItemResolver;
import com.asterovie.momo.starpact.server.quest.QuestConfig;
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
    /** 玩家当前面板里每行对应的任务 ID（点行号时用） */
    private final Map<UUID, java.util.List<String>> rowIds = new ConcurrentHashMap<>();
    /** 每个玩家详情区的滚动偏移（行数） */
    private final Map<UUID, Integer> detailScroll = new ConcurrentHashMap<>();

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

    /** 第 row 行对应的任务 ID；越界返回 null。 */
    public String questIdAt(Player p, int row) {
        var ids = rowIds.get(p.getUniqueId());
        if (ids == null || row < 0 || row >= ids.size()) {
            return null;
        }
        return ids.get(row);
    }

    /** 详情区滚动偏移（行）。 */
    public int detailScrollOf(Player p) {
        return detailScroll.getOrDefault(p.getUniqueId(), 0);
    }

    /** 滚轮：正数向下、负数向上，偏移量按行计。 */
    public void scrollDetail(Player p, int delta) {
        int cur = detailScrollOf(p);
        int next = Math.max(0, cur + delta);
        detailScroll.put(p.getUniqueId(), next);
    }

    /** 内容变化后重置滚动（比如切换任务）。 */
    public void resetDetailScroll(Player p) {
        detailScroll.remove(p.getUniqueId());
    }

    public void forget(UUID id) {
        pages.remove(id);
        selected.remove(id);
        hudPanel.remove(id);
        rowIds.remove(id);
        detailScroll.remove(id);
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
        // 追踪状态：按钮文字在「追踪任务 / 取消追踪」之间切换
        String trackedId = pref.trackedId;
        String selId = selectedOf(player);
        boolean isTracked = trackedId != null && trackedId.equals(selId);
        p.put("tracked", isTracked ? 1 : 0);
        p.put("track_text", isTracked
                ? "\u00a7c\u2715 \u00a7f\u53d6\u6d88\u8ffd\u8e2a"
                : "\u00a7b\u25c8 \u00a7f\u8ffd\u8e2a\u4efb\u52a1");

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
        // 分类优先级：quests/ 里配的 category > Chemdah 的 meta.type 推断
        List<QuestInfo> filtered = new ArrayList<>();
        for (QuestInfo q : quests) {
            if (page.key.equals(categoryOf(q))) {
                filtered.add(q);
            }
        }

        // 左栏：结构化条目（RepeatGrid 用），每条是一个字典：
        //   id / text（整行文本）/ name / progress / selected
        List<Map<String, Object>> items = new ArrayList<>();
        if (filtered.isEmpty()) {
            // 空列表时给一条占位，避免 RepeatGrid 什么都不画
            Map<String, Object> empty = new HashMap<>();
            empty.put("id", "__empty__");
            empty.put("text", "\u00a78暂无" + page.title + "任务");
            items.add(empty);
        } else {
            for (QuestInfo q : filtered) {
                boolean sel = q.id().equals(selectedOf(player));
                Map<String, Object> it = new HashMap<>();
                it.put("id", q.id());
                it.put("name", Texts.strip(Texts.ellipsis(q.name(), 20)));
                it.put("progress", q.progressText());
                it.put("selected", sel ? 1 : 0);
                it.put("text", (sel ? "\u00a7b\u25b8 " : "\u00a78\u25b8 ")
                        + "\u00a7f" + Texts.ellipsis(q.name(), 20)
                        + "  \u00a77" + q.progressText());
                items.add(it);
            }
        }
        p.put("quest_items", items);

        java.util.List<String> ids = new java.util.ArrayList<>();
        for (QuestInfo q : filtered) {
            ids.add(q.id());
        }
        rowIds.put(player.getUniqueId(), ids);

        // 左栏：固定 8 行控件，逐行下发文本 / 可见性 / 对应任务 ID。
        // 不用 RepeatGrid —— 实测它的 items 在 sync 里赋值不生效。
        int rows = 8;
        for (int i = 0; i < rows; i++) {
            if (i < filtered.size()) {
                QuestInfo q = filtered.get(i);
                boolean sel = q.id().equals(selectedOf(player));
                p.put("qr_show" + i, 1);
                p.put("qr_text" + i, (sel ? "\u00a7b\u25b8 " : "\u00a78\u25b8 ")
                        + "\u00a7f" + Texts.ellipsis(q.name(), 18)
                        + "  \u00a77" + q.progressText());
                p.put("qr_id" + i, q.id());
            } else if (i == 0 && filtered.isEmpty()) {
                p.put("qr_show0", 1);
                p.put("qr_text0", "\u00a78暂无" + page.title + "任务");
                p.put("qr_id0", "__empty__");
            } else {
                p.put("qr_show" + i, 0);
                p.put("qr_text" + i, "\u00a7r");
                p.put("qr_id" + i, "");
            }
        }

        // 兼容旧版：仍然发一份纯文本（HUD / 降级时用得上）
        List<String> listLines = new ArrayList<>();
        for (Map<String, Object> it : items) {
            listLines.add(String.valueOf(it.get("text")));
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
            p.put("detail_rows", 1);
            p.put("scroll_hint", "");
        } else {
            // 详情区只放「名称 + 描述 + 目标」。
            // 奖励由下面独立的 reward 控件渲染（底部固定区），
            // 塞进这里会导致同一份奖励显示两遍、并把 total 撑大。
            List<String> all = new ArrayList<>(detailLines(cur));

            // 一页能放几行：按「详情区高度 / 实测单行高」算。
            //
            // UI 里 detail 的 height = SH * 0.62。
            // 单行高实测：fontSize 0.0700 时截图行距约 40px / 773px ≈ 0.0518 SH，
            // 容量 = 0.62 / 0.0518 ≈ 12 行。
            //
            // 这个值只能靠实测校准 —— AX 的行高不等于 fontSize，
            // 与 lineSpace 的叠加方式也不是简单相加。改 fontSize 后必须重量一次。
            final int perPage = 12;
            int total = all.size();

            // 内容不足一页：全部显示，不启用滚动
            if (total <= perPage) {
                p.put("has_sel", 1);
                p.put("detail_lines", all);
            p.put("reward_lines", rewardLines(cur));
            p.put("reward_rows", rewardSlotCount(cur));
            pushRewardAmounts(p, cur);
                p.put("detail_rows", total);
                p.put("scroll_hint", "");
                // 归零，避免下次内容变多时还停在旧位置
                detailScroll.remove(player.getUniqueId());
                return p;
            }

            int maxStart = Math.max(0, total - perPage);

            // 夹一下滚动偏移，避免滚过头
            int start = Math.min(Math.max(0, detailScrollOf(player)), maxStart);
            detailScroll.put(player.getUniqueId(), start);
            int end = Math.min(total, start + perPage);

            p.put("has_sel", 1);
            p.put("detail_lines", new ArrayList<>(all.subList(start, end)));
            p.put("reward_lines", rewardLines(cur));
            p.put("reward_rows", rewardSlotCount(cur));
            pushRewardAmounts(p, cur);
            p.put("detail_rows", total);

            // 底部提示：还有多少没显示
            {
                String hint = "\u00a78" + (start + 1) + "-" + end + " / " + total;
                if (end < total) {
                    hint += "  \u00a77\u25bc";
                }
                if (start > 0) {
                    hint = "\u00a77\u25b2  " + hint;
                }
                p.put("scroll_hint", hint);
            }
        }
        return p;
    }

    /**
     * 任务属于哪个分类。
     *
     * <p>优先级：<b>quests/ 配置里写的 category</b> &gt; Chemdah 按 meta.type 推断的结果。
     *
     * <p>之所以要做这层覆盖：Chemdah 的 {@code meta.type} 是服主自己随手起的名字
     * （比如 {@code test} / {@code L1}），跟「时序 / 间章 / 残流」没有必然关系。
     * 让服主在 quests/ 里显式指定最可靠。
     */
    private String categoryOf(QuestInfo q) {
        var qc = plugin.quests() == null ? null : plugin.quests().get(q.id());
        if (qc != null && qc.category() != null) {
            return qc.category();
        }
        return q.category();
    }

    /** 详情区：名称 + 描述 + 各目标进度 */
    private List<String> detailLines(QuestInfo q) {
        List<String> out = new ArrayList<>();

        // quests/ 里可能覆盖了名称/描述
        var qc = plugin.quests() == null ? null : plugin.quests().get(q.id());
        String name = (qc != null && qc.name() != null) ? qc.name() : q.name();
        List<String> desc = (qc != null && !qc.description().isEmpty())
                ? qc.description() : q.description();

        out.add("\u00a7f\u00a7l" + Texts.ellipsis(name, 40));

        // 描述：裁掉首尾空白、跳过空行、连续空行合并。
        // Chemdah 的 addon.ui.description 经常带空行，全吐出来右栏会非常空。
        List<String> cleanDesc = new ArrayList<>();
        boolean lastBlank = false;
        for (String d : desc) {
            String line = d == null ? "" : d.strip();
            if (line.isEmpty()) {
                if (!lastBlank && !cleanDesc.isEmpty()) {
                    cleanDesc.add("");
                    lastBlank = true;
                }
                continue;
            }
            // 配置里的描述保留自己的颜色码；Chemdah 的默认加灰
            cleanDesc.add(line.startsWith("\u00a7") || line.startsWith("&")
                    ? Texts.colorize(line) : "\u00a77" + line);
            lastBlank = false;
        }
        while (!cleanDesc.isEmpty() && cleanDesc.get(cleanDesc.size() - 1).isEmpty()) {
            cleanDesc.remove(cleanDesc.size() - 1);
        }

        if (!cleanDesc.isEmpty()) {
            // 名称与描述之间用一条淡横线分隔，而不是空行
            out.add("\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
            out.addAll(cleanDesc);
        }

            out.add("\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        out.add("\u00a78\u76ee\u6807");
        for (QuestInfo.Objective o : q.objectives()) {
            String mark = o.completed() ? "\u00a7a\u2714" : "\u00a7b\u25aa";
            // 目标名和进度条合成一行，省掉一半垂直空间
            out.add(mark + " \u00a7f" + Texts.ellipsis(o.name(), 24)
                    + "   " + bar(o.percent(), 10, o.completed())
                    + " \u00a77" + o.numbers());
        }
        return out;
    }


    /** 奖励数量文本（Slot 右下角的 ×N），只对真能解析成物品的奖励编号。 */
    private void pushRewardAmounts(Map<String, Object> p, QuestInfo q) {
        ItemResolver items = plugin.items();
        int slot = 0;
        if (items != null && plugin.quests() != null) {
            var qc = plugin.quests().get(q.id());
            if (qc != null) {
                for (QuestConfig.Reward r : qc.rewards()) {
                    if (slot >= REWARD_SLOT_COUNT) {
                        break;
                    }
                    if (items.resolve(r) == null) {
                        continue;   // 解析不出来就不占格子
                    }
                    int amt = r.amount();
                    p.put("rw_amt" + slot, amt > 1 ? "\u00a7e\u00d7" + amt : "");
                    slot++;
                }
            }
        }
        // 其余格子清空数量文本
        for (int i = slot; i < REWARD_SLOT_COUNT; i++) {
            p.put("rw_amt" + i, "");
        }
    }

    /** 奖励格子上限（和 UI 里的 rw_slotN 数量一致）。 */
    public static final int REWARD_SLOT_COUNT = 9;

    /** 实际能显示的奖励数量（解析成功的个数）。 */
    private int rewardSlotCount(QuestInfo q) {
        ItemResolver items = plugin.items();
        if (items == null || plugin.quests() == null || q == null) {
            return 0;
        }
        var qc = plugin.quests().get(q.id());
        if (qc == null) {
            return 0;
        }
        int n = 0;
        for (QuestConfig.Reward r : qc.rewards()) {
            if (n >= REWARD_SLOT_COUNT) {
                break;
            }
            if (items.resolve(r) != null) {
                n++;
            }
        }
        return n;
    }

    /** 当前选中的任务；没选返回 null。 */
    public QuestInfo selectedQuest(Player p) {
        String id = selectedOf(p);
        if (id == null) {
            return null;
        }
        for (QuestInfo q : plugin.chemdah().readQuests(p)) {
            if (q.id().equals(id)) {
                return q;
            }
        }
        return null;
    }

    /**
     * 把奖励物品推到右下的 Slot 格子上。
     *
     * <p>物品不走 packet（只支持基本类型），而是用 AX 的
     * {@code sendSlotItemStack} 单独发。没有奖励时要显式清空，
     * 否则切换到无奖励任务会残留上一个任务的图标。
     */
    public void pushRewardItems(Player player, QuestInfo q) {
        if (!plugin.ax().available()) {
            return;
        }
        java.util.List<QuestConfig.Reward> rewards = new ArrayList<>();
        if (q != null) {
            var qc = plugin.quests() == null ? null : plugin.quests().get(q.id());
            if (qc != null) {
                rewards.addAll(qc.rewards());
            }
        }
        ItemResolver items = plugin.items();
        boolean debug = plugin.spConfig().debug();
        for (int i = 0; i < 3; i++) {
            String slotId = "StarPactReward" + i;
            if (i < rewards.size() && items != null) {
                var stack = items.resolve(rewards.get(i));
                if (debug) {
                    var rw = rewards.get(i);
                }
                if (stack != null) {
                    boolean ok = plugin.ax().sendSlotItem(player, slotId, stack);
                    continue;
                }
            }
            plugin.ax().clearSlotItem(player, slotId);
        }
        // 让客户端用最新缓存刷新一遍槽位渲染
        plugin.ax().syncSlots(player);
        if (debug) {
            for (int i = 0; i < 3; i++) {
            }
        }
    }

    /** 奖励区（优先用 quests/ 配置，回退到 Chemdah 的 addon.reward） */
    private List<String> rewardLines(QuestInfo q) {
        List<String> out = new ArrayList<>();

        // 1) quests/ 里显式配的奖励（带物品库解析）
        var qc = plugin.quests() == null ? null : plugin.quests().get(q.id());
        if (qc != null && !qc.rewards().isEmpty()) {
            ItemResolver items = plugin.items();
            for (QuestConfig.Reward r : qc.rewards()) {
                String name = items == null ? null : items.displayName(r);
                if (name == null || name.isBlank()) {
                    // 解析不出来就显示原始 ID，并标注来源
                    name = r.provider() + ":" + r.key();
                }
                String tag = switch (r.provider()) {
                    case "MM" -> "\u00a7d[M]";
                    case "NI" -> "\u00a7b[N]";
                    default -> "";
                };
                String amt = r.amount() > 1 ? " \u00a7e\u00d7" + r.amount() : "";
                out.add("\u00a7f\u25aa " + tag + "\u00a7f " + name + amt);
            }
            return out;
        }

        // 2) 回退到 Chemdah 的 addon.reward
        if (q.rewards().isEmpty()) {
            out.add("\u00a78该任务没有配置奖励预览");
            out.add("\u00a78（在 quests/ 里加 rewards 即可显示）");
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
