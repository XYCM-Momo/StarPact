package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渲染调度：决定用 AX HUD 还是降级方案，并做服务端→客户端的分发。
 *
 * <p>一个玩家只会走一条路径：
 * <ul>
 *   <li>ArcartX 可用 → AX HUD（打开过一次后保持）</li>
 *   <li>否则 → BossBar（可选再叠一层记分板）</li>
 * </ul>
 * 所以不需要「先清 AX 再画 BossBar」的互斥处理，切换时清掉另一条即可。
 */
public final class HudService {

    private final StarPactPlugin plugin;
    private final HudRenderer axRenderer;
    private final BossBarFallback bossBar;
    private final ScoreboardFallback scoreboard;

    /** 已经为这个玩家打开过 AX HUD */
    private final Set<UUID> opened = ConcurrentHashMap.newKeySet();
    /**
     * 玩家进服时刻，用于「进服后宽容期」判断。
     *
     * <p>客户端刚连上时，ArcartX 的 UI 定义还在下发，这时候 {@code open()}
     * 会返回成功但实际被吞掉。宽容期内每次刷新都重开一遍，代价可忽略
     * （AX 对已打开的 UI 会忽略重复 open），换来「HUD 一定会显示」。
     */
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();

    /** 宽容期时长：30 秒，足够覆盖加载慢的客户端。 */
    private static final long JOIN_GRACE_MS = 30_000L;

    /** 进服宽容期内允许的额外重开次数（不含首次）。 */
    private static final int JOIN_REOPEN_TIMES = 3;

    /** 每个玩家已经用掉的额外重开次数。 */
    private final Map<UUID, Integer> reopenUsed = new ConcurrentHashMap<>();
    /** 上次推送时的任务 ID 集合，用于判断「是否该播放入场动画」。 */
    private final Map<UUID, String> lastAnimKey = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 每个玩家「上一次刷新时各任务的进度」：任务 ID -> 进度值。
     *
     * <p>用来判断有没有**新接取**的任务。两种信号：
     * <ol>
     *   <li>出现了上次没有的 ID —— 全新任务</li>
     *   <li>ID 相同但进度从非 0 回到 0 —— <b>完成/放弃后又重新接取</b>
     *       （同一任务 ID 重复接取时只有这个信号能识别出来）</li>
     * </ol>
     *
     * <p>这样才能实现：「接新任务自动追踪，手动取消后不被反复追回」。
     */
    private final Map<UUID, Map<String, Long>> lastKnownQuests = new ConcurrentHashMap<>();

    public HudService(StarPactPlugin plugin) {
        this.plugin = plugin;
        this.axRenderer = new HudRenderer(plugin);
        this.bossBar = new BossBarFallback(plugin);
        this.scoreboard = new ScoreboardFallback(plugin);
    }

    public HudRenderer axRenderer() {
        return axRenderer;
    }

    /**
     * 刷新一个玩家的 HUD。
     *
     * @param force 忽略节流
     */
    public void refresh(Player player, boolean force) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // HUD 只显示「玩家在面板里点了追踪」的那一个任务。
        // 没追踪任何任务时 tracks 为空，HUD 不渲染（相当于不打扰玩家）。
        // ⚠️ 必须读「全部进行中任务」，不能用 maxTracks 截断。
        // HUD 只显示一个，但追踪判定需要看到完整列表——
        // 否则玩家接过 4 个以上任务时，排在第 4 位之后的目标会被当成「已失效」清掉，
        // 表现就是「点了追踪按钮 HUD 也不出来」。
        List<TrackEntry> all = plugin.chemdah().readTracks(player, 64);

        // ── 追踪规则 ────────────────────────────────────────────
        // 1) 追踪的任务不在了（完成/放弃）→ 清掉追踪
        // 2) 出现了「上次没见过的任务」= 接了新任务 → 追踪这条新的
        // 3) 其余情况（含玩家手动取消）→ 保持原样，不自动追回
        UUID uid = player.getUniqueId();
        String trackedId = plugin.prefs().of(uid).trackedId;

        // 规则 1：追踪目标失效就清掉
        if (trackedId != null && !trackedId.isBlank()) {
            final String checkId = trackedId;
            boolean stillThere = all.stream().anyMatch(t -> checkId.equals(t.questId()));
            if (!stillThere) {
                plugin.prefs().setTracked(uid, null);
                trackedId = null;
            }
        }

