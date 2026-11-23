package com.asterovie.momo.starpact.server;

import com.asterovie.momo.starpact.server.ax.AxConfigInstaller;
import com.asterovie.momo.starpact.server.ax.AxHook;
import com.asterovie.momo.starpact.server.chemdah.ChemdahHook;
import com.asterovie.momo.starpact.server.command.StarPactCommand;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.hud.HudRenderer;
import com.asterovie.momo.starpact.server.hud.HudService;
import com.asterovie.momo.starpact.server.hud.PanelListenerRegistrar;
import com.asterovie.momo.starpact.server.hud.PanelRenderer;
import com.asterovie.momo.starpact.server.hud.PanelService;
import com.asterovie.momo.starpact.server.quest.ItemResolver;
import com.asterovie.momo.starpact.server.quest.QuestConfigLoader;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import com.asterovie.momo.starpact.server.util.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 星契 StarPact 主类。
 *
 * <p>职责边界：
 * <ul>
 *   <li>{@link ChemdahHook} 只负责「问 Chemdah 要数据」</li>
 *   <li>{@link HudService} 负责「把数据画出来」，优先 AX HUD，否则降级 BossBar</li>
 *   <li>{@link AxHook} 负责 AX 的反射调用，失败即降级，绝不拖垮插件</li>
 * </ul>
 */
public final class StarPactPlugin extends JavaPlugin {

    private static final String BANNER = "\u00a7b\u2726 \u00a7f\u661f\u5951 StarPact";

    private SpConfig config;
    private LanguageManager lang;
    private PlayerPrefs prefs;
    private AxHook ax;
    private ChemdahHook chemdah;
    private HudService hud;
    private PanelService panel;
    private com.asterovie.momo.starpact.server.chat.ChatBridge chatBridge;
    private QuestConfigLoader quests;
    private ItemResolver items;

    private BukkitTask refreshTask;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        printBanner();

        saveDefaultConfig();
        config = new SpConfig(this);
        lang = new LanguageManager(this);
        prefs = new PlayerPrefs(this);

        items = new ItemResolver(this);
        quests = new QuestConfigLoader(this);
        quests.reload();

        chemdah = new ChemdahHook();
        ax = new AxHook(this);
        hud = new HudService(this);
        panel = new PanelService(this);
        chatBridge = new com.asterovie.momo.starpact.server.chat.ChatBridge(this);
        chatBridge.start();

        if (!chemdah.available()) {
            getLogger().warning("未检测到 Chemdah —— 任务追踪将显示为空。");
        }
        if (!ax.available()) {
            getLogger().warning("未检测到 ArcartX（或 API 不兼容）—— 已退回原版 BossBar 显示。");
        }

        registerUi();
        registerCommand();
        registerListeners();
        startTasks();
        installAxKeyConfig();
        scheduleChemdahConversationReload();
        scheduleItemPrewarm();

