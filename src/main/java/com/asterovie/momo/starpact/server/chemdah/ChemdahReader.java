package com.asterovie.momo.starpact.server.chemdah;

import com.asterovie.momo.starpact.server.model.QuestInfo;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import com.asterovie.momo.starpact.server.util.Texts;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Chemdah 的实际读取实现。
 *
 * <p><b>这个类只有在确认 Chemdah 存在之后才会被加载。</b>
 * 原因是它直接 import 了 {@code ink.ptms.chemdah.*}，一旦 JVM 解析到这些符号
 * 而 Chemdah 又没装，就会抛 {@link NoClassDefFoundError} —— 而这个错误
 * 在类初始化阶段抛出，catch 不住，会把整个插件启用流程打断。
 *
 * <p>所以外层 {@link ChemdahHook} 只做「Chemdah 在不在」的判断（纯字符串反射，
 * 不 import 任何 Chemdah 类），确认在之后才反射式地 new 出本类。
 */
final class ChemdahReader {

    /** 由 {@link ChemdahHook} 通过反射调用，因此必须是包可见或 public。 */
    static List<TrackEntry> read(Player player, int max) {
        List<TrackEntry> out = new ArrayList<>();
        if (player == null || max <= 0) {
            return out;
        }
        try {
            read0(player, max, out);
        } catch (Throwable t) {
            player.getServer().getLogger()
                    .fine("[StarPact] 读取 Chemdah 任务失败: " + t);
        }
        return out;
    }

    private static void read0(Player player, int max, List<TrackEntry> out) {
        var profile = ink.ptms.chemdah.api.ChemdahAPI.INSTANCE.getChemdahProfile(player);
        if (profile == null) {
            return;
        }

        // 1) 玩家主动追踪的任务模板
        var tracked = ink.ptms.chemdah.core.quest.addon.AddonTrack.Companion.getTrackQuest(profile);
        List<ink.ptms.chemdah.core.quest.Quest> candidates = new ArrayList<>();

        if (tracked != null) {
            var q = profile.getQuestById(tracked.getId(), false);
            if (q != null && q.isValid() && !q.isCompleted()) {
                candidates.add(q);
            }
        }

        // 2) 没设追踪 / 追踪的已完成：退回到所有进行中任务
        if (candidates.isEmpty()) {
            for (var q : profile.getQuests(false)) {
                if (q != null && q.isValid() && !q.isCompleted()) {
                    candidates.add(q);
                }
            }
        }

        candidates.sort(Comparator.comparingLong(ink.ptms.chemdah.core.quest.Quest::getStartTime));

        for (var quest : candidates) {
            if (out.size() >= max) {
                break;
            }
            collect(quest, profile, max, out);
        }
    }

    private static void collect(ink.ptms.chemdah.core.quest.Quest quest,
                                ink.ptms.chemdah.core.PlayerProfile profile,
                                int max, List<TrackEntry> out) {
        var template = quest.getTemplate();
        if (template == null) {
            return;
        }
        String questTitle = nameOf(template, profile);

        List<ink.ptms.chemdah.core.quest.Task> tasks = new ArrayList<>(quest.getTasks());
        tasks.sort(Comparator
                .comparing((ink.ptms.chemdah.core.quest.Task t) -> t.isCompleted(profile))
                .thenComparing(ink.ptms.chemdah.core.quest.Task::getId));

        for (var task : tasks) {
            if (out.size() >= max) {
                return;
            }
            boolean completed = task.isCompleted(profile);
            var progress = progressOf(template, task, profile);

            String title = nameOf(task, profile);
            if (title.isEmpty()) {
                title = questTitle;
            }

            double value = 0, target = 0, percent = 0;
            if (progress != null) {
                value = asDouble(progress.getValue());
                target = asDouble(progress.getTarget());
                percent = progress.getPercent();
                if (target > 0 && value > target) {
                    value = target;
                }
                if (percent <= 0 && target > 0) {
                    percent = Math.min(1.0, value / target);
                }
            }
            percent = Math.max(0, Math.min(1, percent));

            out.add(new TrackEntry(
                    quest.getId(), task.getId(), title, Texts.strip(title),
                    value, target, percent, completed));
        }
    }