        // 规则 2：找出「新接取」的任务
        // 用「任务 ID -> 接取时间」判断：同一任务重新接取时 startTime 一定变，
        // 而 id 不变、进度也可能一直是 0（实测），只有时间是可靠的。
        Map<String, Long> nowStart = new java.util.LinkedHashMap<>();
        for (TrackEntry e : all) {
            nowStart.merge(e.questId(), e.startTime(), Math::max);
        }
        Map<String, Long> before = lastKnownQuests.get(uid);
        // before == null 说明这是「进服后第一次刷新」。
        // 此时不能靠「有没有新任务」来判定，但要恢复上次追踪的那个任务 ——
        // 玩家上次下线时正在追踪 A，重新进服应该继续追踪 A 并把导航重新下发，
        // 否则表现就是「有时候进服没有自动导航」。
        boolean firstSeen = before == null;

        if (firstSeen) {
            boolean restored = trackedId != null && !trackedId.isBlank();
            if (restored) {
                // 优先：恢复上次追踪的目标，并重放它的追踪指令（导航等）
                plugin.panel().runTrackCommands(player, trackedId);
            } else if (!all.isEmpty()) {
                // 没有追踪记录：自动追踪「最新接取」的那个任务。
                // all 已按接取时间升序排列，最后一条就是最新的。
                TrackEntry pick = all.get(all.size() - 1);
                trackedId = pick.questId();
                plugin.prefs().setTracked(uid, trackedId);
                plugin.panel().runTrackCommands(player, trackedId);
            }
        }

        if (!firstSeen) {
            String freshId = null;
            for (TrackEntry e : all) {
                Long prev = before.get(e.questId());
                // 没见过的 ID，或同一 ID 的接取时间变了 => 新接取
                if (prev == null || !prev.equals(e.startTime())) {
                    // 不 break：一次接了多个任务时取「最后一个」。
                    // all 已按接取时间升序排列，最后一个就是最新接下的。
                    freshId = e.questId();
                }
            }
            if (freshId != null) {
                trackedId = freshId;
                plugin.prefs().setTracked(uid, trackedId);
                // 与手动点「追踪」行为一致：触发这个任务的追踪指令（导航等）
                plugin.panel().runTrackCommands(player, freshId);
            }
        }
        lastKnownQuests.put(uid, nowStart);

        final String finalTracked = trackedId;
        List<TrackEntry> tracks;
        if (finalTracked == null || finalTracked.isBlank()) {
            tracks = List.of();
        } else {
            tracks = all.stream()
                    .filter(t -> finalTracked.equals(t.questId()))
                    .limit(1)
                    .toList();
        }

        if (plugin.ax().available()) {
            // 确保 UI 已打开（defaultOpen=false，需要服务端主动 open）。
            //
            // ax().open() 返回 true 只代表「已下发」，不代表客户端 UI 真的挂上了 ——
            // 进服那几秒客户端还在加载资源包/UI 定义，这时候 open 会被吞掉，
            // 而服务端如果记了 opened 就再也不重试，表现就是「进服 HUD 时有时无」。
            // 每 2 秒的轮询都重开一次，代价极小（AX 内部对已打开的 UI 会直接忽略），
            // 换来的是「无论如何最终一定会显示」。
            if (plugin.prefs().of(player.getUniqueId()).visible && !tracks.isEmpty()) {
                // 进服宽容期内「有限次重开」：客户端刚连上时 UI 定义还没下发完，
                // open() 会返回 true 但实际被吞掉。
                //
                // ⚠️ 不能每 2 秒无限重开 —— 实测那样 HUD 会「显示一下就消失」，
                // 因为重复 open 会让 AX 重建 UI。只在记账次数还没用完时补开几次，
                // 补完就回归「只开一次」的正常逻辑。
                boolean needOpen = force || !opened.contains(uid);
                if (!needOpen && inJoinGrace(uid) && reopenBudget(uid) > 0) {
                    needOpen = true;
                    useReopenBudget(uid);
                }
                if (needOpen) {
                    boolean ok = plugin.ax().open(player, HudRenderer.UI_ID);
                    if (ok) {
                        opened.add(uid);
                    } else {
                        opened.remove(uid);
                    }
                }
            } else if (tracks.isEmpty() && opened.contains(uid)) {
                // 没有任务可显示：直接关掉 UI（不做淡出，任务完成了就该干脆利落地消失）
                plugin.ax().close(player, HudRenderer.UI_ID);
                opened.remove(player.getUniqueId());
            }
            // 任务集合变了（接了新任务 / 换了追踪目标）就播一次入场动画
            String animKey = tracks.isEmpty() ? "" : tracks.get(0).questId()
                    + ":" + tracks.get(0).startTime();
            String prevKey = lastAnimKey.get(player.getUniqueId());
            // 只有「变成有任务」时才播入场动画。
            // 任务消失（tracks 为空）不该播 —— 否则完成任务的瞬间会插一段淡入，
            // 紧接着重新接取又播一次，两段动画的帧交错执行，看起来像循环闪烁。
            boolean wantAnim = !tracks.isEmpty() && !animKey.equals(prevKey == null ? "" : prevKey);
            lastAnimKey.put(player.getUniqueId(), animKey);
            if (wantAnim) {
                // 接取新任务：播放一声清脆提示音 + 面板滑入
                try {
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 0.35f, 2.0f);
                } catch (Throwable ignored) {
                    // 音效失败不影响功能
                }
            }