        getLogger().info("StarPact Plugin 已成功启用！");
        getLogger().info("  Chemdah: " + (chemdah.available() ? "已接入" : "未接入")
                + " / ArcartX: " + (ax.available() ? "已接入" : "未接入（降级 BossBar）"));
    }

    /**
     * 启动后补一次 Chemdah 的对话重载。
     *
     * <p><b>为什么需要</b>：Chemdah 的对话（conversation）是延后加载的 ——
     * 服务端刚启动时 {@code ConversationManager} 里还是空的，此时玩家进服
     * 右键 NPC 完全没反应，必须手动 {@code /ch reload} 一次才正常。
     * 这是 Chemdah 自身的加载时序问题，不是 StarPact 的。
     *
     * <p><b>为什么安全</b>：只在「没有玩家在线」时才执行，
     * 不会打断任何正在进行中的对话或任务。
     */
    private void scheduleChemdahConversationReload() {
        if (!chemdah.available()) {
            return;
        }
        // 等 10 秒：给 Chemdah 自己留出首次加载的时间，避免和它抢
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                getLogger().info("启动补载跳过：已有玩家在线，交给 Chemdah 自己处理");
                return;
            }
            getLogger().info("启动补载：重新加载 Chemdah 对话（修复「进服要 ch reload 才能对话」）");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chemdah reload");
        }, 200L);
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (hud != null) {
            hud.hideAll();
            hud.shutdown();
        }
        if (prefs != null) {
            prefs.flush();
        }
        getLogger().info("StarPact Plugin 已关闭。");
    }

    private void printBanner() {
        getLogger().info(BANNER + "  v" + getPluginMeta().getVersion());
        getLogger().info("\u672c\u63d2\u4ef6\u7531 \u661f\u91ce\u5c18\u68a6\u5de5\u4f5c\u5ba4 \u00b7 \u9ed8\u9ed8 \u5236\u4f5c");
    }

    // ── 启动装配 ──────────────────────────────────────────────

    /**
     * 把 jar 内 ui/quest-tracker.yml 复制到数据目录后注册给 ArcartX。
     *
     * <p>优先注册数据目录那一份（玩家/服主改过的外观），
     * 没有就用 jar 内置的字符串注册 —— 确保 HUD 永远存在。
     */
    private void registerUi() {
        if (!ax.available()) {
            return;
        }
        registerOneUi(HudRenderer.UI_ID, "ui/quest-tracker.yml");
        registerOneUi(PanelRenderer.UI_ID, "ui/quest-panel.yml");
        registerOneUi(PanelRenderer.HUD_SETTINGS_UI_ID, "ui/hud-settings.yml");
        // 对话面板：盖住 Chemdah 的箱子对话
        registerOneUi(com.asterovie.momo.starpact.server.chat.ChatBridge.UI_ID,
                "ui/chat-panel.yml");
    }

    /**
     * 把 jar 内 UI 定义注册给 ArcartX。
     *
     * <p>优先注册数据目录那一份（服主改过的外观），没有就用 jar 内置字符串 ——
     * 确保 UI 永远存在，不会因为少了配置文件而消失。
     */
    private void registerOneUi(String id, String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        File uiDir = new File(getDataFolder(), "ui");
        if (!uiDir.exists() && !uiDir.mkdirs()) {
            getLogger().warning("无法创建 ui 目录: " + uiDir);
        }
        File uiFile = new File(uiDir, fileName);
        if (!uiFile.exists()) {
            saveResource(resourcePath, false);
        }
        if (uiFile.exists() && ax.registerUiFile(id, uiFile)) {
            getLogger().info("已注册 UI: " + id + "（来自 " + fileName + "）");
            return;
        }
        // 兜底：直接用 jar 内文本注册
        try (var in = getResource(resourcePath)) {
            if (in != null) {
                String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (ax.registerUi(id, yaml)) {
                    getLogger().info("已注册 UI: " + id + "（来自 jar 内置定义）");
                }
            }
        } catch (IOException e) {
            getLogger().warning("读取内置 UI 定义失败 " + resourcePath + ": " + e.getMessage());
        }
    }

    private void registerCommand() {
        StarPactCommand cmd = new StarPactCommand(this);
        var command = getCommand("starpact");
        if (command != null) {
            command.setExecutor(cmd);
            command.setTabCompleter(cmd);
        }
    }

    /**
     * 把按键配置写进 ArcartX 目录，并在有新文件时让 AX 重载。
     *
     * <p>为什么要走文件（而不是纯 API 注册按键）：见
     * {@link com.asterovie.momo.starpact.server.ax.AxConfigInstaller} 的说明。
     */
    private void installAxKeyConfig() {
        if (!ax.available() || !config.installAxKeyConfig()) {
            return;
        }
        AxConfigInstaller installer = new AxConfigInstaller(this);
        if (installer.install()) {
            // 延迟一拍，确保文件已落盘，再让 AX 重新读配置
            Bukkit.getScheduler().runTaskLater(this, () -> {
                getLogger().info("让 ArcartX 重载按键配置…");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "arcartx reload");
            }, 20L);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        // 对话桥：把 Chemdah 的箱子对话翻译给 AX 面板
        Bukkit.getPluginManager().registerEvents(chatBridge, this);
        // 奖励容器只读拦截（纯 Bukkit，无条件注册）
        Bukkit.getPluginManager().registerEvents(
                new com.asterovie.momo.starpact.server.hud.RewardContainerListener(this), this);
        // 面板交互监听依赖 ArcartX 的事件类，必须隔离注册
        PanelListenerRegistrar.register(this);
        // 任务奖励发放依赖 Chemdah 的事件类，同样隔离注册
        com.asterovie.momo.starpact.server.quest.QuestRewardRegistrar.register(this);
        // 任务接取事件：接了就立刻刷 HUD，不用等 2 秒轮询
        com.asterovie.momo.starpact.server.quest.QuestAcceptRegistrar.register(this);
        // 任务失败事件：执行 fail-commands（停导航等）
        com.asterovie.momo.starpact.server.quest.QuestFailRegistrar.register(this);
    }

    private void startTasks() {
        int interval = config.intervalSeconds();
        if (interval > 0) {
            long ticks = interval * 20L;
            refreshTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        hud.refresh(p, false);
                    } catch (Throwable t) {
                        getLogger().warning("刷新 " + p.getName() + " 的 HUD 时出错: " + t);
                    }
                }
            }, ticks, ticks);
        }

        // 每秒检查一次是否有需要落盘的玩家设置
        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> prefs.flushIfDirty(), 20L, 20L);
    }

    // ── 重载 ──────────────────────────────────────────────────

    /** /sp reload：重读 config + language + 两个 UI 定义，并把结果推给所有在线玩家。 */
    /**
     * 后台预热物品解析缓存。
     *
     * <p>第一次按 J 打开面板时要把所有奖励物品走一遍 MM/NI 反射查询，
     * 那一下会明显卡顿。这里在启动后异步把缓存灌满。
     *
     * <p><b>必须异步</b>：解析要走反射 + 物品库查询，同步跑会拖慢服务端启动，
     * 物品多的服可能要几百毫秒甚至更久。
     * <p><b>必须延迟几秒</b>：物品库（MythicMobs / NeigeItems）启动时可能还没加载完，
     * 太早解析会拿到空结果并把空的也缓存起来。
     */
    private void scheduleItemPrewarm() {
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
            try {
                int n = items.prewarm(quests);
                getLogger().info("物品解析预热完成：" + n + " 条（后台线程，不影响开服）");
            } catch (Throwable t) {
                getLogger().warning("物品解析预热失败: " + t);
            }
        }, 100L);   // 5 秒
    }

    public void reloadAll() {
        config.reload();
        lang.reload();
        // 任务展示配置（描述 / 奖励）一起重载
        if (quests != null) {
            quests.reload();
        }
        // 物品解析缓存要清掉：物品库里的名称/Lore 可能已经改了
        if (items != null) {
            items.clearCache();
        }
        // 清完立刻在后台重新预热，否则下次开面板又要现场解析
        scheduleItemPrewarm();

        // 重新注册两个 UI（可能改了 ui/*.yml）
        if (ax.available()) {
            ax.unregisterUi(HudRenderer.UI_ID);
            ax.unregisterUi(PanelRenderer.UI_ID);
        ax.unregisterUi(PanelRenderer.HUD_SETTINGS_UI_ID);
            registerUi();
            // 按键走 AX 配置文件（simple_key），不在这里注册

            // UI 重新注册后客户端的旧 UI 已失效，清掉「已打开」记录，
            // 下次 refresh 会重新 open —— 否则 reload 后 HUD 会消失。
            hud.resetOpened();
        }
        // 重建刷新任务（间隔可能变了）
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        startTasks();

        for (Player p : Bukkit.getOnlinePlayers()) {
            hud.refresh(p, true);
        }
    }

    // ── 访问器 ──────────────────────────────────────────────

    public SpConfig spConfig() {
        return config;
    }

    public LanguageManager lang() {
        return lang;
    }

    public PlayerPrefs prefs() {
        return prefs;
    }

    public AxHook ax() {
        return ax;
    }

    public ChemdahHook chemdah() {
        return chemdah;
    }

    public HudService hud() {
        return hud;
    }

    public PanelService panel() {
        return panel;
    }

    public com.asterovie.momo.starpact.server.chat.ChatBridge chatBridge() {
        return chatBridge;
    }

    /** quests/ 目录的任务展示配置。 */
    public QuestConfigLoader quests() {
        return quests;
    }

    /** MythicMobs / NeigeItems 物品名解析。 */
    public ItemResolver items() {
        return items;
    }

    /** 供内部/调试用：当前可显示的追踪条目。 */
    public List<?> debugTracks(Player p) {
        return chemdah.readTracks(p, config.maxTracks());
    }

    /** 复制 jar 内资源到数据目录，已存在则跳过。 */
    public void ensureResource(String path) {
        File f = new File(getDataFolder(), path);
        if (!f.exists()) {
            saveResource(path, false);
        }
    }
}
