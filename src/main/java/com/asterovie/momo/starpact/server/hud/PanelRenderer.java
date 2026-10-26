package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.model.QuestInfo;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import com.asterovie.momo.starpact.server.util.Texts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * 任务面板（全屏 Menu）的数据渲染。
 */
public final class PanelRenderer {
    public static final String UI_ID = "menu/星契面板";
    public static final String HUD_SETTINGS_UI_ID = "starpact:hud_settings";
    private final StarPactPlugin plugin;
    private final Map<UUID, Page> pages = new ConcurrentHashMap<UUID, Page>();
    private final Set<UUID> hudPanel = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> selected = new ConcurrentHashMap<UUID, String>();

    public boolean hudPanelOpen(Player player) {
        return this.hudPanel.contains(player.getUniqueId());
    }

    public void setHudPanel(Player player, boolean bl) {
        if (bl) {
            this.hudPanel.add(player.getUniqueId());
        } else {
            this.hudPanel.remove(player.getUniqueId());
        }
    }

    public PanelRenderer(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
    }

    public Page pageOf(Player player) {
        return this.pages.getOrDefault(player.getUniqueId(), Page.SHIXU);
    }

    public void setPage(Player player, Page page) {
        this.pages.put(player.getUniqueId(), page);
    }

    public String selectedOf(Player player) {
        return this.selected.get(player.getUniqueId());
    }

    public void setSelected(Player player, String string) {
        if (string == null) {
            this.selected.remove(player.getUniqueId());
        } else {
            this.selected.put(player.getUniqueId(), string);
        }
    }

    public void forget(UUID uUID) {
        this.pages.remove(uUID);
        this.selected.remove(uUID);
        this.hudPanel.remove(uUID);
    }

    public Map<String, Object> buildPacket(Player player) {
        QuestInfo questInfo = null;
        SpConfig spConfig = this.plugin.spConfig();
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        Page page = this.pageOf(player);
        List<QuestInfo> list = this.plugin.chemdah().readQuests(player);
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        double d = pref.uiScale <= 0.0 ? 1.0 : pref.uiScale;
        hashMap.put("fit", spConfig.panelFit());
        hashMap.put("ui_scale", d);
        hashMap.put("hud_panel", this.hudPanelOpen(player) ? 1 : 0);
        hashMap.put("hud_scale_text", "§f" + Math.round(pref.scale * 100.0) + "%");
        hashMap.put("hud_w_text", "§f" + (pref.hudW > 0 ? pref.hudW : spConfig.width()));
        hashMap.put("set_scale_text", "§f" + Math.round(pref.uiScale * 100.0) + "%");
        hashMap.put("set_content_text", "§f" + Math.round(pref.contentScale * 100.0) + "%");
        hashMap.put("set_left_text", "§f" + Math.round(pref.leftRatio * 100.0) + "%");
        hashMap.put("left_ratio", pref.leftRatio);
        hashMap.put("content_scale", pref.contentScale);
        hashMap.put("page_name", "§l" + page.title + "  §8- " + (String)(page == Page.SETTING ? "HUD 与面板设置" : page.title + "任务"));
        hashMap.put("tab_shixu_on", page == Page.SHIXU ? 1 : 0);
        hashMap.put("tab_jian_on", page == Page.JIAN ? 1 : 0);
        hashMap.put("tab_can_on", page == Page.CAN ? 1 : 0);
        if (page == Page.SETTING) {
            hashMap.put("quest_lines", List.of("§7设置项"));
            hashMap.put("detail_lines", this.settingLines(spConfig, pref));
            hashMap.put("reward_lines", List.of("§8-"));
            hashMap.put("has_sel", 0);
            hashMap.put("show_setting", 1);
            hashMap.put("val_scale", "§f" + Math.round(pref.scale * 100.0) + "%");
            hashMap.put("val_w", "§f" + (pref.hudW > 0 ? pref.hudW : spConfig.width()));
            hashMap.put("val_uiz", "§f" + Math.round(d * 100.0) + "%");
            return hashMap;
        }
        hashMap.put("show_setting", 0);
        ArrayList<QuestInfo> arrayList = new ArrayList<QuestInfo>();
        for (QuestInfo object : list) {
            if (!page.key.equals(object.category())) continue;
            arrayList.add(object);
        }
        ArrayList arrayList2 = new ArrayList();
        if (arrayList.isEmpty()) {
            arrayList2.add("§8暂无" + page.title + "任务");
        } else {
            for (QuestInfo questInfo2 : arrayList) {
                String string = questInfo2.id().equals(this.selectedOf(player)) ? "§b▸ " : "§8▸ ";
                arrayList2.add(string + "§f" + Texts.ellipsis(questInfo2.name(), 20) + "  §7" + questInfo2.progressText());
            }
        }
        hashMap.put("quest_lines", arrayList2);
        for (QuestInfo questInfo3 : arrayList) {
            if (!questInfo3.id().equals(this.selectedOf(player))) continue;
            questInfo = questInfo3;
            break;
        }
        if (questInfo == null) {
            hashMap.put("has_sel", 0);
            hashMap.put("detail_lines", List.of("§8从左侧选择一个任务"));
            hashMap.put("reward_lines", List.of("§8-"));
        } else {
            hashMap.put("has_sel", 1);
            hashMap.put("detail_lines", this.detailLines(questInfo));
            hashMap.put("reward_lines", this.rewardLines(questInfo));
        }
        return hashMap;
    }

