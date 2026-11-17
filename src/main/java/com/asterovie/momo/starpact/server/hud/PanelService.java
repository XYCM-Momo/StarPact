package com.asterovie.momo.starpact.server.hud;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.util.Texts;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务面板的开关与交互。
 *
 * <p>交互链路：
 * <pre>
 * 按 J
 *   → AX simple_key 触发 scripts/星契.yml
 *   → PlayerUtils.dispatchOpCommand(player, "sp panel")
 *   → openPanel()：open UI + 推数据
 *
 * 点图标 / 任务
 *   → UI 里 Aria 执行 Packet.send('starpact:page:xxx' / 'starpact:quest:ID')
 *   → PanelListener 收到 ClientCustomPacketEvent
 *   → 本类处理并重新下发
 * </pre>
 */
public final class PanelService {

    public static final String KEY_ID = "星契任务面板";

    private final StarPactPlugin plugin;
    private final PanelRenderer renderer;

    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();

    public PanelService(StarPactPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new PanelRenderer(plugin);
    }

    public PanelRenderer renderer() {
        return renderer;
    }

    /** 打开面板（按 J 或 /sp panel）。 */
    public void openPanel(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!plugin.ax().available()) {
            player.sendMessage(Texts.colorize(plugin.lang()
                    .getString("panel.no-arcartx", "&c需要安装 ArcartX 客户端才能打开任务面板")));
            return;
        }

        // 不做代码层防抖：按键连发由 ArcartX key_group 的 interval(600ms) 负责拦截。
        // 这里再叠一层会导致「刚关掉面板马上按 J 打不开」。
        UUID uid = player.getUniqueId();


        // 先开奖励容器（AX 的 match 会把它替换成任务面板）
        openRewardContainer(player);