            if (wantAnim) {
                pushFadeIn(player, tracks);
            } else {
                axRenderer.push(player, tracks, force);
            }
            // 切到 AX 时清掉降级残留
            bossBar.hide(player);
            if (plugin.spConfig().fallbackToScoreboard()) {
                scoreboard.clear(player);
            }
        } else if (plugin.spConfig().fallbackToBossbar()) {
            // 只有「AX 不可用」且「配置允许降级」时才画 BossBar
            bossBar.render(player, tracks);
            if (plugin.spConfig().fallbackToScoreboard()) {
                scoreboard.render(player, tracks);
            }
        } else {
            bossBar.hide(player);
            scoreboard.clear(player);
        }
    }

    /**
     * hud_ready 包的节流：每玩家最多每 3 秒受理一次。
     *
     * <p>历史上这个包曾因「UI open 触发器在每次重绘时都触发」而形成死循环
     * （发 ready → 推数据 → 重绘 → 再发 ready），把 HUD 卡成狂闪。
     * 现在 HUD 侧已不再发这个包，这里保留兜底节流。
     */
    public boolean allowReadyPing(java.util.UUID id) {
        long now = System.currentTimeMillis();
        Long last = readyPingAt.get(id);
        if (last != null && now - last < 3000L) {
            return false;
        }
        readyPingAt.put(id, now);
        return true;
    }

    private final java.util.Map<java.util.UUID, Long> readyPingAt =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 插件重载后清空「已打开」记录。
     *
     * <p>reload 会 unregister 再 register UI，客户端手里的旧 UI 已经失效，
     * 但服务端还记着 opened —— 于是再也不发 open，HUD 就消失了。
     */
    public void resetOpened() {
        opened.clear();
    }

    /** 记录进服时刻，开启宽容期。 */
    public void markJoined(Player player) {
        if (player != null) {
            joinedAt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** 是否处在「进服宽容期」内 —— 此时每次刷新都重开 UI。 */
    private boolean inJoinGrace(UUID uid) {
        Long at = joinedAt.get(uid);
        if (at == null) {
            return false;
        }
        if (System.currentTimeMillis() - at > JOIN_GRACE_MS) {
            joinedAt.remove(uid);
            return false;
        }
        return true;
    }

    /** 剩余可用的额外重开次数。 */
    private int reopenBudget(UUID uid) {
        return JOIN_REOPEN_TIMES - reopenUsed.getOrDefault(uid, 0);
    }

    /** 消耗一次额外重开额度。 */
    private void useReopenBudget(UUID uid) {
        reopenUsed.merge(uid, 1, Integer::sum);
    }

    /** 玩家显隐切换。 */
    public void setVisible(Player player, boolean visible) {
        plugin.prefs().setVisible(player.getUniqueId(), visible);
        if (!visible) {
            plugin.ax().close(player, HudRenderer.UI_ID);
            opened.remove(player.getUniqueId());
            bossBar.hide(player);
            if (plugin.spConfig().fallbackToScoreboard()) {
                scoreboard.clear(player);
            }
        } else {
            refresh(player, true);
        }
    }

    /** 玩家改完位置后立刻重推一次。 */
    public void applyPosition(Player player) {
        refresh(player, true);
    }

    /**
     * 入场淡入：只发一个 anim=1 的包，动画完全由客户端按时间戳驱动。
     *
     * <p>为什么不在服务端分帧：Bukkit 调度最小 1 tick = 50ms，
     * 而两个包间隔 50ms 时，客户端 16ms 的插值几乎抓不到低 alpha 的瞬间。
     * 交给客户端按 System 时间插值，才能真正做到 60fps。
     */
    private void pushFadeIn(Player player, List<TrackEntry> tracks) {
        axRenderer.push(player, tracks, true, true, 1.0);
    }
    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        opened.remove(id);
        // 清掉「上次见过的任务集合」，下次进服重新建立基线
        lastKnownQuests.remove(id);
        lastAnimKey.remove(id);
        axRenderer.forget(id);
        bossBar.forget(id);
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.ax().close(p, HudRenderer.UI_ID);
            bossBar.hide(p);
            if (plugin.spConfig().fallbackToScoreboard()) {
                scoreboard.clear(p);
            }
        }
        opened.clear();
    }

    public void shutdown() {
        bossBar.clear();
    }
}
