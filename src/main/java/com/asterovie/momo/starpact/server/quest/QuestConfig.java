package com.asterovie.momo.starpact.server.quest;

import java.util.List;

/**
 * 一个任务在 quests/ 配置里的定义。
 *
 * <p>这些配置<b>只用于展示</b>——描述文字和奖励列表会被推到任务面板上，
 * 实际奖励发放仍由 Chemdah 的任务脚本负责。
 *
 * <p>字段全部可空：没配的部分回退到 Chemdah 的原始数据。
 */
public record QuestConfig(
        /** 任务 ID（配置里的键，对应 Chemdah 的 quest id） */
        String id,
        /** 覆盖显示名；null = 用 Chemdah 的 */
        String name,
        /** 覆盖分类（shixu / jian / can）；null = 用 Chemdah 的 meta.type */
        String category,
        /** 描述（多行，可为空 = 用 Chemdah 的 addon.ui.description） */
        List<String> description,
        /** 奖励列表 */
        List<Reward> rewards,
        /**
         * 玩家在任务面板里点「追踪」时执行的指令（可多条）。
         *
         * <p>典型用途：接上 StarTrail 的导航，比如
         * {@code startrail nav {player} <x> <z> <名称>}。
         *
         * <p>支持占位符 {@code {player}}、{@code {x}}、{@code {y}}、{@code {z}}
         * （坐标来自 {@link #navPos()}，没配就替换成 0）。
         *
         * <p>以<b>玩家本人身份</b>执行；玩家权限不足时自动改用控制台重试。
         */
        List<String> trackCommands,
        /**
         * 玩家点「取消追踪」时执行的指令（可多条）。
         *
         * <p>典型用途：停掉导航 —— {@code startrail nav stop {player}}。
         *
         * <p>触发时机：手动取消追踪、或者切去追踪另一个任务时（旧任务取消）。
         */
        List<String> untrackCommands,
        /**
         * 任务<b>完成</b>时执行的指令（可多条）。
         *
         * <p>典型用途：停掉导航 —— {@code startrail nav stop {player}}。
         *
         * <p>占位符与 {@link #trackCommands()} 相同。
         */
        List<String> completeCommands,
        /**
         * 任务<b>失败</b>（含超时/放弃）时执行的指令（可多条）。
         *
         * <p>典型用途：停掉导航。
         */
        List<String> failCommands,
        /**
         * 导航目标坐标 {@code [x, y, z]}，用于「点追踪后视角转向目标」。
         *
         * <p>也可以不配 —— 那样只执行 {@link #trackCommands()}，不转视角。
         * 只配两个值时按 {@code [x, z]} 处理（Y 由服务端取玩家当前高度）。
         */
        List<Double> navPos) {

    /**
     * 单条奖励。
     *
     * <p>两种形态，靠 {@code provider} 区分：
     * <ul>
     *   <li><b>物品</b>：{@code MM}（MythicMobs）/ {@code NI}（NeigeItems）/
     *       {@code VANILLA}（原版材质名），{@code key} 是物品 ID</li>
     *   <li><b>指令</b>：{@code COMMAND}，{@code key} 是要执行的指令。
     *       用于物品表达不了的奖励——经验、点券、权限、称号等。
     *       支持占位符 {@code {player}}，会被替换成玩家名。
     *       指令<b>以控制台身份</b>执行（所以权限类指令也能生效）。</li>
     * </ul>
     *
     * @param provider 见上
     * @param key      物品 ID 或指令内容
     * @param amount   数量（物品用；指令忽略）
     * @param display  面板上显示的名字；null = 自动
     * @param icon     指令奖励在面板上用哪个物品展示，格式 {@code mm:ID} /
     *                 {@code ni:ID} / {@code 材质名}。
     *                 null = 用命令方块兜底。物品型奖励忽略此字段。
     */
    public record Reward(
            String provider,
            String key,
            int amount,
            String display,
            String icon) {

        /** 是不是「执行指令」型奖励。 */
        public boolean isCommand() {
            return "COMMAND".equals(provider);
        }
    }

    /** 是否什么都没配（用于快速跳过）。 */
    public boolean isEmpty() {
        return name == null && category == null
                && (description == null || description.isEmpty())
                && (rewards == null || rewards.isEmpty())
                && (trackCommands == null || trackCommands.isEmpty())
                && (untrackCommands == null || untrackCommands.isEmpty())
                && (completeCommands == null || completeCommands.isEmpty())
                && (failCommands == null || failCommands.isEmpty())
                && (navPos == null || navPos.isEmpty());
    }
}