        // 立刻推一次（AX 会缓存，UI 就绪时处理），5 tick 后再推一次兜底
        push(player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                push(player);
            }
        }, 5L);
    }

    /**
     * 把奖励容器的内容刷新成「当前选中任务」的奖励。
     *
     * <p>容器只在打开时创建一次，但选中任务会变，所以每次 push 前要重新填。
     * 物品来源和 {@link PanelRenderer#rewardSlotCount} 必须完全一致，
     * 否则会出现「有格子但没物品」或「有物品但格子被隐藏」。
     */
    private void refreshRewardContainer(Player player) {
        if (!rewardViewers.contains(player.getUniqueId())) {
            return;
        }
        org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof RewardHolder)) {
            return;
        }
        var items = plugin.items();
        if (items == null || plugin.quests() == null) {
            return;
        }
        var sel = renderer.selectedQuest(player);
        if (sel == null) {
            // 没有选中任务时保持原样，不要把已有物品清掉
            return;
        }
        var qc = plugin.quests().get(sel.id());
        if (qc == null) {
            return;
        }
        // 确认有任务之后再清空，避免「选中短暂为空」时把容器掏空
        for (int i = 0; i < top.getSize(); i++) {
            top.setItem(i, null);
        }
        int placed = 0;
        for (var r : qc.rewards()) {
            if (placed >= PanelRenderer.REWARD_SLOT_COUNT) {
                break;
            }
            var stack = items.resolve(r);
            if (stack == null) {
                continue;
            }
            top.setItem(placed, stack);
            placed++;
        }
    }

    /** 奖励预览容器的 holder（用于识别并拦截点击）。 */
    public static final class RewardHolder implements org.bukkit.inventory.InventoryHolder {
        private org.bukkit.inventory.Inventory inventory;

        void bind(org.bukkit.inventory.Inventory inv) {
            this.inventory = inv;
        }

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return inventory;
        }
    }

    /** 奖励容器的固定标题（AX 的 match 靠它命中）。 */
    public static final String REWARD_TITLE = "StarPactRewards";

    /** 奖励最多 9 格（一行箱子槽位）。 */
    

    /** 正在展示奖励容器的玩家。 */
    private final java.util.Set<java.util.UUID> rewardViewers = new java.util.HashSet<>();

    public boolean isRewardViewer(Player player) {
        return rewardViewers.contains(player.getUniqueId());
    }

    /**
     * 打开奖励容器：一个 9 格箱子，里面放当前任务的奖励物品。
     *
     * <p>为什么要箱子：AX 的 Slot 控件只有绑定「真实容器槽位」才能渲染物品，
     * 自定义 UI 里直接推送物品不会被客户端渲染。物品放在真实容器里，
     * 图标和原版 tooltip 都由客户端原生提供。
     *
     * <p>UI 侧用 {@code match} 命中这个容器标题，把原版盒子盖成任务面板；
     * 槽位在界面上的位置仍由 UI 里 Slot 控件的 x/y 决定。
     */
    public void openRewardContainer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        RewardHolder holder = new RewardHolder();
        org.bukkit.inventory.Inventory inv = plugin.getServer()
                .createInventory(holder, PanelRenderer.REWARD_SLOT_COUNT, REWARD_TITLE);
        holder.bind(inv);

        // 按顺序放「能解析成物品」的奖励，解析失败的跳过，不占格子
        var items = plugin.items();
        int placed = 0;
        if (items != null && plugin.quests() != null) {
            java.util.List<com.asterovie.momo.starpact.server.quest.QuestConfig.Reward> rewards =
                    new java.util.ArrayList<>();
            var sel = renderer.selectedQuest(player);
            String preferId = sel == null ? null : sel.id();
            if (preferId != null && plugin.quests().get(preferId) != null) {
                rewards.addAll(plugin.quests().get(preferId).rewards());
            }
            if (rewards.isEmpty()) {
                for (String qid : plugin.quests().ids()) {
                    var qc = plugin.quests().get(qid);
                    if (qc != null && !qc.rewards().isEmpty()) {
                        rewards.addAll(qc.rewards());
                        break;
                    }
                }
            }
            for (var r : rewards) {
                if (placed >= PanelRenderer.REWARD_SLOT_COUNT) {
                    break;
                }
                var stack = items.resolve(r);
                if (stack == null) {
                    continue;
                }
                inv.setItem(placed, stack);
                placed++;
            }
        }

        rewardViewers.add(player.getUniqueId());
        player.openInventory(inv);

    }

    public void onRewardContainerClose(Player player) {
        if (player != null) {
            rewardViewers.remove(player.getUniqueId());
        }
    }

    /**
     * 关掉任务面板。
     *
     * <p>面板是套在「奖励容器」（原版箱子）上的，所以除了关 AX 的 UI，
     * 还要把那个箱子也关掉——否则玩家会看到一个空的箱子界面，
     * 而且箱子的点击拦截会一直生效。
     */
    public void closePanel(Player player) {
        plugin.ax().close(player, PanelRenderer.UI_ID);
        // 关掉承载面板的箱子
        org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof RewardHolder) {
            player.closeInventory();
        }
        onRewardContainerClose(player);
    }

    /** 切页（点顶栏图标）。 */
    public void onPage(Player player, String key) {
        clickSound(player);
        renderer.setPage(player, PanelRenderer.Page.of(key));
        // 切到非设置页时清掉选中，避免选中项不属于当前分类
        if (!"setting".equalsIgnoreCase(key)) {
            renderer.setSelected(player, null);
        }
        push(player);
    }

    /**
     * 「打开 HUD 设置」：关闭任务面板，打开独立的 HUD 设置面板。
     *
     * <p>HUD 设置是**独立 HUD 类型 UI**（starpact:hud_settings），
     * 不是任务面板的一部分 —— 所以关掉任务面板后它还在，
     * 玩家能同时看到 HUD 和设置项。
     */
    public void onHudEdit(Player player) {
        clickSound(player);
        closePanel(player);
        plugin.ax().open(player, PanelRenderer.HUD_SETTINGS_UI_ID);
        pushHudSettings(player);
    }

    /** 关闭 HUD 设置面板。 */
    public void onHudPanelClose(Player player) {
        clickSound(player);
        plugin.ax().close(player, PanelRenderer.HUD_SETTINGS_UI_ID);
    }

    /** 把 HUD 设置面板的数据推给客户端。 */
    public void pushHudSettings(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        com.asterovie.momo.starpact.server.storage.PlayerPrefs.Pref pref =
                plugin.prefs().of(player.getUniqueId());
        java.util.Map<String, Object> p = new java.util.HashMap<>();
        p.put("show", 1);
        p.put("v1", "\u00a7f" + Math.round(pref.scale * 100) + "%");
        p.put("v2", "\u00a7f" + (pref.hudW > 0 ? pref.hudW : plugin.spConfig().width()));
        p.put("align", pref.align == null ? "NONE" : pref.align);
        p.put("yRatio", Math.max(0, Math.min(1, pref.y / 294.0)));
        plugin.ax().sendPacket(player, PanelRenderer.HUD_SETTINGS_UI_ID, "sync", p);
    }
    /** HUD 显隐切换。 */
    public void onToggleHud(Player player) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        plugin.prefs().setVisible(player.getUniqueId(), !pref.visible);
        plugin.hud().refresh(player, true);
        pushHudSettings(player);
    }

    /** HUD 水平吸附：LEFT / RIGHT / NONE。 */
    public void onAlignHud(Player player, String dir) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String d = dir == null ? "" : dir.trim().toUpperCase(java.util.Locale.ROOT);
        if (!d.equals("LEFT") && !d.equals("RIGHT") && !d.equals("NONE")) {
            return;
        }
        clickSound(player);
        plugin.prefs().setAlign(player.getUniqueId(), d);
        plugin.hud().refresh(player, true);
        pushHudSettings(player);
    }

    /** HUD 缩放。delta 为 +-10 表示 10%。 */
    public void onScaleHud(Player player, int delta) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        plugin.prefs().setScale(player.getUniqueId(),
                plugin.spConfig().clampScale(pref.scale + delta / 100.0));
        plugin.hud().refresh(player, true);
        pushHudSettings(player);
    }

    /** HUD 宽度。delta 为 +-40。 */
    public void onHudWidth(Player player, int delta) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        int base = pref.hudW > 0 ? pref.hudW : plugin.spConfig().width();
        plugin.prefs().setHudWidth(player.getUniqueId(), base + delta);
        plugin.hud().refresh(player, true);
        pushHudSettings(player);
    }

    /** HUD 移动。 */
    public void onMoveHud(Player player, int dx, int dy) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        plugin.prefs().setPosition(player.getUniqueId(), pref.x + dx, pref.y + dy);
        plugin.hud().applyPosition(player);
        pushHudSettings(player);
    }

