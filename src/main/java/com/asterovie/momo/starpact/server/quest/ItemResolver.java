package com.asterovie.momo.starpact.server.quest;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * 物品库适配层 —— 把 {@code MM:xxx} / {@code NI:xxx} / {@code VANILLA:xxx}
 * 解析成可显示的物品名。
 *
 * <p><b>全程反射</b>，和 {@code AxHook} / {@code ChemdahHook} 同一个原则：
 * 物品库没装时不能让插件崩在类解析阶段。
 *
 * <p>用途仅限<b>展示</b>——取到物品名和图标的文字描述，不发放物品。
 */
public final class ItemResolver {

    /** MythicMobs 是否可用（启动时探测一次） */
    private final boolean mythicAvailable;
    /** NeigeItems 是否可用 */
    private final boolean neigeAvailable;

    public ItemResolver(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.mythicAvailable = isPluginEnabled("MythicMobs");
        this.neigeAvailable = isPluginEnabled("NeigeItems");
        plugin.getLogger().info("物品库接入: MythicMobs="
                + (mythicAvailable ? "已接入" : "未安装")
                + " / NeigeItems=" + (neigeAvailable ? "已接入" : "未安装"));
    }

    public boolean hasMythic() {
        return mythicAvailable;
    }

    public boolean hasNeige() {
        return neigeAvailable;
    }

    /**
     * 解析一条奖励，得到「显示名」。
     *
     * <p>解析失败时返回 {@code null}，由调用方决定怎么兜底
     * （通常是直接显示原始 key）。
     */
    public String displayName(QuestConfig.Reward reward) {
        if (reward == null) {
            return null;
        }
        if (reward.display() != null && !reward.display().isBlank()) {
            return reward.display();
        }
        String key = reward.key();
        if (key == null || key.isBlank()) {
            return null;
        }

        return switch (reward.provider()) {
            case "MM" -> mythicName(key);
            case "NI" -> neigeName(key);
            case "VANILLA" -> vanillaName(key);
            default -> null;
        };
    }

    /**
     * 解析结果缓存。
     *
     * <p>每次按 J 打开任务面板都会重新解析所有奖励物品，而解析要走
     * MythicMobs / NeigeItems 的反射查询 + ItemStack 克隆，开销不小 ——
     * 实测这就是「按 J 有明显卡顿」的来源。
     *
     * <p>键用 {@code provider|key|amount|icon}，几点注意：
     * <ul>
     *   <li>存的是 {@code clone()}，避免调用方改到缓存里的实例；</li>
     *   <li>{@code /sp reload} 时清空（物品库配置可能变了）；</li>
     *   <li>上限定在 512，防止长期运行后无限增长。</li>
     * </ul>
     */
    private final java.util.Map<String, org.bukkit.inventory.ItemStack> cache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 清空解析缓存（reload 时调用）。 */
    public void clearCache() {
        cache.clear();
    }

