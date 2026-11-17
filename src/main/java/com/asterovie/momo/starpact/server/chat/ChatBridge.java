package com.asterovie.momo.starpact.server.chat;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用 StarPact 自己的 AX 界面展示 Chemdah 的对话。
 *
 * <p><b>为什么不用「盖箱子」的路子</b>：Chemdah 的箱子对话是虚拟库存
 * （{@code RemoteInventory}），点击只认客户端原始 NMS 包；
 * 面板盖上去之后客户的点击被 AX 吃掉，服务端无论派发什么 Bukkit 事件
 * 它都收不到 —— 实测走了很多弯路。
 *
 * <p><b>现在的做法</b>：完全不走箱子。
 * <ol>
 *   <li>定期检查玩家的 Chemdah 对话会话（{@link ChemdahChat}）</li>
 *   <li>会话出现 → 打开我们的 AX 面板 + 推送台词与选项</li>
 *   <li>玩家点选项 → 执行该选项的 Kether action（含 conversation goto）</li>
 *   <li>会话内容变化 → 自动刷新面板；会话结束 → 关面板</li>
 * </ol>
 *
 * <p>这样点击逻辑 100% 在自己手里，不依赖 Chemdah 的界面实现。
 */
public class ChatBridge implements Listener {

    /** AX 对话 UI 的 ID。 */
    public static final String UI_ID = "starpact:chat_panel";

    /** 轮询间隔（tick）。20 = 1 秒。 */
    private static final long POLL_TICKS = 10L;

    private final StarPactPlugin plugin;

    /** 面板当前是否已为这个玩家打开。 */
    private final Map<UUID, Boolean> showing = new ConcurrentHashMap<>();

    /** 上次推送的内容指纹，用来判断要不要刷新。 */
    private final Map<UUID, String> lastPush = new ConcurrentHashMap<>();

    public ChatBridge(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    /** 启动轮询。 */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, POLL_TICKS, POLL_TICKS);
    }

    /** 每秒检查一次所有在线玩家的对话状态。 */
    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                tickOne(player);
            } catch (Throwable t) {
                plugin.getLogger().warning("[对话] 轮询出错: " + t);
            }
        }
    }

    private void tickOne(Player player) {
        ChemdahChat.Snapshot snap = ChemdahChat.snapshot(plugin, player);
        UUID uid = player.getUniqueId();

        // 没会话：关面板（只在我们确实推过数据时才关，避免误关别的界面）
        if (snap.isEmpty()) {
            if (lastPush.remove(uid) != null) {
                plugin.ax().close(player, UI_ID);
                showing.remove(uid);
            }
            return;
        }
        showing.put(uid, true);

        // 有会话：界面由 AX 的 ui.match 自动接管（匹配箱子标题），
        // 这里只负责推数据。自己再 open 一次会和 match 打架。
        String fp = fingerprint(snap);
        if (!fp.equals(lastPush.get(uid))) {
            push(player, snap);
        }
    }

    /** 把快照推给 AX 面板。 */
    public void push(Player player, ChemdahChat.Snapshot snap) {
        if (snap == null || snap.isEmpty()) {
            return;
        }
        Map<String, Object> p = new HashMap<>();
        p.put("show", 1);
        // UI 里所有内容（台词/名字/选项）都靠 ready 才显示，
        // 少了这个键整个面板就是全透明的。
        p.put("ready", 1);
        p.put("npc_name", snap.title().isEmpty() ? "\u00a7r" : "\u00a7e" + snap.title());
        p.put("npc_text", snap.npcLines().isEmpty()
                ? "\u00a77..." : "\u00a7f" + String.join("\n", snap.npcLines()));
        for (int i = 0; i < 6; i++) {
            boolean has = i < snap.replies().size();
            p.put("v" + i, has ? 1 : 0);
            p.put("t" + i, has ? "\u00a7f" + snap.replies().get(i).text() : "\u00a7r");
        }
        plugin.ax().sendPacket(player, UI_ID, "sync", p);
        lastPush.put(player.getUniqueId(), fingerprint(snap));
    }

    /** AX 面板就绪时补推一次。 */
    public void pushAgain(Player player) {
        push(player, ChemdahChat.snapshot(plugin, player));
    }

    /**
     * 玩家点了第 index 个选项。
     */
    public void onPick(Player player, int index) {
        // 点击反馈音：和面板其它按钮保持同一个音色
        player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        boolean ok = ChemdahChat.pick(plugin, player, index);
        if (plugin.spConfig().debug()) {
            plugin.getLogger().info("[对话] 点击选项 #" + index
                    + " (" + player.getName() + ") 结果=" + ok);
        }
        // 稍等一下让 Chemdah 处理完，再刷新面板
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ChemdahChat.Snapshot snap = ChemdahChat.snapshot(plugin, player);
            if (snap.isEmpty()) {
                plugin.ax().close(player, UI_ID);
                showing.remove(player.getUniqueId());
                lastPush.remove(player.getUniqueId());
            } else {
                push(player, snap);
            }
        }, 3L);
    }

    /** 关掉对话面板（玩家按 ESC 时由 AX 侧触发）。 */
    public void closePanel(Player player) {
        plugin.ax().close(player, UI_ID);
        showing.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        showing.remove(uid);
        lastPush.remove(uid);
    }

    private String fingerprint(ChemdahChat.Snapshot snap) {
        StringBuilder sb = new StringBuilder(snap.title());
        for (String s : snap.npcLines()) {
            sb.append('|').append(s);
        }
        for (ChemdahChat.Reply r : snap.replies()) {
            sb.append('#').append(r.text());
        }
        return sb.toString();
    }
}
