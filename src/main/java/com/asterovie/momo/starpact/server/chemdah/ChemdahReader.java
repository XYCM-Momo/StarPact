package com.asterovie.momo.starpact.server.chemdah;

import com.asterovie.momo.starpact.server.model.QuestInfo;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import com.asterovie.momo.starpact.server.util.Texts;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonStats;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.meta.MetaName;
import ink.ptms.chemdah.core.quest.meta.MetaType;
import ink.ptms.chemdah.core.quest.objective.Progress;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.entity.Player;

final class ChemdahReader {
    static List<TrackEntry> read(Player player, int n) {
        ArrayList<TrackEntry> arrayList = new ArrayList<TrackEntry>();
        if (player == null || n <= 0) {
            return arrayList;
        }
        try {
            ChemdahReader.read0(player, n, arrayList);
        }
        catch (Throwable throwable) {
            player.getServer().getLogger().fine("[StarPact] 读取 Chemdah 任务失败: " + String.valueOf(throwable));
        }
        return arrayList;
    }

    private static void read0(Player player, int n, List<TrackEntry> list) {
        Quest quest;
        PlayerProfile playerProfile = ChemdahAPI.INSTANCE.getChemdahProfile(player);
        if (playerProfile == null) {
            return;
        }
        Template template = AddonTrack.Companion.getTrackQuest(playerProfile);
        ArrayList<Quest> arrayList = new ArrayList<Quest>();
        if (template != null && (quest = playerProfile.getQuestById(template.getId(), false)) != null && quest.isValid() && !quest.isCompleted()) {
            arrayList.add(quest);
        }
        if (arrayList.isEmpty()) {
            for (Quest quest2 : playerProfile.getQuests(false)) {
                if (quest2 == null || !quest2.isValid() || quest2.isCompleted()) continue;
                arrayList.add(quest2);
            }
        }
        arrayList.sort(Comparator.comparingLong(Quest::getStartTime));
        for (Quest quest2 : arrayList) {
            if (list.size() >= n) break;
            ChemdahReader.collect(quest2, playerProfile, n, list);
        }
    }

    private static void collect(Quest quest, PlayerProfile playerProfile, int n, List<TrackEntry> list) {
        Template template = quest.getTemplate();
        if (template == null) {
            return;
        }
        String string = ChemdahReader.nameOf((QuestContainer)template, playerProfile);
        ArrayList<Task> arrayList = new ArrayList<Task>(quest.getTasks());
        arrayList.sort(Comparator.comparing(task -> task.isCompleted(playerProfile)).thenComparing(task -> task.getId()));
        for (Task task2 : arrayList) {
            if (list.size() >= n) {
                return;
            }
            boolean bl = task2.isCompleted(playerProfile);
            Progress progress = ChemdahReader.progressOf(template, task2, playerProfile);
            String string2 = ChemdahReader.nameOf((QuestContainer)task2, playerProfile);
            if (string2.isEmpty()) {
                string2 = string;
            }
            double d = 0.0;
            double d2 = 0.0;
            double d3 = 0.0;
            if (progress != null) {
                d = ChemdahReader.asDouble(progress.getValue());
                d2 = ChemdahReader.asDouble(progress.getTarget());
                d3 = progress.getPercent();
                if (d2 > 0.0 && d > d2) {
                    d = d2;
                }
                if (d3 <= 0.0 && d2 > 0.0) {
                    d3 = Math.min(1.0, d / d2);
                }
            }
            d3 = Math.max(0.0, Math.min(1.0, d3));
            list.add(new TrackEntry(quest.getId(), task2.getId(), string2, Texts.strip(string2), d, d2, d3, bl));
        }
    }

    private static Progress progressOf(Template template, Task task, PlayerProfile playerProfile) {
        Progress progress = ChemdahReader.tryProgress((QuestContainer)task, playerProfile);
        if (progress != null) {
            return progress;
        }
        return ChemdahReader.tryProgress((QuestContainer)template, playerProfile);
    }