    private static ink.ptms.chemdah.core.quest.objective.Progress progressOf(
            ink.ptms.chemdah.core.quest.Template template,
            ink.ptms.chemdah.core.quest.Task task,
            ink.ptms.chemdah.core.PlayerProfile profile) {
        var p = tryProgress(task, profile);
        if (p != null) {
            return p;
        }
        return tryProgress(template, profile);
    }

    private static ink.ptms.chemdah.core.quest.objective.Progress tryProgress(
            ink.ptms.chemdah.core.quest.QuestContainer container,
            ink.ptms.chemdah.core.PlayerProfile profile) {
        try {
            var future = ink.ptms.chemdah.core.quest.addon.AddonStats.Companion
                    .getProgress(container, profile);
            if (future == null) {
                return null;
            }
            if (future.isDone()) {
                return future.getNow(null);
            }
            return future.get(50, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String nameOf(ink.ptms.chemdah.core.quest.QuestContainer container,
                                 ink.ptms.chemdah.core.PlayerProfile profile) {
        try {
            String n = ink.ptms.chemdah.core.quest.meta.MetaName.Companion
                    .displayName(container, false, profile);
            return n == null ? "" : n;
        } catch (Throwable t) {
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 完整任务列表（面板用）
    // ═══════════════════════════════════════════════════════════

    /**
     * 读取玩家「接取中」的所有任务，含分类、描述、目标、奖励。
     *
     * <p>分类映射：Chemdah 的 meta.type 字符串 → shixu / jian / can。
     * 匹配规则按关键字（大小写不敏感）：
     * <pre>
     *   主线/时序/main      → shixu   时序
     *   支线/间章/side      → jian    间章
     *   每日/活动/限时/daily → can     残流
     *   其他                → shixu   （兜底放进时序，避免任务看不到）
     * </pre>
     */
    static List<QuestInfo> readQuests(Player player) {
        List<QuestInfo> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        try {
            var profile = ink.ptms.chemdah.api.ChemdahAPI.INSTANCE.getChemdahProfile(player);
            if (profile == null) {
                return out;
            }
            for (var quest : profile.getQuests(false)) {
                if (quest == null || !quest.isValid() || quest.isCompleted()) {
                    continue;
                }
                try {
                    out.add(toInfo(quest, profile));
                } catch (Throwable t) {
                    player.getServer().getLogger()
                            .fine("[StarPact] 读取任务 " + quest.getId() + " 失败: " + t);
                }
            }
            out.sort(Comparator.comparingLong(q -> {
                // 稳定排序：按接取时间（早的在前）—— 需要从原 Quest 取，这里用 id 兜底
                return 0L;
            }));
        } catch (Throwable t) {
            player.getServer().getLogger().fine("[StarPact] 读取任务列表失败: " + t);
        }
        return out;
    }

    private static QuestInfo toInfo(ink.ptms.chemdah.core.quest.Quest quest,
                                    ink.ptms.chemdah.core.PlayerProfile profile) {
        var template = quest.getTemplate();
        String id = quest.getId();
        String name = template != null ? nameOf(template, profile) : id;
        if (name.isEmpty()) {
            name = id;
        }
        String category = categoryOf(template);
        List<String> desc = descriptionOf(template);

        List<QuestInfo.Objective> objs = new ArrayList<>();
        for (var task : quest.getTasks()) {
            boolean done = task.isCompleted(profile);
            var prog = progressOf(template, task, profile);
            double v = 0, tgt = 0, pct = 0;
            if (prog != null) {
                v = asDouble(prog.getValue());
                tgt = asDouble(prog.getTarget());
                pct = prog.getPercent();
                if (tgt > 0 && v > tgt) {
                    v = tgt;
                }
                if (pct <= 0 && tgt > 0) {
                    pct = Math.min(1.0, v / tgt);
                }
            }
            pct = Math.max(0, Math.min(1, pct));
            String tname = nameOf(task, profile);
            if (tname.isEmpty()) {
                tname = task.getId();
            }
            objs.add(new QuestInfo.Objective(task.getId(), tname, v, tgt, pct, done));
        }
        objs.sort(Comparator.comparing(QuestInfo.Objective::completed)
                .thenComparing(QuestInfo.Objective::id));

        List<QuestInfo.Reward> rewards = rewardsOf(template);
        return new QuestInfo(id, name, category, desc, objs, rewards);
    }

    /** meta.type → shixu / jian / can */
    private static String categoryOf(ink.ptms.chemdah.core.quest.Template template) {
        if (template == null) {
            return "shixu";
        }
        try {
            List<String> types = ink.ptms.chemdah.core.quest.meta.MetaType.Companion.type(template);
            if (types != null) {
                for (String ty : types) {
                    if (ty == null) {
                        continue;
                    }
                    String s = ty.toLowerCase(java.util.Locale.ROOT);
                    if (s.contains("支线") || s.contains("间章") || s.contains("side")) {
                        return "jian";
                    }
                    if (s.contains("每日") || s.contains("活动") || s.contains("限时")
                            || s.contains("daily") || s.contains("event")) {
                        return "can";
                    }
                    if (s.contains("主线") || s.contains("时序") || s.contains("main")) {
                        return "shixu";
                    }
                }
            }
        } catch (Throwable ignored) {
            // fallthrough
        }
        return "shixu";
    }

    /** 读 addon.ui.description（Chemdah 的任务描述约定位置） */
    private static List<String> descriptionOf(ink.ptms.chemdah.core.quest.Template template) {
        List<String> out = new ArrayList<>();
        if (template == null) {
            return out;
        }
        try {
            var addons = template.getAddonMap();
            if (addons != null) {
                Object ui = addons.get("ui");
                if (ui != null) {
                    var m = ui.getClass().getMethod("getDescription");
                    Object v = m.invoke(ui);
                    if (v instanceof List<?> list) {
                        for (Object o : list) {
                            out.add(String.valueOf(o));
                        }
                    } else if (v instanceof String s) {
                        out.add(s);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 没有 ui addon 就没有描述
        }
        return out;
    }

    /**
     * 读奖励。
     *
     * <p>Chemdah 的奖励写在 agent 里（Kether 脚本），没有结构化数据可读。
     * 这里只读 {@code addon.reward} 或 {@code addon.arcartx-view.rewards} 这类
     * 显式配置；读不到就返回空（面板显示「完成任务后由任务脚本发放」）。
     */
    private static List<QuestInfo.Reward> rewardsOf(ink.ptms.chemdah.core.quest.Template template) {
        List<QuestInfo.Reward> out = new ArrayList<>();
        if (template == null) {
            return out;
        }
        try {
            // ⚠️ 不能写 `var cfg = template.getConfig()` —— getConfig() 的返回类型是
            // ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection，
            // 而那个类**不在 Chemdah 的 jar 里**（由 TabooLib 运行时注入），
            // 直接引用会导致编译失败（同类问题还有 BukkitProxyEvent）。
            // 所以这里全程用反射 + Object，不绑定该类型。
            Object cfg = template.getClass().getMethod("getConfig").invoke(template);
            if (cfg == null) {
                return out;
            }
            Object sec = cfg.getClass()
                    .getMethod("getConfigurationSection", String.class)
                    .invoke(cfg, "addon.reward");
            if (sec == null) {
                return out;
            }
            Object keysObj = sec.getClass().getMethod("getKeys", boolean.class)
                    .invoke(sec, false);
            if (!(keysObj instanceof java.util.Set<?> keys)) {
                return out;
            }
            var getStr = sec.getClass().getMethod("getString", String.class, String.class);
            for (Object k : keys) {
                String key = String.valueOf(k);
                Object nm = getStr.invoke(sec, key + ".name", key);
                Object am = getStr.invoke(sec, key + ".amount", "");
                out.add(new QuestInfo.Reward(String.valueOf(nm), String.valueOf(am)));
            }
        } catch (Throwable ignored) {
            // 没有奖励配置
        }
        return out;
    }

    private static double asDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return 0;
    }

    private ChemdahReader() {
    }
}
