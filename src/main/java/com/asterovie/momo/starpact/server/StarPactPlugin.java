package com.asterovie.momo.starpact.server;

import com.asterovie.momo.starpact.server.PlayerListener;
import com.asterovie.momo.starpact.server.ax.AxConfigInstaller;
import com.asterovie.momo.starpact.server.ax.AxHook;
import com.asterovie.momo.starpact.server.chemdah.ChemdahHook;
import com.asterovie.momo.starpact.server.command.StarPactCommand;
import com.asterovie.momo.starpact.server.config.SpConfig;
import com.asterovie.momo.starpact.server.hud.HudService;
import com.asterovie.momo.starpact.server.hud.PanelListenerRegistrar;
import com.asterovie.momo.starpact.server.hud.PanelService;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import com.asterovie.momo.starpact.server.util.LanguageManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 插件主类。负责装配各个子系统（配置、语言、玩家偏好、ArcartX、Chemdah、HUD、面板），
 * 注册命令与监听器，并启动定时刷新任务。
 */
public final class StarPactPlugin
extends JavaPlugin {
    private static final String BANNER = "§b✦ §f星契 StarPact";
    private SpConfig config;
    private LanguageManager lang;
    private PlayerPrefs prefs;
    private AxHook ax;
    private ChemdahHook chemdah;
    private HudService hud;
    private PanelService panel;
    private BukkitTask refreshTask;
    private BukkitTask saveTask;

    public void onEnable() {
        this.printBanner();
        this.saveDefaultConfig();
        this.config = new SpConfig(this);
        this.lang = new LanguageManager(this);
        this.prefs = new PlayerPrefs(this);
        this.chemdah = new ChemdahHook();
        this.ax = new AxHook((Plugin)this);
        this.hud = new HudService(this);
        this.panel = new PanelService(this);
        if (!this.chemdah.available()) {
            this.getLogger().warning("未检测到 Chemdah —— 任务追踪将显示为空。");
        }
        if (!this.ax.available()) {
            this.getLogger().warning("未检测到 ArcartX（或 API 不兼容）—— 已退回原版 BossBar 显示。");
        }
        this.registerUi();
        this.registerCommand();
        this.registerListeners();
        this.startTasks();
        this.installAxKeyConfig();
        this.getLogger().info("StarPact Plugin 已成功启用！");
        this.getLogger().info("  Chemdah: " + (this.chemdah.available() ? "已接入" : "未接入") + " / ArcartX: " + (this.ax.available() ? "已接入" : "未接入（降级 BossBar）"));
    }

    public void onDisable() {
        if (this.refreshTask != null) {
            this.refreshTask.cancel();
        }
        if (this.saveTask != null) {
            this.saveTask.cancel();
        }
        if (this.hud != null) {
            this.hud.hideAll();
            this.hud.shutdown();
        }
        if (this.prefs != null) {
            this.prefs.flush();
        }
        this.getLogger().info("StarPact Plugin 已关闭。");
    }

    private void printBanner() {
        this.getLogger().info("§b✦ §f星契 StarPact  v" + this.getPluginMeta().getVersion());
        this.getLogger().info("本插件由 星野尘梦工作室 · 默默 制作");
    }

    private void registerUi() {
        try {
            this.registerUiInternal();
        }
        catch (Throwable throwable) {
            this.getLogger().warning("注册 UI 失败（不影响按键配置）: " + String.valueOf(throwable));
        }
    }

    private void registerUiInternal() {
        if (!this.ax.available()) {
            return;
        }
        this.registerOneUi("starpact:quest_tracker", "ui/quest-tracker.yml");
        this.registerOneUi("starpact:hud_settings", "ui/hud-settings.yml");
    }

    private void registerOneUi(String string, String string2) {
        Object object;
        InputStream inputStream;
        File file;
        String string3;
        block26: {
            string3 = string2.substring(string2.lastIndexOf(47) + 1);
            File file2 = new File(this.getDataFolder(), "ui");
            if (!file2.exists() && !file2.mkdirs()) {
                this.getLogger().warning("无法创建 ui 目录: " + String.valueOf(file2));
            }
            file = new File(file2, string3);
            try {
                inputStream = this.getResource(string2);
                try {
                    if (inputStream == null) break block26;
                    object = inputStream.readAllBytes();
                    String string4 = new String((byte[])object, StandardCharsets.UTF_8);
                    boolean bl = true;
                    if (file.exists()) {
                        try {
                            String string5 = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                            bl = !string5.equals(string4);
                        }
                        catch (Throwable throwable) {
                            bl = true;
                        }
                    }
                    if (bl) {
                        try {
                            Files.write(file.toPath(), object, new OpenOption[0]);
                            this.getLogger().info("已更新 UI 文件: " + string3);
                        }
                        catch (Throwable throwable) {
                            this.getLogger().warning("写入 UI 文件失败: " + String.valueOf(throwable));
                        }
                    }
                }
                finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if (file.exists() && this.ax.registerUiFile(string, file)) {
            this.getLogger().info("已注册 UI: " + string + "（来自 " + string3 + "）");
            return;
        }
        try {
            inputStream = this.getResource(string2);
            try {
                if (inputStream != null) {
                    String string6 = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    object = string6;
                    if (this.ax.registerUi(string, string6)) {
                        this.getLogger().info("已注册 UI: " + string + "（来自 jar 内置定义）");
                    }
                }
            }
            finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        }
        catch (IOException iOException) {
            this.getLogger().warning("读取内置 UI 定义失败 " + string2 + ": " + iOException.getMessage());
        }
    }

    private void registerCommand() {
        StarPactCommand starPactCommand = new StarPactCommand(this);
        PluginCommand pluginCommand = this.getCommand("starpact");
        if (pluginCommand != null) {
            pluginCommand.setExecutor((CommandExecutor)starPactCommand);
            pluginCommand.setTabCompleter((TabCompleter)starPactCommand);
        }
    }

    private void installAxKeyConfig() {
        if (!this.ax.available() || !this.config.installAxKeyConfig()) {
            return;
        }
        AxConfigInstaller axConfigInstaller = new AxConfigInstaller(this);
        if (axConfigInstaller.install()) {
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
                this.getLogger().info("让 ArcartX 重载按键配置…");
                Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)"arcartx reload");
            }, 20L);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents((Listener)new PlayerListener(this), (Plugin)this);
        PanelListenerRegistrar.register(this);
    }

    private void startTasks() {
        int n = this.config.intervalSeconds();
        if (n > 0) {
            long l = (long)n * 20L;
            this.refreshTask = Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        this.hud.refresh(player, false);
                    }
                    catch (Throwable throwable) {
                        this.getLogger().warning("刷新 " + player.getName() + " 的 HUD 时出错: " + String.valueOf(throwable));
                    }
                }
            }, l, l);
        }
        this.saveTask = Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> this.prefs.flushIfDirty(), 20L, 20L);
    }

    public void reloadAll() {
        this.config.reload();
        this.lang.reload();
        if (this.ax.available()) {
            this.ax.unregisterUi("starpact:quest_tracker");
            this.ax.unregisterUi("menu/星契面板");
            this.ax.unregisterUi("starpact:hud_settings");
            this.registerUi();
        }
        if (this.refreshTask != null) {
            this.refreshTask.cancel();
            this.refreshTask = null;
        }
        this.startTasks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.hud.refresh(player, true);
        }
    }

    public SpConfig spConfig() {
        return this.config;
    }

    public LanguageManager lang() {
        return this.lang;
    }

    public PlayerPrefs prefs() {
        return this.prefs;
    }

    public AxHook ax() {
        return this.ax;
    }

    public ChemdahHook chemdah() {
        return this.chemdah;
    }

    public HudService hud() {
        return this.hud;
    }

    public PanelService panel() {
        return this.panel;
    }

    public List<?> debugTracks(Player player) {
        return this.chemdah.readTracks(player, this.config.maxTracks());
    }

    public void ensureResource(String string) {
        File file = new File(this.getDataFolder(), string);
        if (!file.exists()) {
            this.saveResource(string, false);
        }
    }
}