    private static Progress tryProgress(QuestContainer questContainer, PlayerProfile playerProfile) {
        try {
            CompletableFuture completableFuture = AddonStats.Companion.getProgress(questContainer, playerProfile);
            if (completableFuture == null) {
                return null;
            }
            if (completableFuture.isDone()) {
                return completableFuture.getNow(null);
            }
            return (Progress)completableFuture.get(50L, TimeUnit.MILLISECONDS);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private static String nameOf(QuestContainer questContainer, PlayerProfile playerProfile) {
        try {
            String string = MetaName.Companion.displayName(questContainer, false, playerProfile);
            return string == null ? "" : string;
        }
        catch (Throwable throwable) {
            return "";
        }
    }

    static List<QuestInfo> readQuests(Player player) {
        ArrayList<QuestInfo> arrayList = new ArrayList<QuestInfo>();
        if (player == null) {
            return arrayList;
        }
        try {
            PlayerProfile playerProfile = ChemdahAPI.INSTANCE.getChemdahProfile(player);
            if (playerProfile == null) {
                return arrayList;
            }
            for (Quest quest : playerProfile.getQuests(false)) {
                if (quest == null || !quest.isValid() || quest.isCompleted()) continue;
                try {
                    arrayList.add(ChemdahReader.toInfo(quest, playerProfile));
                }
                catch (Throwable throwable) {
                    player.getServer().getLogger().fine("[StarPact] 读取任务 " + quest.getId() + " 失败: " + String.valueOf(throwable));
                }
            }
            arrayList.sort(Comparator.comparingLong(questInfo -> 0L));
        }
        catch (Throwable throwable) {
            player.getServer().getLogger().fine("[StarPact] 读取任务列表失败: " + String.valueOf(throwable));
        }
        return arrayList;
    }

    private static QuestInfo toInfo(Quest quest, PlayerProfile playerProfile) {
        Template template = quest.getTemplate();
        String string = quest.getId();
        String string2 = template != null ? ChemdahReader.nameOf((QuestContainer)template, playerProfile) : string;
        String string3 = string2;
        if (string2.isEmpty()) {
            string2 = string;
        }
        String string4 = ChemdahReader.categoryOf(template);
        List<String> list = ChemdahReader.descriptionOf(template);
        ArrayList<QuestInfo.Objective> arrayList = new ArrayList<QuestInfo.Objective>();
        for (Task task : quest.getTasks()) {
            boolean bl = task.isCompleted(playerProfile);
            Progress progress = ChemdahReader.progressOf(template, task, playerProfile);
            double d = 0.0;
            double d2 = 0.0;
            double d3 = 0.0;
            if (progress != null) {
                d = ChemdahReader.asDouble(progress.getValue());
                d2 = ChemdahReader.asDouble(progress.getTarget());
                d3 = progress.getPercent();
                if (d2 > 0.0 && d > d2) {
                    d = d2;
                }
                if (d3 <= 0.0 && d2 > 0.0) {
                    d3 = Math.min(1.0, d / d2);
                }
            }
            d3 = Math.max(0.0, Math.min(1.0, d3));
            String string5 = ChemdahReader.nameOf((QuestContainer)task, playerProfile);
            if (string5.isEmpty()) {
                string5 = task.getId();
            }
            arrayList.add(new QuestInfo.Objective(task.getId(), string5, d, d2, d3, bl));
        }
        arrayList.sort(Comparator.comparing(QuestInfo.Objective::completed).thenComparing(QuestInfo.Objective::id));
        List<QuestInfo.Reward> list2 = ChemdahReader.rewardsOf(template);
        return new QuestInfo(string, string2, string4, list, arrayList, list2);
    }

    private static String categoryOf(Template template) {
        if (template == null) {
            return "shixu";
        }
        try {
            List list = MetaType.Companion.type(template);
            if (list != null) {
                for (String string : list) {
                    if (string == null) continue;
                    String string2 = string.toLowerCase(Locale.ROOT);
                    if (string2.contains("支线") || string2.contains("间章") || string2.contains("side")) {
                        return "jian";
                    }
                    if (string2.contains("每日") || string2.contains("活动") || string2.contains("限时") || string2.contains("daily") || string2.contains("event")) {
                        return "can";
                    }
                    if (!string2.contains("主线") && !string2.contains("时序") && !string2.contains("main")) continue;
                    return "shixu";
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return "shixu";
    }

    private static List<String> descriptionOf(Template template) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (template == null) {
            return arrayList;
        }
        try {
            Object v;
            HashMap hashMap = template.getAddonMap();
            if (hashMap != null && (v = hashMap.get("ui")) != null) {
                Method method = v.getClass().getMethod("getDescription", new Class[0]);
                Object object = method.invoke(v, new Object[0]);
                if (object instanceof List) {
                    List list = (List)object;
                    for (Object e : list) {
                        arrayList.add(String.valueOf(e));
                    }
                } else if (object instanceof String) {
                    String string = (String)object;
                    arrayList.add(string);
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return arrayList;
    }

    private static List<QuestInfo.Reward> rewardsOf(Template template) {
        ArrayList<QuestInfo.Reward> arrayList = new ArrayList<QuestInfo.Reward>();
        if (template == null) {
            return arrayList;
        }
        try {
            Object object = template.getClass().getMethod("getConfig", new Class[0]).invoke((Object)template, new Object[0]);
            if (object == null) {
                return arrayList;
            }
            Object object2 = object.getClass().getMethod("getConfigurationSection", String.class).invoke(object, "addon.reward");
            if (object2 == null) {
                return arrayList;
            }
            Object object3 = object2.getClass().getMethod("getKeys", Boolean.TYPE).invoke(object2, false);
            if (!(object3 instanceof Set)) {
                return arrayList;
            }
            Set set = (Set)object3;
            Method method = object2.getClass().getMethod("getString", String.class, String.class);
            for (Object e : set) {
                String string = String.valueOf(e);
                Object object4 = method.invoke(object2, string + ".name", string);
                Object object5 = method.invoke(object2, string + ".amount", "");
                arrayList.add(new QuestInfo.Reward(String.valueOf(object4), String.valueOf(object5)));
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return arrayList;
    }

    private static double asDouble(Object object) {
        if (object instanceof Number) {
            Number number = (Number)object;
            return number.doubleValue();
        }
        if (object != null) {
            try {
                return Double.parseDouble(String.valueOf(object));
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return 0.0;
    }

    private ChemdahReader() {
    }
}

