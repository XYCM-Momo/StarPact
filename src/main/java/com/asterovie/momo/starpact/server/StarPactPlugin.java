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

    private BukkitTask refreshTask;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        printBanner();

        saveDefaultConfig();
        config = new SpConfig(this);
        lang = new LanguageManager(this);
        prefs = new PlayerPrefs(this);

        chemdah = new ChemdahHook();
        ax = new AxHook(this);
        hud = new HudService(this);
        panel = new PanelService(this);

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

        getLogger().info("StarPact Plugin 已成功启用！");
        getLogger().info("  Chemdah: " + (chemdah.available() ? "已接入" : "未接入")
                + " / ArcartX: " + (ax.available() ? "已接入" : "未接入（降级 BossBar）"));
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
        // 面板交互监听依赖 ArcartX 的事件类，必须隔离注册
        PanelListenerRegistrar.register(this);
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
    public void reloadAll() {
        config.reload();
        lang.reload();

        // 重新注册两个 UI（可能改了 ui/*.yml）
        if (ax.available()) {
            ax.unregisterUi(HudRenderer.UI_ID);
            ax.unregisterUi(PanelRenderer.UI_ID);
        ax.unregisterUi(PanelRenderer.HUD_SETTINGS_UI_ID);
            registerUi();
            // 按键走 AX 配置文件（simple_key），不在这里注册
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