    private List<String> detailLines(QuestInfo questInfo) {
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("§f§l" + Texts.ellipsis(questInfo.name(), 40));
        arrayList.add("");
        for (String object : questInfo.description()) {
            arrayList.add("§7" + object);
        }
        if (!questInfo.description().isEmpty()) {
            arrayList.add("");
        }
        arrayList.add("§8目标");
        for (QuestInfo.Objective objective : questInfo.objectives()) {
            String string = objective.completed() ? "§a✔" : "§b▪";
            arrayList.add(string + " §f" + Texts.ellipsis(objective.name(), 28));
            arrayList.add("   " + this.bar(objective.percent(), 14, objective.completed()) + " §7" + objective.numbers());
        }
        return arrayList;
    }

    private List<String> rewardLines(QuestInfo questInfo) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (questInfo.rewards().isEmpty()) {
            arrayList.add("§8该任务没有配置奖励预览");
            arrayList.add("§8（实际奖励由任务脚本发放）");
            return arrayList;
        }
        for (QuestInfo.Reward reward : questInfo.rewards()) {
            String string = reward.amount() == null || reward.amount().isEmpty() ? "" : " §e" + reward.amount();
            arrayList.add("§f▪ " + reward.name() + string);
        }
        return arrayList;
    }

    private List<String> settingLines(SpConfig spConfig, PlayerPrefs.Pref pref) {
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("§fHUD 状态：  " + (pref.visible ? "§a已开启" : "§8已关闭"));
        arrayList.add("§fHUD 缩放：  §f" + Math.round(pref.scale * 100.0) + "%");
        arrayList.add("§fHUD 宽度：  §f" + (pref.hudW > 0 ? pref.hudW : spConfig.width()));
        arrayList.add("§f面板缩放：  §f" + Math.round(pref.uiScale * 100.0) + "%");
        arrayList.add("");
        arrayList.add("§8用 /sp 命令调整：toggle / move / reset / scale");
        return arrayList;
    }

    private String bar(double d, int n, boolean bl) {
        int n2 = (int)Math.round(Math.max(0.0, Math.min(1.0, d)) * (double)n);
        if (bl) {
            n2 = n;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(bl ? "§a" : "§b");
        for (int i = 0; i < n; ++i) {
            stringBuilder.append(i < n2 ? (char)'|' : '.');
        }
        return stringBuilder.toString();
    }

    public static enum Page {
        SHIXU("shixu", "时序", "&b"),
        JIAN("jian", "间章", "&d"),
        CAN("can", "残流", "&e"),
        SETTING("setting", "设置", "&7");

        public final String key;
        public final String title;
        public final String color;

        private Page(String string2, String string3, String string4) {
            this.key = string2;
            this.title = string3;
            this.color = string4;
        }

        public static Page of(String string) {
            if (string == null) {
                return SHIXU;
            }
            for (Page page : Page.values()) {
                if (!page.key.equalsIgnoreCase(string)) continue;
                return page;
            }
            return SHIXU;
        }
    }
}

