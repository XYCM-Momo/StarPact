package com.asterovie.momo.starpact.server.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * quests/ 目录的加载器。
 *
 * <p>目录结构（两种格式都支持，可混用）：
 * <pre>
 * plugins/StarPact/quests/
 * ├── 主线/
 * │   ├── 序章.yml          ← 多任务格式（顶层键是任务 ID）
 * │   └── 第一章.yml
 * ├── 支线/
 * │   └── 铁匠的请求.yml    ← 单任务格式（文件名就是任务 ID）
 * └── 活动.yml
 * </pre>
 *
 * <p><b>格式自动判断</b>：顶层含 {@code description} / {@code rewards} / {@code name}
 * 这类「单任务字段」时按单任务解析（任务 ID = 文件名去扩展名）；
 * 否则把顶层每个键当作任务 ID，按多任务解析。
 *
 * <p>子目录会被递归扫描，深度不限。
 */
public final class QuestConfigLoader {

    /** 单任务格式的判定字段 */
    private static final List<String> SINGLE_KEYS =
            List.of("description", "desc", "rewards", "reward", "name", "category");

    private final JavaPlugin plugin;
    private final File questDir;

    /** 任务 ID → 配置。后加载的同 ID 配置会覆盖先前的（便于子目录细化覆盖）。 */
    private final Map<String, QuestConfig> byId = new LinkedHashMap<>();

