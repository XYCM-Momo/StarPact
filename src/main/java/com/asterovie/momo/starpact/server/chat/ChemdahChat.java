package com.asterovie.momo.starpact.server.chat;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chemdah 对话的数据读写层（方案 A 的核心）。
 *
 * <p>全程反射，不 import Chemdah 类（Chemdah 缺席时不能拖垮插件）。
 *
 * <p>拿到的数据：
 * <ul>
 *   <li>NPC 名 —— {@code Session.getTitle()}</li>
 *   <li>台词 —— {@code Session.getNpcSide()}</li>
 *   <li>选项 —— {@code Session.getPlayerReplyForDisplay()}</li>
 * </ul>
 *
 * <p>点击某个选项时执行该选项的 Kether action（{@code PlayerReply.getAction()}），
 * 其中通常包含 {@code conversation goto ...}，执行后对话自然推进。
 */
public final class ChemdahChat {

    /** 一个选项的快照。 */
    public record Reply(String text, List<String> actions) {
    }

    /** 一次对话的快照。 */
    public record Snapshot(String title, List<String> npcLines, List<Reply> replies) {
        public boolean isEmpty() {
            return title.isEmpty() && npcLines.isEmpty() && replies.isEmpty();
        }
    }

    private ChemdahChat() {
    }

    /** 取玩家当前的对话会话；没有返回 null。 */
    /**
     * 缓存的反射入口。
     *
     * <p>轮询每 100ms 跑一次，每次都 {@code Class.forName} + {@code getMethod}
     * 开销很大（这几步都要遍历方法表）。这里只查一次，之后复用。
     */
    private static Class<?> mgrCls;
    private static Object mgrInstance;
    private static java.lang.reflect.Method getSessionsMethod;
    private static boolean reflected;

    private static void initReflection(StarPactPlugin plugin) {
        if (reflected) {
            return;
        }
        reflected = true;
        try {
            ClassLoader cl = plugin.getClass().getClassLoader();
            mgrCls = Class.forName(
                    "ink.ptms.chemdah.core.conversation.ConversationManager", false, cl);
            mgrInstance = mgrCls.getField("INSTANCE").get(null);
            getSessionsMethod = mgrCls.getMethod("getSessions");
        } catch (Throwable t) {
            mgrCls = null;
        }
    }

