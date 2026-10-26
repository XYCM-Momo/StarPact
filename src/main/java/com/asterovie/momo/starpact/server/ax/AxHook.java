package com.asterovie.momo.starpact.server.ax;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * ArcartX 的反射封装。
 *
 * <p>为什么不直接 {@code compileOnly} 调 API：AX 是 Kotlin 写的，
 * 方法签名带 {@code INSTANCE} / 默认参数，跨版本易变（2.3.6 → 2.7.77 已经改过）。
 * 用反射可以做「找不到就降级」，插件永远不会因为 AX 升级而启动失败。
 *
 * <p>已验证的实际签名（2.7.77）：
 * <pre>
 * ArcartXAPI.getUIRegistry()                      → ArcartXUIRegistry
 * ArcartXUIRegistry.register(String, String)      → ArcartXUI
 * ArcartXUIRegistry.unregister(String)
 * ArcartXUIRegistry.open(Player, String)
 * ArcartXUIRegistry.close(Player, String)
 * ArcartXUIRegistry.sendPacket(Player, String, String, Object)
 * ArcartXUIRegistry.run(Player, String, String)
 * </pre>
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
        init();
    }

    private void init() {
        Plugin ax = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (ax == null || !ax.isEnabled()) {
            available = false;
            return;
        }
        try {
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Method getRegistry = apiClass.getMethod("getUIRegistry");
            registry = getRegistry.invoke(null);

            Class<?> regClass = registry.getClass();
            mRegisterStr = regClass.getMethod("register", String.class, String.class);
            mUnregister = regClass.getMethod("unregister", String.class);
            mOpen = regClass.getMethod("open", Player.class, String.class);
            mClose = regClass.getMethod("close", Player.class, String.class);
            mSendPacket = regClass.getMethod("sendPacket", Player.class, String.class, String.class, Object.class);
            mRun = regClass.getMethod("run", Player.class, String.class, String.class);
            try {
                mRegisterFile = regClass.getMethod("register", String.class, File.class);
            } catch (NoSuchMethodException ignored) {
                mRegisterFile = null;
            }
            available = true;
        } catch (Throwable t) {
            available = false;
            plugin.getLogger().warning("ArcartX 存在但 API 反射失败（版本不兼容？）：" + t);
        }
    }

    public boolean available() {
        return available;
    }

    /** 用 YAML 文本注册 UI。成功返回 true。 */
    public boolean registerUi(String id, String yaml) {
        if (!available) {
            return false;
        }
        try {
            mRegisterStr.invoke(registry, id, yaml);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("注册 ArcartX UI '" + id + "' 失败: " + unwrap(t));
            return false;
        }
    }

    /** 用文件注册 UI。 */
    public boolean registerUiFile(String id, File file) {
        if (!available || mRegisterFile == null) {
            return false;
        }
        try {
            mRegisterFile.invoke(registry, id, file);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("注册 ArcartX UI '" + id + "' 失败: " + unwrap(t));
            return false;
        }
    }

    public void unregisterUi(String id) {
        if (!available) {
            return;
        }
        try {
            mUnregister.invoke(registry, id);
        } catch (Throwable t) {
            plugin.getLogger().fine("注销 ArcartX UI '" + id + "' 失败: " + unwrap(t));
        }
    }

    // ── 按键绑定 ────────────────────────────────────────────

    /**
     * 注册一个客户端按键，并绑定按下回调。
     *
     * <p>对应 API：
     * {@code ArcartXKeyBindRegistry.registerClientKeyBind(id, category, defaultKey, KeyCallBack)}
     *
     * @param id      按键 ID（也是客户端按键表里显示的名字）
     * @param category 客户端按键设置里的分类名
     * @param defaultKey 默认按键，如 "J"
     * @param onPress  按下时回调，参数是玩家
     */
    public boolean registerKey(String id, String category, String defaultKey,
                               java.util.function.Consumer<Player> onPress) {
        if (!available) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object keyRegistry = apiClass.getMethod("getKeyBindRegistry").invoke(null);

            // KeyCallBack 是 Kotlin 的 fun interface，运行时是一个接口，
            // Java 侧用动态代理实现即可，不必编译期依赖它。
            Class<?> callbackType = Class.forName(
                    "priv.seventeen.artist.arcartx.util.collections.KeyCallBack");
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[]{callbackType},
                    (p, method, args) -> {
                        if ("call".equals(method.getName()) && args != null && args.length >= 1) {
                            Object arg = args[0];
                            if (arg instanceof Player pl) {
                                onPress.accept(pl);
                            }
                        }
                        return kotlinUnit(method.getReturnType());
                    });

            keyRegistry.getClass()
                    .getMethod("registerClientKeyBind",
                            String.class, String.class, String.class, callbackType)
                    .invoke(keyRegistry, id, category, defaultKey, proxy);

            // ⚠️ 关键：registerClientKeyBind 只是把 ClientKeyElement 塞进服务端内存 map，
            // **不会**通知客户端。按键表是随 SPackSettings 一起下发的（玩家加入 / reload 时）。
            // 所以注册完必须补发一次 setting，否则玩家客户端按键表里根本没有这个键。
            resendSettingsToAll();
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("注册 ArcartX 按键 '" + id + "' 失败: " + unwrap(t));
            return false;
        }
    }

    /** 让在线玩家重新接收 AX 的 setting 包（含按键表、UI 列表等）。 */
    private void resendSettingsToAll() {
        try {
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object sender = apiClass.getMethod("getNetworkSender").invoke(null);
            java.lang.reflect.Method sendSetting = sender.getClass()
                    .getMethod("sendSetting", Player.class);
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                try {
                    sendSetting.invoke(sender, p);
                } catch (Throwable ignored) {
                    // 单个玩家失败不影响其他人
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("补发 ArcartX setting 失败: " + unwrap(t));
        }
    }

    /** 供玩家进服时调用：让他拿到最新的按键表。 */
    public void sendSettings(Player player) {
        if (!available || player == null || !player.isOnline()) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object sender = apiClass.getMethod("getNetworkSender").invoke(null);
            sender.getClass().getMethod("sendSetting", Player.class).invoke(sender, player);
        } catch (Throwable t) {
            plugin.getLogger().fine("发送 ArcartX setting 失败: " + unwrap(t));
        }
    }

    public void unregisterKey(String id) {
        if (!available) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object keyRegistry = apiClass.getMethod("getKeyBindRegistry").invoke(null);
            keyRegistry.getClass()
                    .getMethod("unregisterClientKeyBind", String.class)
                    .invoke(keyRegistry, id);
        } catch (Throwable t) {
            plugin.getLogger().fine("注销 ArcartX 按键 '" + id + "' 失败: " + unwrap(t));
        }
    }

    /** 动态代理回调需要正确的返回值（Kotlin Unit 或是 void）。 */
    private static Object kotlinUnit(Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        try {
            return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void open(Player player, String id) {
        if (!available) {
            return;
        }
        try {
            mOpen.invoke(registry, player, id);
        } catch (Throwable t) {
            plugin.getLogger().fine("打开 ArcartX UI 失败: " + unwrap(t));
        }
    }

    public void close(Player player, String id) {
        if (!available) {
            return;
        }
        try {
            mClose.invoke(registry, player, id);
        } catch (Throwable t) {
            plugin.getLogger().fine("关闭 ArcartX UI 失败: " + unwrap(t));
        }
    }

    public void sendPacket(Player player, String id, String handler, Object data) {
        if (!available) {
            return;
        }
        try {
            mSendPacket.invoke(registry, player, id, handler, data);
        } catch (Throwable t) {
            plugin.getLogger().fine("向 ArcartX UI 发包失败: " + unwrap(t));
        }
    }

    public void run(Player player, String id, String code) {
        if (!available) {
            return;
        }
        try {
            mRun.invoke(registry, player, id, code);
        } catch (Throwable t) {
            plugin.getLogger().fine("在 ArcartX UI 执行脚本失败: " + unwrap(t));
        }
    }

    /** 通过 ArcartX 的「服务端变量」把值塞给客户端（Aria 里可读）。 */
    public void sendVariable(Player player, String name, Object value) {
        if (!available) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object sender = apiClass.getMethod("getNetworkSender").invoke(null);
            sender.getClass()
                    .getMethod("sendServerVariable", Player.class, String.class, Object.class)
                    .invoke(sender, player, name, value);
        } catch (Throwable t) {
            plugin.getLogger().fine("发送 ArcartX 变量失败: " + unwrap(t));
        }
    }

    public void sendVariables(Player player, Map<String, Object> vars) {
        if (!available) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI");
            Object sender = apiClass.getMethod("getNetworkSender").invoke(null);
            sender.getClass()
                    .getMethod("sendMultipleServerVariable", Player.class, Map.class)
                    .invoke(sender, player, vars);
        } catch (Throwable t) {
            plugin.getLogger().fine("批量发送 ArcartX 变量失败: " + unwrap(t));
        }
    }

    private static String unwrap(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }
}