    public QuestConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.questDir = new File(plugin.getDataFolder(), "quests");
    }

    public File dir() {
        return questDir;
    }

    public int size() {
        return byId.size();
    }

    public QuestConfig get(String questId) {
        return questId == null ? null : byId.get(questId);
    }

    /** 所有已加载的任务 ID（只读快照）。 */
    public java.util.Set<String> ids() {
        return java.util.Set.copyOf(byId.keySet());
    }

    /**
     * 扫描并加载 quests/ 下所有 yml。
     *
     * <p>不会清空目录，只在缺失时创建一份示例文件，避免覆盖服主的内容。
     */
    public void reload() {
        byId.clear();

        if (!questDir.exists() && !questDir.mkdirs()) {
            plugin.getLogger().warning("无法创建 quests 目录: " + questDir);
            return;
        }
        installSampleIfEmpty();

        List<File> files = new ArrayList<>();
        collect(questDir, files);

        int ok = 0;
        for (File f : files) {
            try {
                if (loadFile(f)) {
                    ok++;
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "加载任务配置失败: " + f.getName(), t);
            }
        }
        plugin.getLogger().info("已加载 " + byId.size() + " 个任务展示配置（"
                + ok + "/" + files.size() + " 个文件）");
    }

    /** 递归收集 .yml / .yaml，跳过以 . 开头的文件和目录。 */
    private void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            if (c.getName().startsWith(".")) {
                continue;
            }
            if (c.isDirectory()) {
                collect(c, out);
            } else {
                String n = c.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".yml") || n.endsWith(".yaml")) {
                    out.add(c);
                }
            }
        }
    }

    /** 返回是否解析出至少一个任务。 */
    private boolean loadFile(File file) throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));

        // 格式判断：顶层出现单任务字段 → 单任务
        boolean single = false;
        for (String k : SINGLE_KEYS) {
            if (yml.contains(k)) {
                single = true;
                break;
            }
        }

        if (single) {
            String id = stripExt(file.getName());
            QuestConfig q = parseOne(id, yml);
            if (q != null) {
                byId.put(id, q);
                return true;
            }
            return false;
        }

        // 多任务格式
        boolean any = false;
        for (String key : yml.getKeys(false)) {
            ConfigurationSection sec = yml.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            QuestConfig q = parseOne(key, sec);
            if (q != null) {
                byId.put(key, q);
                any = true;
            }
        }
        return any;
    }

    /** 从任意配置节解析一个任务；什么都没配返回 null。 */
    private QuestConfig parseOne(String id, ConfigurationSection sec) {
        String name = trimToNull(sec.getString("name"));
        String category = normalizeCategory(sec.getString("category"));

        List<String> desc = new ArrayList<>();
        List<String> rawDesc = sec.getStringList("description");
        if (rawDesc.isEmpty()) {
            rawDesc = sec.getStringList("desc");
        }
        for (String line : rawDesc) {
            if (line != null) {
                desc.add(line);
            }
        }

        List<QuestConfig.Reward> rewards = new ArrayList<>();
        List<Map<?, ?>> rawRewards = sec.getMapList("rewards");
        if (rawRewards.isEmpty()) {
            rawRewards = sec.getMapList("reward");
        }
        for (Map<?, ?> m : rawRewards) {
            QuestConfig.Reward r = parseReward(m);
            if (r != null) {
                rewards.add(r);
            }
        }

        QuestConfig q = new QuestConfig(id, name, category, desc, rewards,
                parseCommandList(sec, "track-commands", "trackCommands",
                        "track-command", "trackCommand"),
                parseCommandList(sec, "untrack-commands", "untrackCommands",
                        "untrack-command", "untrackCommand"),
                parseCommandList(sec, "complete-commands", "completeCommands",
                        "complete-command", "completeCommand",
                        "on-complete", "onComplete"),
                parseCommandList(sec, "fail-commands", "failCommands",
                        "fail-command", "failCommand",
                        "on-fail", "onFail"),
                parseNavPos(sec));
        return q.isEmpty() ? null : q;
    }

    /**
     * 解析一组指令（点追踪 / 取消追踪共用）。
     *
     * <p>键名支持连字符和小驼峰两种写法，列表或单条都可以。
     * 会自动去掉前导 {@code /}。
     */
    private List<String> parseCommandList(ConfigurationSection sec, String... keys) {
        List<String> out = new ArrayList<>();
        List<String> list = new ArrayList<>();
        for (String key : keys) {
            list = sec.getStringList(key);
            if (!list.isEmpty()) {
                break;
            }
        }
        if (list.isEmpty() && keys.length > 0) {
            // 允许写成单条字符串
            for (String key : keys) {
                String one = sec.getString(key);
                if (one != null && !one.isBlank()) {
                    list = List.of(one);
                    break;
                }
            }
        }
        for (String s : list) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String cmd = s.trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            out.add(cmd);
        }
        return out;
    }

    /**
     * 解析导航坐标 {@code nav-pos: [x, y, z]}（也接受 {@code [x, z]}）。
     */
    private List<Double> parseNavPos(ConfigurationSection sec) {
        List<Double> nums = new ArrayList<>();
        for (String key : new String[]{"nav-pos", "navPos", "nav"}) {
            List<?> raw = sec.getList(key);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            for (Object o : raw) {
                if (o instanceof Number n) {
                    nums.add(n.doubleValue());
                } else if (o != null) {
                    try {
                        nums.add(Double.parseDouble(String.valueOf(o).trim()));
                    } catch (NumberFormatException ignored) {
                        // 非数字就跳过这个键
                    }
                }
            }
            if (!nums.isEmpty()) {
                break;
            }
        }
        return nums;
    }

    private QuestConfig.Reward parseReward(Map<?, ?> m) {
        Object providerObj = m.get("provider");
        Object typeObj = m.get("type");

        // 指令型奖励：command / cmd 键直接给出指令内容
        String cmd = trimToNull(str(firstNonNull(m.get("command"), m.get("cmd"))));
        if (cmd != null) {
            String disp = trimToNull(str(firstNonNull(m.get("display"), m.get("name"))));
            String icon = trimToNull(str(firstNonNull(m.get("icon"), m.get("iconItem"))));
            return new QuestConfig.Reward("COMMAND", cmd, 1, disp, icon);
        }

        // key 允许写成 "mm:物品ID" / "ni:物品ID" 的短格式
        String key = str(firstNonNull(m.get("key"), m.get("id"), m.get("item")));
        String provider = str(firstNonNull(providerObj, typeObj));

        if (provider == null && key != null && key.contains(":")) {
            int i = key.indexOf(':');
            provider = key.substring(0, i);
            key = key.substring(i + 1);
        }
        if (key == null || key.isBlank()) {
            return null;
        }

        provider = normalizeProvider(provider);
        int amount = toInt(firstNonNull(m.get("amount"), m.get("count")), 1);
        String display = trimToNull(str(m.get("display")));
        if (display == null) {
            display = trimToNull(str(m.get("name")));
        }
        return new QuestConfig.Reward(provider, key.trim(), Math.max(1, amount), display, null);
    }

    /** provider 归一化：mm / mythicmobs / mythic → MM */
    private static String normalizeProvider(String s) {
        if (s == null || s.isBlank()) {
            return "VANILLA";
        }
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("mm") || v.contains("mythic")) {
            return "MM";
        }
        if (v.startsWith("ni") || v.contains("neige")) {
            return "NI";
        }
        if (v.startsWith("vanilla") || v.startsWith("mc") || v.startsWith("原版")) {
            return "VANILLA";
        }
        if (v.startsWith("command") || v.startsWith("cmd") || v.startsWith("指令")) {
            return "COMMAND";
        }
        return s.trim().toUpperCase(Locale.ROOT);
    }

    /** 分类归一化：接受中文和英文别名。 */
    private static String normalizeCategory(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim().toLowerCase(Locale.ROOT);
        // 间章
        if (v.contains("支线") || v.contains("间章")
                || v.equals("jian") || v.equals("jianzhang") || v.equals("side")) {
            return "jian";
        }
        // 残流
        if (v.contains("残流") || v.contains("每日") || v.contains("活动") || v.contains("限时")
                || v.equals("can") || v.equals("canliu")
                || v.equals("daily") || v.equals("event")) {
            return "can";
        }
        // 时序
        if (v.contains("主线") || v.contains("时序")
                || v.equals("shixu") || v.equals("main")) {
            return "shixu";
        }
        return null;
    }

    /** 目录为空时放一份示例（只在真的没有 yml 时）。 */
    private void installSampleIfEmpty() {
        List<File> existing = new ArrayList<>();
        collect(questDir, existing);
        if (!existing.isEmpty()) {
            return;
        }
        File sample = new File(questDir, "示例.yml");
        try {
            String text = """
                    # 星契 StarPact — 任务展示配置示例
                    #
                    # 这里只影响「任务面板显示什么」，不会发放任何东西。
                    # 实际奖励仍由 Chemdah 的任务脚本负责。
                    #
                    # ── 两种写法 ───────────────────────────────
                    # 1) 多任务格式（本文件就是）：顶层键 = 任务 ID
                    # 2) 单任务格式：文件名 = 任务 ID，下面直接写字段
                    #
                    # ── 子目录 ─────────────────────────────────
                    # quests 下可以建子目录分类管理，会递归扫描：
                    #   quests/主线/序章.yml
                    #   quests/支线/铁匠.yml

                    示例任务ID:
                      # 覆盖显示名（不写就用 Chemdah 里的名字）
                      name: "&b示例任务"

                      # 分类：shixu（时序）/ jian（间章）/ can（残流）
                      # 不写就按 Chemdah 的 meta.type 自动判断
                      category: shixu

                      # 任务描述（多行）。不写就用 Chemdah 的 addon.ui.description
                      description:
                        - "&7这是第一行描述"
                        - "&7这是第二行描述"

                      # 奖励预览（只展示）
                      rewards:
                        # MythicMobs 物品
                        - provider: MM
                          key: "SkeletonKingSword"
                          amount: 1

                        # 也可以写成短格式
                        - key: "mm:ExampleItem"
                          amount: 2

                        # 原版物品（材质名）
                        - provider: VANILLA
                          key: "DIAMOND"
                          amount: 3
                          display: "&b钻石"   # 可选，覆盖显示名
                    """;
            Files.writeString(sample.toPath(), text, StandardCharsets.UTF_8);
            plugin.getLogger().info("已生成任务配置示例: quests/示例.yml");
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "写示例文件失败", e);
        }
    }

    // ── 小工具 ───────────────────────────────────────────────

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static Object firstNonNull(Object... arr) {
        for (Object o : arr) {
            if (o != null) {
                return o;
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        return def;
    }
}