    /**
     * 预解析所有任务配置里的奖励，把结果灌进缓存。
     *
     * <p>不预热的话，「第一次按 J 打开面板」仍会卡一下 —— 那次请求要把所有
     * 奖励物品走一遍 MM/NI 反射查询。这个方法给启动时的异步预热用，
     * **调用方必须放到后台线程**，不要阻塞服务端启动。
     *
     * @return 实际解析成功的条数
     */
    public int prewarm(QuestConfigLoader loader) {
        if (loader == null || (!mythicAvailable && !neigeAvailable)) {
            return 0;   // 两个物品库都没装，预解析没有意义
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        int done = 0;
        for (String qid : loader.ids()) {
            QuestConfig qc = loader.get(qid);
            if (qc == null || qc.rewards().isEmpty()) {
                continue;
            }
            for (QuestConfig.Reward r : qc.rewards()) {
                String k = cacheKey(r);
                if (k == null || !seen.add(k)) {
                    continue;   // 已经处理过同一个奖励
                }
                try {
                    if (resolve(r) != null) {
                        done++;
                    }
                } catch (Throwable ignored) {
                    // 单条失败不影响其它条
                }
            }
        }
        return done;
    }

    /**
     * 解析成真实的 ItemStack（用于 Slot 控件渲染图标），结果带缓存。
     *
     * <p>解析失败返回 null —— 调用方决定兜底（通常是显示纯文字）。
     */
    public org.bukkit.inventory.ItemStack resolve(QuestConfig.Reward reward) {
        if (reward == null) {
            return null;
        }
        String ck = cacheKey(reward);
        org.bukkit.inventory.ItemStack hit = ck == null ? null : cache.get(ck);
        if (hit != null) {
            return hit.clone();
        }
        org.bukkit.inventory.ItemStack made = resolveUncached(reward);
        if (ck != null && made != null) {
            if (cache.size() > 512) {
                cache.clear();
            }
            cache.put(ck, made.clone());
        }
        return made;
    }

    /** 缓存键；拿不到有效 key 时返回 null（不缓存）。 */
    private static String cacheKey(QuestConfig.Reward r) {
        if (r == null || r.key() == null || r.key().isBlank()) {
            return null;
        }
        return r.provider() + '|' + r.key() + '|' + r.amount() + '|' + r.icon();
    }

    /** 真正干活的部分（原 resolve 的实现）。 */
    private org.bukkit.inventory.ItemStack resolveUncached(QuestConfig.Reward reward) {
        if (reward == null) {
            return null;
        }
        String key = reward.key();
        if (key == null || key.isBlank()) {
            return null;
        }
        // 指令型奖励：面板上用 reward.icon 指定的物品展示
        // （比如「经验」用 MM/NI 里配的经验瓶图标）
        if (reward.isCommand()) {
            boolean iconFromLib = iconComesFromItemLib(reward.icon());
            org.bukkit.inventory.ItemStack icon = resolveIcon(reward.icon());
            if (icon == null) {
                icon = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMMAND_BLOCK);
                iconFromLib = false;
            }
            // 图标来自 MM/NI 时保留它自己的名称与 Lore（作者已经在库里写好了）；
            // 原版材质/兜底图标才用 display 补名字，并把指令写进 Lore。
            if (!iconFromLib) {
                try {
                    var meta = icon.getItemMeta();
                    if (meta != null) {
                        String label = reward.display() != null && !reward.display().isBlank()
                                ? reward.display() : "\u00a7e\u5956\u52b1\u6307\u4ee4";
                        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', label));
                        meta.setLore(java.util.List.of("\u00a78" + reward.key()));
                        icon.setItemMeta(meta);
                    }
                } catch (Throwable ignored) {
                    // 改不了名字就用原物品名
                }
            }
            return icon;
        }

        org.bukkit.inventory.ItemStack stack = switch (reward.provider()) {
            case "MM" -> mythicStack(key);
            case "NI" -> neigeStack(key);
            case "VANILLA" -> vanillaStack(key);
            default -> null;
        };
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        // 数量只用于展示，真实发放由 Chemdah 负责
        org.bukkit.inventory.ItemStack copy = stack.clone();
        copy.setAmount(Math.max(1, Math.min(64, reward.amount())));
        // MM / NI 物品自带完整名称与 Lore，直接用它们，不覆盖。
        // 只有「原版材质」这类没有自定义名称的物品，才用 display 补一个名字。
        boolean keepOriginalName = "MM".equals(reward.provider())
                || "NI".equals(reward.provider());
        if (!keepOriginalName && reward.display() != null && !reward.display().isBlank()) {
            try {
                var meta = copy.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes(
                            '&', reward.display()));
                    copy.setItemMeta(meta);
                }
            } catch (Throwable ignored) {
                // 改不了名字就算了，保留原始名称
            }
        }
        return copy;
    }

    /** icon 引用是否来自 MM/NI（这类物品自带名称与 Lore，不该被 display 覆盖）。 */
    private static boolean iconComesFromItemLib(String ref) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        String s = ref.trim();
        int q = s.indexOf('?');
        if (q > 0) {
            s = s.substring(0, q);
        }
        int i = s.indexOf(':');
        if (i <= 0) {
            return false;
        }
        String p = normalizeProviderLocal(s.substring(0, i));
        return "MM".equals(p) || "NI".equals(p);
    }

    /**
     * 解析「图标」引用，格式和奖励物品一致。
     *
     * <p>支持 {@code mm:ID} / {@code ni:ID} / {@code 材质名}，
     * 解析不出来返回 null（调用方用兜底图标）。
     */
    private org.bukkit.inventory.ItemStack resolveIcon(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String s = ref.trim();

        // 支持 "ni:物品ID?键=值&键2=值2" 传参，用于动态名称/Lore 的图标
        java.util.Map<String, String> params = null;
        int q = s.indexOf('?');
        if (q > 0) {
            params = new java.util.LinkedHashMap<>();
            for (String pair : s.substring(q + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    params.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
            s = s.substring(0, q);
        }

        int i = s.indexOf(':');
        String p = i > 0 ? s.substring(0, i) : "VANILLA";
        String id = i > 0 ? s.substring(i + 1) : s;
        org.bukkit.inventory.ItemStack stack = switch (normalizeProviderLocal(p)) {
            case "MM" -> mythicStack(id);
            case "NI" -> neigeStack(id, params);
            case "VANILLA" -> vanillaStack(id);
            default -> null;
        };
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }

    /** 和 QuestConfigLoader 的规则保持一致。 */
    private static String normalizeProviderLocal(String s) {
        if (s == null || s.isBlank()) {
            return "VANILLA";
        }
        String v = s.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.startsWith("mm") || v.contains("mythic")) {
            return "MM";
        }
        if (v.startsWith("ni") || v.contains("neige")) {
            return "NI";
        }
        return "VANILLA";
    }

    /** 取 MythicMobs 物品的 ItemStack。 */
    private org.bukkit.inventory.ItemStack mythicStack(String id) {
        if (!mythicAvailable) {
            return null;
        }
        try {
            Class<?> bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = bukkit.getMethod("inst").invoke(null);
            if (inst == null) {
                return null;
            }
            Object mgr = bukkit.getMethod("getItemManager").invoke(inst);
            if (mgr == null) {
                return null;
            }
            Object opt = mgr.getClass().getMethod("getItem", String.class).invoke(mgr, id);
            Object mythicItem = (opt instanceof java.util.Optional<?> o) ? o.orElse(null) : opt;
            if (mythicItem == null) {
                return null;
            }
            // MythicItem 上能直接拿 ItemStack 的方法（javap 实测）：
            //   getCachedMenuItem()  -> ItemStack
            //   getCachedBaseItem()  -> ItemStack
            //   generateItemStack(int) -> AbstractItemStack（接口无公开方法，不好用）
            for (String m : new String[]{"getCachedMenuItem", "getCachedBaseItem"}) {
                try {
                    Object v = mythicItem.getClass().getMethod(m).invoke(mythicItem);
                    if (v instanceof org.bukkit.inventory.ItemStack s
                            && !s.getType().isAir()) {
                        return s;
                    }
                } catch (NoSuchMethodException ignored) {
                    // 换下一个
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 取原版物品的 ItemStack。 */
    private org.bukkit.inventory.ItemStack vanillaStack(String key) {
        try {
            Material m = Material.matchMaterial(key.trim().toUpperCase(Locale.ROOT));
            if (m == null || m.isAir()) {
                return null;
            }
            return new ItemStack(m);
        } catch (Throwable ignored) {
            return null;
        }
    }
    // ── MythicMobs ───────────────────────────────────────────

    /**
     * 取 MythicMobs 物品的显示名。
     *
     * <p>路径：{@code MythicBukkit.inst().getItemManager().getItem(id)}
     * → {@code MythicItem.getDisplayName()}。
     * 5.6.2 里 {@code getItem} 返回 {@code Optional<MythicItem>}。
     */
    private String mythicName(String id) {
        if (!mythicAvailable) {
            return null;
        }
        try {
            Class<?> bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = bukkit.getMethod("inst").invoke(null);
            if (inst == null) {
                return null;
            }
            Object mgr = bukkit.getMethod("getItemManager").invoke(inst);
            if (mgr == null) {
                return null;
            }
            Object opt = mgr.getClass().getMethod("getItem", String.class).invoke(mgr, id);
            if (opt == null) {
                return null;
            }
            // Optional<MythicItem>
            Object item = null;
            if (opt instanceof java.util.Optional<?> o) {
                item = o.orElse(null);
            } else {
                item = opt;
            }
            if (item == null) {
                return null;
            }
            // 优先 getDisplayName()，退回内部 ID
            for (String m : new String[]{"getDisplayName", "getDisplayname"}) {
                try {
                    Object v = item.getClass().getMethod(m).invoke(item);
                    String s = toPlain(v);
                    if (s != null && !s.isBlank()) {
                        return s;
                    }
                } catch (NoSuchMethodException ignored) {
                    // 换下一个
                }
            }
            return id;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ── NeigeItems ───────────────────────────────────────────

    /**
     * 取 NeigeItems 物品的显示名。
     *
     * <p>NI 的 API 路径在不同版本间变动过，这里做多路探测：
     * <ol>
     *   <li>{@code pers.neige.neigeitems.manager.ItemManager#getItemStack(String)}</li>
     *   <li>{@code pers.neige.neigeitems.NeigeItemsAPI#getItemStack(String)}</li>
     * </ol>
     */
    private String neigeName(String id) {
        if (!neigeAvailable) {
            return null;
        }
        ItemStack stack = neigeStack(id);
        if (stack == null) {
            return null;
        }
        return plainOfStack(stack);
    }

    /**
     * 拿到 NI 的 ItemStack（拿不到就 null）。
     *
     * <p>NeigeItems 1.22.1 的实际签名（javap 实测）：
     * <pre>
     * pers.neige.neigeitems.manager.ItemManager
     *   public static final ItemManager INSTANCE;
     *   public final ItemStack getItemStack(String)
     * </pre>
     * 所以是「静态字段拿单例 → 实例方法取物品」。
     */
    /**
     * NeigeItems 带参数版本。
     *
     * <p>NI 物品里可以写 {@code <变量名>} 占位符，这里传进去的 Map 会替换它们，
     * 于是同一个物品 ID 能生成名称/Lore 不同的一次性图标
     * （比如「100 点经验」的经验瓶）。
     *
     * @param id     NI 物品 ID
     * @param params 参数表；null 或空 = 等价于普通调用
     */
    private ItemStack neigeStack(String id, java.util.Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return neigeStack(id);
        }
        try {
            Class<?> mgrClass = Class.forName("pers.neige.neigeitems.manager.ItemManager");
            Object inst = mgrClass.getField("INSTANCE").get(null);
            if (inst == null) {
                return neigeStack(id);
            }
            Object v = mgrClass
                    .getMethod("getItemStack", String.class, java.util.Map.class)
                    .invoke(inst, id, params);
            ItemStack s = asStack(v);
            if (s != null) {
                return s;
            }
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[StarPact][NI] 带参调用失败: " + t);
        }
        return neigeStack(id);
    }

    private ItemStack neigeStack(String id) {
        try {
            Class<?> mgrClass = Class.forName("pers.neige.neigeitems.manager.ItemManager");
            Object inst = mgrClass.getField("INSTANCE").get(null);
            if (inst == null) {
                return null;
            }
            Object v = mgrClass.getMethod("getItemStack", String.class).invoke(inst, id);
            ItemStack s = asStack(v);
            if (s != null) {
                return s;
            }
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[StarPact][NI] ItemManager 路径失败: " + t);
        }

        // 备用：走插件主类的 getInstance()
        try {
            Class<?> main = Class.forName("pers.neige.neigeitems.NeigeItems");
            Object plugin = main.getMethod("getInstance").invoke(null);
            if (plugin == null) {
                return null;
            }
            Object mgr = main.getMethod("getItemManager").invoke(plugin);
            if (mgr == null) {
                return null;
            }
            Object v = mgr.getClass().getMethod("getItemStack", String.class).invoke(mgr, id);
            return asStack(v);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[StarPact][NI] NeigeItems 路径依赖失败: " + t);
            return null;
        }
    }

    /** 把反射拿到的对象转成 ItemStack（兼容直接返回和 Optional 包装）。 */
    private static ItemStack asStack(Object v) {
        if (v instanceof ItemStack s) {
            return s;
        }
        if (v instanceof java.util.Optional<?> o) {
            Object inner = o.orElse(null);
            if (inner instanceof ItemStack s) {
                return s;
            }
        }
        return null;
    }

    // ── 原版 ─────────────────────────────────────────────────

    private String vanillaName(String key) {
        try {
            Material m = Material.matchMaterial(key.trim().toUpperCase(Locale.ROOT));
            if (m == null || m.isAir()) {
                return null;
            }
            ItemStack stack = new ItemStack(m);
            return plainOfStack(stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ── 通用 ─────────────────────────────────────────────────

    /**
     * 取 ItemStack 的显示名（纯文本，去样式）。
     *
     * <p>Paper 1.20.1 上 {@code ItemMeta#displayName()} 返回 Adventure 的
     * {@code Component}，而 {@code getDisplayName()} 返回旧版 String —— 两个都试。
     */
    private static String plainOfStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        try {
            var meta = stack.getItemMeta();
            if (meta != null) {
                // 旧版 API（保留色码）
                try {
                    String legacy = meta.getClass().getMethod("getDisplayName").invoke(meta)
                            instanceof String s ? s : null;
                    if (legacy != null && !legacy.isBlank()) {
                        return stripColors(legacy);
                    }
                } catch (NoSuchMethodException ignored) {
                    // 新版没有这个方法
                }
                // 新版 Adventure
                try {
                    Object comp = meta.getClass().getMethod("displayName").invoke(meta);
                    if (comp != null) {
                        String s = String.valueOf(comp);
                        if (!s.isBlank()) {
                            return stripColors(s);
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                    // 没有就算了
                }
            }
        } catch (Throwable ignored) {
            // 落到下面的兜底
        }
        return prettifyMaterial(stack.getType());
    }

    /** 把 DIAMOND_SWORD 变成「钻石剑」这种事做不到，只做基础美化。 */
    private static String prettifyMaterial(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : n.toCharArray()) {
            if (c == ' ') {
                sb.append(' ');
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }

    /** 去掉 §x / &x 色码。 */
    private static String stripColors(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("[\u00a7&][0-9a-fk-orx]", "").trim();
    }

    /** Adventure Component 转纯文本字符串（兼容旧版）。 */
    private static String toPlain(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return stripColors(s);
        }
        // Adventure Component：转成 legacy 字符串再剥色码
        try {
            Class<?> legacy = Class.forName(
                    "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object inst = legacy.getMethod("legacySection").invoke(null);
            Object out = inst.getClass().getMethod("serialize", Object.class)
                    .invoke(inst, v);
            if (out instanceof String s) {
                return stripColors(s);
            }
        } catch (Throwable ignored) {
            // 不是 Component，走 toString
        }
        return stripColors(String.valueOf(v));
    }

    private static boolean isPluginEnabled(String name) {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            return p != null && p.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
