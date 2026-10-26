package com.asterovie.momo.starpact.server.ax;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * ArcartX 的反射封装。所有对 AX 的调用都经过这里，
 * AX 缺失或 API 变动时只降级、不抛异常。
 */
public final class AxHook {
    private final Plugin plugin;
    private boolean available;
    private Object registry;
    private Method mRegisterStr;
    private Method mRegisterFile;
    private Method mUnregister;
    private Method mOpen;
    private Method mClose;
    private Method mSendPacket;
    private Method mRun;

    public AxHook(Plugin plugin) {
        this.plugin = plugin;
        this.init();
    }

    private void init() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (plugin == null || !plugin.isEnabled()) {
            this.available = false;
            return;
        }
        try {
            Class<?> clazz = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Method method = clazz.getMethod("getUIRegistry", new Class[0]);
            this.registry = method.invoke(null, new Object[0]);
            Class<?> clazz2 = this.registry.getClass();
            this.mRegisterStr = clazz2.getMethod("register", String.class, String.class);
            this.mUnregister = clazz2.getMethod("unregister", String.class);
            this.mOpen = clazz2.getMethod("open", Player.class, String.class);
            this.mClose = clazz2.getMethod("close", Player.class, String.class);
            this.mSendPacket = clazz2.getMethod("sendPacket", Player.class, String.class, String.class, Object.class);
            this.mRun = clazz2.getMethod("run", Player.class, String.class, String.class);
            try {
                this.mRegisterFile = clazz2.getMethod("register", String.class, File.class);
            }
            catch (NoSuchMethodException noSuchMethodException) {
                this.mRegisterFile = null;
            }
            this.available = true;
        }
        catch (Throwable throwable) {
            this.available = false;
            this.plugin.getLogger().warning("ArcartX 存在但 API 反射失败（版本不兼容？）：" + String.valueOf(throwable));
        }
    }

    public boolean available() {
        return this.available;
    }

    public boolean registerUi(String string, String string2) {
        if (!this.available) {
            return false;
        }
        try {
            this.mRegisterStr.invoke(this.registry, string, string2);
            return true;
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("注册 ArcartX UI '" + string + "' 失败: " + AxHook.unwrap(throwable));
            return false;
        }
    }

    public boolean registerUiFile(String string, File file) {
        if (!this.available || this.mRegisterFile == null) {
            return false;
        }
        try {
            this.mRegisterFile.invoke(this.registry, string, file);
            return true;
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("注册 ArcartX UI '" + string + "' 失败: " + AxHook.unwrap(throwable));
            return false;
        }
    }

    public void unregisterUi(String string) {
        if (!this.available) {
            return;
        }
        try {
            this.mUnregister.invoke(this.registry, string);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("注销 ArcartX UI '" + string + "' 失败: " + AxHook.unwrap(throwable));
        }
    }

    public boolean registerKey(String string, String string2, String string3, Consumer<Player> consumer) {
        if (!this.available) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object object2 = clazz.getMethod("getKeyBindRegistry", new Class[0]).invoke(null, new Object[0]);
            Class<?> clazz2 = Class.forName("priv.seventeen.artist.arcartx.util.collections.KeyCallBack");
            Object object3 = Proxy.newProxyInstance(clazz2.getClassLoader(), new Class[]{clazz2}, (object, method, objectArray) -> {
                Object object2;
                if ("call".equals(method.getName()) && objectArray != null && objectArray.length >= 1 && (object2 = objectArray[0]) instanceof Player) {
                    Player player = (Player)object2;
                    consumer.accept(player);
                }
                return AxHook.kotlinUnit(method.getReturnType());
            });
            object2.getClass().getMethod("registerClientKeyBind", String.class, String.class, String.class, clazz2).invoke(object2, string, string2, string3, object3);
            this.resendSettingsToAll();
            return true;
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("注册 ArcartX 按键 '" + string + "' 失败: " + AxHook.unwrap(throwable));
            return false;
        }
    }

    private void resendSettingsToAll() {
        try {
            Class<?> clazz = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object object = clazz.getMethod("getNetworkSender", new Class[0]).invoke(null, new Object[0]);
            Method method = object.getClass().getMethod("sendSetting", Player.class);
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    method.invoke(object, player);
                }
                catch (Throwable throwable) {}
            }
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("补发 ArcartX setting 失败: " + AxHook.unwrap(throwable));
        }
    }

    public void sendSettings(Player player) {
        if (!this.available || player == null || !player.isOnline()) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object object = clazz.getMethod("getNetworkSender", new Class[0]).invoke(null, new Object[0]);
            object.getClass().getMethod("sendSetting", Player.class).invoke(object, player);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("发送 ArcartX setting 失败: " + AxHook.unwrap(throwable));
        }
    }

    public void unregisterKey(String string) {
        if (!this.available) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object object = clazz.getMethod("getKeyBindRegistry", new Class[0]).invoke(null, new Object[0]);
            object.getClass().getMethod("unregisterClientKeyBind", String.class).invoke(object, string);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("注销 ArcartX 按键 '" + string + "' 失败: " + AxHook.unwrap(throwable));
        }
    }

    private static Object kotlinUnit(Class<?> clazz) {
        if (clazz == Void.TYPE || clazz == Void.class) {
            return null;
        }
        try {
            return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public void open(Player player, String string) {
        if (!this.available) {
            return;
        }
        try {
            this.mOpen.invoke(this.registry, player, string);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("打开 ArcartX UI 失败: " + AxHook.unwrap(throwable));
        }
    }

    public void close(Player player, String string) {
        if (!this.available) {
            return;
        }
        try {
            this.mClose.invoke(this.registry, player, string);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("关闭 ArcartX UI 失败: " + AxHook.unwrap(throwable));
        }
    }

    public void sendPacket(Player player, String string, String string2, Object object) {
        if (!this.available) {
            return;
        }
        try {
            this.mSendPacket.invoke(this.registry, player, string, string2, object);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("向 ArcartX UI 发包失败: " + AxHook.unwrap(throwable));
        }
    }

    public void run(Player player, String string, String string2) {
        if (!this.available) {
            return;
        }
        try {
            this.mRun.invoke(this.registry, player, string, string2);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("在 ArcartX UI 执行脚本失败: " + AxHook.unwrap(throwable));
        }
    }

    public void sendVariable(Player player, String string, Object object) {
        if (!this.available) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object object2 = clazz.getMethod("getNetworkSender", new Class[0]).invoke(null, new Object[0]);
            object2.getClass().getMethod("sendServerVariable", Player.class, String.class, Object.class).invoke(object2, player, string, object);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("发送 ArcartX 变量失败: " + AxHook.unwrap(throwable));
        }
    }

    public void sendVariables(Player player, Map<String, Object> map) {
        if (!this.available) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object object = clazz.getMethod("getNetworkSender", new Class[0]).invoke(null, new Object[0]);
            object.getClass().getMethod("sendMultipleServerVariable", Player.class, Map.class).invoke(object, player, map);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().fine("批量发送 ArcartX 变量失败: " + AxHook.unwrap(throwable));
        }
    }

    private static String unwrap(Throwable throwable) {
        Throwable throwable2;
        for (throwable2 = throwable; throwable2.getCause() != null && throwable2.getCause() != throwable2; throwable2 = throwable2.getCause()) {
        }
        return throwable2.getClass().getSimpleName() + ": " + throwable2.getMessage();
    }
}

