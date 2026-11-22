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

    /**
     * 轮询间隔（tick）。20 = 1 秒。
     *
     * <p>2 tick（100ms）：面板由 ui.match 一打开就是空的，
     * 轮询太慢会让玩家盯着空面板等 —— 实测 0.5 秒就能感觉到卡顿。
     * 会话读取很轻（反射几个 getter + 一个 HashMap 查找），可以跑勤一点。
     */
    private static final long POLL_TICKS = 2L;

    private final StarPactPlugin plugin;

    /** 面板当前是否已为这个玩家打开。 */
    private final Map<UUID, Boolean> showing = new ConcurrentHashMap<>();

    /** 上次推送的内容指纹，用来判断要不要刷新。 */
    private final Map<UUID, String> lastPush = new ConcurrentHashMap<>();

    /**
     * 打字机进度：玩家 → 已显示的可见字符数。
     *
     * <p>为什么放在服务端：AX 的 {@code limit} 属性在 {@code Texture} / {@code Text}
     * 控件上实测都不生效（设成 0 文字照样完整显示），Aria 里也没找到可靠的跨
     * {@code sync} / {@code tasks} 共享状态的办法。干脆由服务端逐帧下发截断后的文本，
     * 客户端只负责渲染 —— 这个一定有效。
     */
    private final Map<UUID, Integer> typed = new ConcurrentHashMap<>();

    /**
     * 打字机节奏：玩家 → 下一个字应该出现的时刻（毫秒时间戳）。
     *
     * <p>每个字都单独定一个时刻，并在基础间隔上叠随机抖动，
     * 这样读起来像真人说话，而不是等间隔的机械"哒哒哒"。
     */
    private final Map<UUID, Long> nextCharAt = new ConcurrentHashMap<>();

    /** 抖动用的随机源。 */
    private final java.util.Random random = new java.util.Random();

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
            // 打字进度必须一起清掉：否则下次打开同一段对话时，
            // 指纹和上次一样 → 判定为"没变化" → 直接沿用旧进度，
            // 一开就是完整台词，看不到打字过程。
            typed.remove(uid);
            nextCharAt.remove(uid);
            return;
        }
        showing.put(uid, true);

        // 有会话：界面由 AX 的 ui.match 自动接管（匹配箱子标题），
        // 这里只负责推数据。自己再 open 一次会和 match 打架。
        String fp = fingerprint(snap);
        boolean changed = !fp.equals(lastPush.get(uid));

        if (changed) {
            // 内容变了（新一段对话 / 选了选项）→ 打字进度归零，从头打
            typed.put(uid, 0);
            nextCharAt.remove(uid);
            push(player, snap);
            return;
        }

        // 内容没变：如果还在打字，就继续推进
        int total = totalVisible(snap.npcLines());
        int cur = typed.getOrDefault(uid, 0);
        if (cur < total) {
            // 速度来自 config.yml 的 chat.type-speed（每秒几个字）。
            //
            // 为什么不用"每拍推进 speed/10 个字"：那样每个字的间隔完全相等，
            // 观感是机械的"哒哒哒"。这里改成每个字都定一个「下次出现的时刻」，
            // 并在基础间隔上叠一点随机抖动，读起来更像真人说话。
            long now = System.currentTimeMillis();
            long nextAt = nextCharAt.getOrDefault(uid, 0L);
            if (now < nextAt) {
                return;   // 还没到下一个字的时间
            }
            int speed = plugin.spConfig().chatTypeSpeed();
            long base = Math.max(16L, 1000L / speed);
            // ±35% 的抖动：基础间隔的正负三分之一
            long jitter = (long) (base * 0.35);
            long gap = base - jitter + (long) (random.nextDouble() * jitter * 2);
            nextCharAt.put(uid, now + gap);

            typed.put(uid, cur + 1);
            push(player, snap);
        }
    }

    /** 把快照推给 AX 面板（按当前打字进度截断台词）。 */
    public void push(Player player, ChemdahChat.Snapshot snap) {
        if (snap == null || snap.isEmpty()) {
            return;
        }
        UUID uid = player.getUniqueId();
        Map<String, Object> p = new HashMap<>();
        p.put("show", 1);
        // UI 里所有内容（台词/名字/选项）都靠 ready 才显示，
        // 少了这个键整个面板就是全透明的。
        p.put("ready", 1);
        p.put("npc_name", snap.title().isEmpty() ? "\u00a7r" : "\u00a7e" + snap.title());

        // 打字机：按已显示字数把台词截断后再下发，客户端只负责渲染。
        List<String> lines = snap.npcLines();
        int budget = typed.getOrDefault(uid, 0);
        boolean typing = budget < totalVisible(lines);
        p.put("typing", typing ? 1 : 0);

        for (int i = 0; i < 5; i++) {
            String shown = "";
            boolean has = false;
            if (i < lines.size()) {
                String full = lines.get(i);
                int avail = budget;
                for (int k = 0; k < i && k < lines.size(); k++) {
                    avail -= visibleLength(lines.get(k));
                }
                if (avail > 0) {
                    shown = truncateVisible(full, avail);
                    has = true;
                }
            }
            p.put("npc_" + (i + 1), has ? "\u00a7f" + shown : "\u00a7r");
            p.put("n" + (i + 1), has ? 1 : 0);
        }
        p.put("npc_text", lines.isEmpty() ? "\u00a77..." : "\u00a7f" + String.join("\n", lines));

        // 居中对齐用的宽度：取「完整台词」的估算宽度，而不是当前已显示的部分。
        // 这样打字过程中算出的左边界始终不变，文字不会跟着字数左右移动。
        int widestEm = 0;
        for (String s : lines) {
            widestEm = Math.max(widestEm, textWidthEm(s));
        }
        p.put("text_em", widestEm);

        for (int i = 0; i < 6; i++) {
            boolean has = i < snap.replies().size();
            p.put("v" + i, has ? 1 : 0);
            p.put("t" + i, has ? "\u00a7f" + snap.replies().get(i).text() : "\u00a7r");
        }
        plugin.ax().sendPacket(player, UI_ID, "sync", p);
        lastPush.put(uid, fingerprint(snap));
    }

    /**
     * 估算一行文字的宽度，单位是「em」（1em = 一个中文字宽 = fontSize）。
     *
     * <p>Aria 和 Java 都拿不到字体度量，所以按字符类别粗略折算：
     * 中日韩文字和全角标点算 1.0，ASCII 算 0.55。
     * 只用来算居中位置，误差一两个字宽肉眼基本看不出来。
     */
    private static int textWidthEm(String s) {
        if (s == null) {
            return 0;
        }
        double em = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\u00a7') {
                i++;              // 跳过颜色码
                continue;
            }
            if (isWide(ch)) {
                em += 1.0;
            } else {
                em += 0.55;
            }
        }
        return (int) Math.ceil(em);
    }

    /** 中日韩文字与全角标点。 */
    private static boolean isWide(char ch) {
        return (ch >= 0x4E00 && ch <= 0x9FFF)      // 汉字
                || (ch >= 0x3000 && ch <= 0x303F)  // 中文标点
                || (ch >= 0xFF00 && ch <= 0xFFEF); // 全角字符
    }

    /** 台词总可见字数。 */
    private static int totalVisible(List<String> lines) {
        int n = 0;
        for (String s : lines) {
            n += visibleLength(s);
        }
        return n;
    }

    /**
     * 截取前 {@code limit} 个可见字符（保留其中的颜色代码）。
     */
    private static String truncateVisible(String s, int limit) {
        if (s == null || limit <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int seen = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\u00a7') {
                sb.append(ch);
                if (i + 1 < s.length()) {
                    sb.append(s.charAt(++i));
                }
                continue;
            }
            if (seen >= limit) {
                break;
            }
            sb.append(ch);
            seen++;
        }
        return sb.toString();
    }

    /** AX 面板就绪时补推一次。 */
    public void pushAgain(Player player) {
        push(player, ChemdahChat.snapshot(plugin, player));
    }

    /**
     * 客户端分辨率变了 → 重推一次数据。
     *
     * <p>UI 的布局全在 {@code packetHandler.sync} 里按 {@code Screen.getWidth()}
     * 实时算，但 sync 只在服务端推数据时才跑。玩家切换分辨率后如果不重推，
     * 坐标就停留在旧尺寸上，界面会错位。
     * UI 侧每 0.5 秒比一次屏幕尺寸，变了就发 {@code chat_resize}。
     */
    public void onResize(Player player) {
        ChemdahChat.Snapshot snap = ChemdahChat.snapshot(plugin, player);
        if (snap.isEmpty()) {
            return;
        }
        // 强制重推：先清掉指纹，让 tickOne 认为内容变了
        lastPush.remove(player.getUniqueId());
        push(player, snap);
    }

    /**
     * 玩家点了台词区域 → 跳过打字动画，立刻显示全文。
     *
     * <p>选项要等打完才出现，所以这个动作不会和选项选择冲突。
     */
    public void onSkip(Player player) {
        UUID uid = player.getUniqueId();
        ChemdahChat.Snapshot snap = ChemdahChat.snapshot(plugin, player);
        if (snap.isEmpty()) {
            return;
        }
        int total = totalVisible(snap.npcLines());
        int cur = typed.getOrDefault(uid, 0);
        if (cur >= total) {
            return;   // 已经打完，什么也不用做
        }
        typed.put(uid, total);
        player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
        if (plugin.spConfig().debug()) {
            plugin.getLogger().info("[对话] 跳过打字 " + cur + "/" + total);
        }
        push(player, snap);
    }

    /**
     * 玩家点了第 index 个选项。
     *
     * <p>选项只在台词打完之后才出现（UI 侧用 typing 字段控制），
     * 所以这里不需要再判断打字状态。
     */
    public void onPick(Player player, int index) {
        UUID uid = player.getUniqueId();
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
            ChemdahChat.Snapshot s2 = ChemdahChat.snapshot(plugin, player);
            if (s2.isEmpty()) {
                plugin.ax().close(player, UI_ID);
                showing.remove(uid);
                lastPush.remove(uid);
                typed.remove(uid);
                nextCharAt.remove(uid);
            } else {
                typed.put(uid, 0);
                nextCharAt.remove(uid);
                push(player, s2);
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

    /**
     * 一行的可见字符数（不含 {@code §} 颜色代码）。
     *
     * <p>AX 的 {@code limit} 属性按可见字符计数，打字机效果要靠它逐字显示，
     * 所以这里算出来的值必须和 AX 的计数口径一致。
     */
    private static int visibleLength(String s) {
        if (s == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\u00a7') {
                i++;          // 跳过 § 和它后面那个格式码
                continue;
            }
            n++;
        }
        return n;
    }

    private String fingerprint(ChemdahChat.Snapshot snap) {        StringBuilder sb = new StringBuilder(snap.title());
        for (String s : snap.npcLines()) {
            sb.append('|').append(s);
        }
        for (ChemdahChat.Reply r : snap.replies()) {
            sb.append('#').append(r.text());
        }
        return sb.toString();
    }
}