/**
     * 滑块调垂直位置。
     *
     * @param value 0.0 ~ 1.0，映射到屏幕高度的 0% ~ 100%
     */
    public void onHudY(Player player, double value) {
        if (player == null || !player.isOnline()) {
            return;
        }
        double v = Math.max(0, Math.min(1, value));
        var pref = plugin.prefs().of(player.getUniqueId());
        // 上限按 UI 坐标估算：HUD 不能完全沉到屏幕底
        int maxY = Math.max(80, (int) Math.round(392 * 0.75));
        int y = (int) Math.round(maxY * v);
        plugin.prefs().setPosition(player.getUniqueId(), pref.x, y);
        plugin.hud().applyPosition(player);
        pushHudSettings(player);
    }

    /** 滚轮微调垂直位置（每格 8 单位）。 */
    public void onHudYScroll(Player player, double wheel) {
        if (player == null || !player.isOnline() || wheel == 0) {
            return;
        }
        var pref = plugin.prefs().of(player.getUniqueId());
        int delta = wheel > 0 ? -8 : 8;
        int y = Math.max(0, Math.min(300, pref.y + delta));
        plugin.prefs().setPosition(player.getUniqueId(), pref.x, y);
        plugin.hud().applyPosition(player);
        pushHudSettings(player);
    }
    /** HUD 位置重置。 */
    public void onResetHud(Player player) {
        clickSound(player);
        plugin.prefs().resetPosition(player.getUniqueId());
        plugin.hud().applyPosition(player);
        pushHudSettings(player);
    }

    /** 面板整体缩放。delta 为 +-10 表示 10%。 */
    public void onScaleUi(Player player, int delta) {
        clickSound(player);
        var pref = plugin.prefs().of(player.getUniqueId());
        double v = pref.uiScale + delta / 100.0;
        plugin.prefs().setUiScale(player.getUniqueId(), Math.max(0.6, Math.min(1.6, v)));
        push(player);
    }

    /** 面板背景透明度。delta 为 +-10 表示 10%。 */
    public void onPanelAlpha(Player player, int delta) {
        clickSound(player);
        double cur = plugin.prefs().of(player.getUniqueId()).panelAlpha;
        plugin.prefs().setPanelAlpha(player.getUniqueId(), cur + delta / 100.0);
        push(player);
    }

    /** 左侧栏宽度比例。delta 为 +-5 表示 5%。 */
    public void onLeftRatio(Player player, int delta) {
        clickSound(player);
        double cur = plugin.prefs().of(player.getUniqueId()).leftRatio;
        plugin.prefs().setLeftRatio(player.getUniqueId(), cur + delta / 100.0);
        push(player);
    }

    /** 选中左栏某个任务。 */
    /**
     * 详情区滚轮：delta 正数=向下。
     *
     * <p>必须节流 —— 客户端一次滚动会连发好几个 wheel 事件
     * （实测一次滚轮能触发 3 个包），不拦的话偏移量会瞬间被推到底，
     * 表现为「一滚就跳到最后一页，看不到开头」。
     *
     * <p>这里用「每 150ms 最多受理一次」的方式合并突发事件。
     */
    public void onScrollDetail(Player player, int delta) {
        if (delta == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastScrollAt.get(player.getUniqueId());
        if (last != null && now - last < SCROLL_THROTTLE_MS) {
            return;
        }
        lastScrollAt.put(player.getUniqueId(), now);

        renderer.scrollDetail(player, delta);
        push(player);
    }

    /**
     * 点「追踪任务」按钮：把当前选中的任务设为 HUD 追踪目标。
     *
     * <p>再点一次就取消追踪（toggle）。追踪状态存在 PlayerPrefs 里，
     * 重进服务器仍然保留。
     */
    public void onToggleTrack(Player player) {
        String cur = renderer.selectedOf(player);
        if (cur == null || cur.isBlank()) {
            return;
        }
        String tracked = plugin.prefs().of(player.getUniqueId()).trackedId;
        boolean starting = !cur.equals(tracked);
        String previous = tracked;
        plugin.prefs().setTracked(player.getUniqueId(), starting ? cur : null);
        clickSound(player);
        // 立刻刷新 HUD（切追踪目标 / 取消追踪）
        plugin.hud().refresh(player, true);
        if (starting) {
            // 从 A 切到 B：先执行 A 的「取消追踪」指令，再执行 B 的「追踪」指令。
            // 否则导航会叠在一起，两条路线同时画在屏幕上。
            if (previous != null && !previous.isBlank() && !previous.equals(cur)) {
                runUntrackCommands(player, previous);
            }
            // 开始追踪：关掉面板，直接回到游戏看 HUD
            closePanel(player);
            // 执行 quests/ 里配的追踪指令（导航等），放在关面板之后，
            // 这样玩家先看到游戏画面，指令的效果（比如地图上出现路径）更直观。
            runTrackCommands(player, cur);
        } else {
            // 取消追踪：面板保持打开，方便改选别的任务
            runUntrackCommands(player, cur);
            push(player);
        }
    }

    /**
     * 执行 quests/*.yml 里给这个任务配的追踪指令。
     *
     * <p>以玩家本人身份执行一次，不做「失败再走控制台」的重试 ——
     * 那会把指令执行两遍（详见 {@link #runCommands} 里的说明）。
     */
    public void runTrackCommands(Player player, String questId) {
        if (!allowCmd(player, "track", questId)) {
            return;
        }
        var cfg = cfgOf(questId);
        if (cfg == null) {
            return;
        }
        runCommands(player, cfg.trackCommands(), cfg.navPos(), "追踪指令");
    }

    /**
     * 执行 quests/*.yml 里给这个任务配的「取消追踪」指令。
     *
     * <p>典型用途是 {@code startrail nav stop {player}} —— 停止导航。
     */
    public void runUntrackCommands(Player player, String questId) {
        if (!allowCmd(player, "untrack", questId)) {
            return;
        }
        var cfg = cfgOf(questId);
        if (cfg == null) {
            return;
        }
        runCommands(player, cfg.untrackCommands(), cfg.navPos(), "取消追踪指令");
    }

    /**
     * 同一玩家同一类事件的去重窗口（毫秒）。
     *
     * <p>Chemdah 的完成事件可能因为「任务自己完成 + 玩家手动提交」等原因
     * 在极短时间内触发多次；不去重的话玩家会看到一屏重复的指令回执。
     */
    private static final long CMD_DEDUP_MS = 600L;

    /** key = 玩家UUID + 事件类型 + 任务ID */
    private final java.util.Map<String, Long> lastCmdAt = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 去重检查：短时间内重复调用返回 false。
     */
    private boolean allowCmd(Player player, String kind, String questId) {
        String key = player.getUniqueId() + "|" + kind + "|" + questId;
        long now = System.currentTimeMillis();
        Long prev = lastCmdAt.get(key);
        if (prev != null && now - prev < CMD_DEDUP_MS) {
            return false;
        }
        lastCmdAt.put(key, now);
        // 顺手清掉过期条目，避免 Map 无限增长
        if (lastCmdAt.size() > 512) {
            lastCmdAt.entrySet().removeIf(e -> now - e.getValue() > 10_000L);
        }
        return true;
    }

    /**
     * 任务完成时执行的指令。典型用途：停止导航。
     */
    public void runCompleteCommands(Player player, String questId) {
        if (!allowCmd(player, "complete", questId)) {
            return;
        }
        var cfg = cfgOf(questId);
        if (cfg == null) {
            return;
        }
        runCommands(player, cfg.completeCommands(), cfg.navPos(), "完成指令");
        // 完成后任务从追踪列表消失，导航也该停 —— 如果没单独配完成指令，
        // 用取消追踪的兜底一次，避免导航残留。
        if (cfg.completeCommands() == null || cfg.completeCommands().isEmpty()) {
            runCommands(player, cfg.untrackCommands(), cfg.navPos(), "完成指令(兜底)");
        }
    }

    /**
     * 任务失败（含超时 / 放弃）时执行的指令。典型用途：停止导航。
     */
    public void runFailCommands(Player player, String questId) {
        if (!allowCmd(player, "fail", questId)) {
            return;
        }
        var cfg = cfgOf(questId);
        if (cfg == null) {
            return;
        }
        runCommands(player, cfg.failCommands(), cfg.navPos(), "失败指令");
        // 同理：没单独配失败指令时，用取消追踪的兜底
        if (cfg.failCommands() == null || cfg.failCommands().isEmpty()) {
            runCommands(player, cfg.untrackCommands(), cfg.navPos(), "失败指令(兜底)");
        }
    }

    private com.asterovie.momo.starpact.server.quest.QuestConfig cfgOf(String questId) {
        var loader = plugin.quests();
        if (loader == null || questId == null || questId.isBlank()) {
            return null;
        }
        return loader.get(questId);
    }

    /**
     * 逐条执行指令，替换占位符。
     *
     * <p>支持的占位符：{@code {player}}、{@code {x}}、{@code {y}}、{@code {z}}
     * （坐标来自任务的 {@code nav-pos}，没配就是 0）。
     */
    private void runCommands(Player player, java.util.List<String> commands,
                             java.util.List<Double> navPos, String tag) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        double x = 0, y = 0, z = 0;
        if (navPos != null) {
            if (navPos.size() >= 3) {
                x = navPos.get(0);
                y = navPos.get(1);
                z = navPos.get(2);
            } else if (navPos.size() == 2) {
                // 只给了 x/z：Y 用玩家当前高度
                x = navPos.get(0);
                y = player.getLocation().getY();
                z = navPos.get(1);
            }
        }
        plugin.getLogger().info("[" + tag + "] 开始执行 " + commands.size() + " 条指令: " + commands);
        for (String raw : commands) {
            String cmd = raw
                    .replace("{player}", player.getName())
                    .replace("{x}", fmtCoord(x))
                    .replace("{y}", fmtCoord(y))
                    .replace("{z}", fmtCoord(z));
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            try {
                // ⚠️ 只以玩家身份执行一次，不做「失败再用控制台重试」。
                // dispatchCommand 返回 false 有两种情况：玩家没权限、或指令自己执行失败。
                // 后者重试会让指令被执行两遍 —— StarTrail 的 nav stop 在没有导航时
                // 就返回 false，于是玩家会看到两条「已被停止导航」的回执。
                org.bukkit.Bukkit.dispatchCommand(player, cmd);
            } catch (Throwable t) {
                plugin.getLogger().warning("[" + tag + "] 执行失败: " + cmd + " -> " + t);
            }
        }
    }

    /** 坐标格式化：整数不带小数点，小数保留一位。 */
    private static String fmtCoord(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** 滚轮节流间隔（毫秒）。 */
    private static final long SCROLL_THROTTLE_MS = 150L;
    private final java.util.Map<UUID, Long> lastScrollAt = new java.util.concurrent.ConcurrentHashMap<>();

    /** 左栏固定行点击：把行号换算成任务 ID 后走正常选中流程。 */
    public void onSelectRow(Player player, int row) {
        String id = renderer.questIdAt(player, row);
        if (id == null || id.isBlank() || "__empty__".equals(id)) {
            return;
        }
        onSelectQuest(player, id);
    }
    public void onSelectQuest(Player player, String questId) {
        clickSound(player);
        renderer.setSelected(player, questId);
        // 换任务后回到顶部，否则会停在上一个任务的滚动位置
        renderer.resetDetailScroll(player);
        push(player);
    }

    /**
     * ready 包节流：每玩家最多每 2 秒受理一次。
     *
     * <p>面板 UI 的 open 触发器会在重绘时反复触发，不节流会死循环。
     */
    public boolean allowReady(UUID id) {
        long now = System.currentTimeMillis();
        Long last = readyAt.get(id);
        if (last != null && now - last < 2000L) {
            return false;
        }
        readyAt.put(id, now);
        return true;
    }

    /**
     * 播放原版按钮点击音效。
     *
     * <p>用服务端播放而不是客户端 Aria 的 Sound.self：后者要求 .ogg 资源路径。
     */
    public void clickSound(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        } catch (Throwable ignored) {
            // 音效失败不影响功能
        }
    }

    /** 把当前页的数据推给客户端。 */
    public void push(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, Object> packet = renderer.buildPacket(player);
        // 先把容器内容同步成「当前选中任务」的奖励，
        // 否则换任务后 packet 里的 reward_rows 和容器里的物品会对不上
        refreshRewardContainer(player);

        plugin.ax().sendPacket(player, PanelRenderer.UI_ID, "sync", packet);
    }

    public void onQuit(Player player) {
        renderer.forget(player.getUniqueId());
        readyAt.remove(player.getUniqueId());
    }
}