    public static Object sessionOf(StarPactPlugin plugin, Player player) {
        initReflection(plugin);
        if (mgrCls == null) {
            return null;
        }
        try {
            Map<?, ?> sessions = (Map<?, ?>) getSessionsMethod.invoke(mgrInstance);
            if (sessions == null || sessions.isEmpty()) {
                return null;
            }
            Object byName = sessions.get(player.getName());
            if (byName != null) {
                return byName;
            }
            Object byUuid = sessions.get(player.getUniqueId().toString());
            if (byUuid != null) {
                return byUuid;
            }
            for (Object v : sessions.values()) {
                if (player.equals(call(v, "getPlayer"))) {
                    return v;
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读取当前对话的快照。 */
    public static Snapshot snapshot(StarPactPlugin plugin, Player player) {
        Object session = sessionOf(plugin, player);
        if (session == null) {
            return new Snapshot("", List.of(), List.of());
        }
        String title = strip(call(session, "getTitle"));
        List<String> lines = new java.util.ArrayList<>();
        Object npc = call(session, "getNpcSide");
        if (npc instanceof List<?> l) {
            for (Object o : l) {
                String s = strip(o);
                if (!s.isBlank()) {
                    lines.add(s);
                }
            }
        }
        List<Reply> replies = new java.util.ArrayList<>();
        Object rs = call(session, "getPlayerReplyForDisplay");
        if (rs instanceof List<?> l) {
            for (Object r : l) {
                String text = strip(call(r, "getText"));
                List<String> actions = new java.util.ArrayList<>();
                Object act = call(r, "getAction");
                if (act instanceof List<?> al) {
                    for (Object a : al) {
                        if (a != null) {
                            actions.add(String.valueOf(a));
                        }
                    }
                }
                replies.add(new Reply(text, actions));
            }
        }
        return new Snapshot(title, lines, replies);
    }

    /**
     * 执行某个选项：先选中它，再跑它的 Kether action。
     *
     * @return 是否至少执行了一步
     */
    public static boolean pick(StarPactPlugin plugin, Player player, int index) {
        Object session = sessionOf(plugin, player);
        if (session == null) {
            if (plugin.spConfig().debug()) {
                plugin.getLogger().info("[对话] pick 失败：没有会话");
            }
            return false;
        }
        Object rs = call(session, "getPlayerReplyForDisplay");
        if (!(rs instanceof List<?> list) || index < 0 || index >= list.size()) {
            if (plugin.spConfig().debug()) {
                plugin.getLogger().info("[对话] pick 越界: index=" + index
                        + " size=" + (rs instanceof List<?> l2 ? l2.size() : -1));
            }
            return false;
        }
        Object reply = list.get(index);
        // 1) 标记选中
        try {
            session.getClass().getMethod("setPlayerSide", reply.getClass()).invoke(session, reply);
            session.getClass().getMethod("setSelected", boolean.class).invoke(session, true);
        } catch (Throwable ignored) {
            // 标记失败不影响后续执行
        }
        // 2) 跑 action
        Object act = call(reply, "getAction");
        boolean ran = false;
        if (act instanceof List<?> al) {
            for (Object a : al) {
                if (a == null) {
                    continue;
                }
                String script = String.valueOf(a).trim();
                if (script.isEmpty()) {
                    continue;
                }
                if (plugin.spConfig().debug()) {
                    plugin.getLogger().info("[对话] 执行 action: " + script);
                }
                // 「goto xxx」这种最简单的情况：直接调 Session.goto 跳转。
                // 走 Kether 的话需要对话上下文，实测 goto 不生效。
                String target = gotoTarget(script);
                if (target != null && gotoConversation(plugin, player, session, target)) {
                    ran = true;
                    continue;
                }
                if (runKether(plugin, player, script)) {
                    ran = true;
                }
            }
        }
        // 3) 兜底：没 action 就置 next 让 Chemdah 自己推进
        if (!ran) {
            try {
                session.getClass().getMethod("setNext", boolean.class).invoke(session, true);
                if (plugin.spConfig().debug()) {
                    plugin.getLogger().info("[对话] 无 action，置 next=true 兜底");
                }
                ran = true;
            } catch (Throwable ignored) {
                // ignore
            }
        }
        // 4) 把新节点的台词/选项写进会话
        if (ran) {
            refreshSessionData(plugin, player, session);
        }
        return ran;
    }

    /**
     * 把当前节点的台词与选项写进会话。
     *
     * <p><b>不能调 {@code Theme.onDisplay}</b>：那是「打开界面」的入口，
     * 会调用 {@code createDisplay} 重建界面，传错参数时 Chemdah 直接把会话判为结束
     * —— 实测点了选项之后面板整个消失、会话变空。
     *
     * <p>改成直接把数据写进 Session 的两个字段：
     * 台词来自 {@code Conversation.getNpcSide()}，
     * 选项来自 {@code Conversation.getPlayerSide().checked(session)}。
     */
    private static void refreshSessionData(StarPactPlugin plugin, Player player, Object session) {
        try {
            Object conv = call(session, "getConversation");
            if (conv == null) {
                return;
            }
            // 台词
            Object npc = call(conv, "getNpcSide");
            if (npc instanceof List<?> l) {
                java.util.ArrayList<String> lines = new java.util.ArrayList<>();
                for (Object o : l) {
                    if (o != null) {
                        lines.add(String.valueOf(o));
                    }
                }
                session.getClass().getMethod("setNpcSide", java.util.ArrayList.class)
                        .invoke(session, lines);
            }
            // 选项
            //
            // ⚠️ 不能用 PlayerSide.checked()：实测跳转后它一直返回空列表，
            // 而同一时刻 PlayerSide.getReply() 里明明有 1 条回复。
            // 所以直接取原始列表（reply 是配置里写死的选项，不经条件过滤）。
            Object ps = call(conv, "getPlayerSide");
            if (ps != null) {
                Object raw = call(ps, "getReply");
                if (raw instanceof List<?> rl) {
                    java.util.ArrayList<Object> out = new java.util.ArrayList<>();
                    for (Object r : rl) {
                        if (r != null) {
                            out.add(r);
                        }
                    }
                    if (out.isEmpty()) {
                        if (plugin.spConfig().debug()) {
                            plugin.getLogger().info("[对话] 新节点没有选项");
                        }
                        return;
                    }
                    try {
                        session.getClass().getMethod("setPlayerReplyForDisplay",
                                        java.util.ArrayList.class)
                                .invoke(session, out);
                        if (plugin.spConfig().debug()) {
                            plugin.getLogger().info("[对话] 会话已刷新，选项数=" + out.size());
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[对话] 写选项失败: " + t);
                    }
                }
            }
            if (plugin.spConfig().debug()) {
                plugin.getLogger().info("[对话] 已刷新会话数据");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[对话] 刷新会话失败: " + t);
        }
    }

    /** 从 action 脚本里取出 goto 目标；不是 goto 返回 null。 */
    private static String gotoTarget(String script) {
        String s = script.trim();
        if (!s.startsWith("goto ")) {
            return null;
        }
        String id = s.substring(5).trim();
        // 去掉可能的引号
        if (id.length() >= 2 && (id.startsWith("\"") && id.endsWith("\""))) {
            id = id.substring(1, id.length() - 1);
        }
        return id.isEmpty() ? null : id;
    }

    /**
     * 直接跳到另一个对话节点。
     *
     * <p>用 {@code ChemdahAPI.getConversation(String)} 按 id 取节点 ——
     * 这是 Chemdah 自己的对话表（{@code HashMap<String, Conversation>}），
     * 比 {@code ConversationManager.getConversation(player, namespace, ...)}
     * 简单可靠（后者还要 namespace，实测取不到）。
     */
    private static boolean gotoConversation(StarPactPlugin plugin, Player player,
                                            Object session, String conversationId) {
        try {
            ClassLoader cl = plugin.getClass().getClassLoader();
            Class<?> apiCls = Class.forName("ink.ptms.chemdah.api.ChemdahAPI", false, cl);
            Object api = apiCls.getField("INSTANCE").get(null);
            Object conv = apiCls.getMethod("getConversation", String.class)
                    .invoke(api, conversationId);
            if (conv == null) {
                if (plugin.spConfig().debug()) {
                    plugin.getLogger().info("[对话] 找不到对话节点: " + conversationId);
                }
                return false;
            }
            session.getClass().getMethod("goto", conv.getClass(), Map.class)
                    .invoke(session, conv, new HashMap<String, Object>());
            if (plugin.spConfig().debug()) {
                plugin.getLogger().info("[对话] 已跳转到: " + conversationId);
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[对话] goto 失败: " + conversationId + " -> " + t);
            return false;
        }
    }

    /** 通过 ChemdahAPI 执行一段 Kether 脚本。 */
    private static boolean runKether(StarPactPlugin plugin, Player player, String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        try {
            ClassLoader cl = plugin.getClass().getClassLoader();
            Class<?> apiCls = Class.forName("ink.ptms.chemdah.api.ChemdahAPI", false, cl);
            Object api = apiCls.getField("INSTANCE").get(null);
            Method m = apiCls.getMethod("invokeKether", String.class, Player.class, Map.class);
            m.invoke(api, script, player, new HashMap<String, Object>());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[对话] 执行 Kether 失败: " + script + " -> " + t);
            return false;
        }
    }

    private static Object call(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String strip(Object o) {
        if (o == null) {
            return "";
        }
        String s = org.bukkit.ChatColor.stripColor(String.valueOf(o));
        return s == null ? "" : s.trim();
    }
}
