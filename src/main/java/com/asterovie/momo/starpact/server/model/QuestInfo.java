package com.asterovie.momo.starpact.server.model;

import java.util.List;

/**
 * 一个「玩家接取中的任务」的完整快照，供任务面板渲染。
 *
 * <p>和 {@link TrackEntry} 的区别：TrackEntry 是「单条追踪目标」（HUD 用），
 * 这个是「整项任务 + 它的所有目标 + 奖励」（面板左栏列表 + 右栏详情用）。
 */
public record QuestInfo(
        /** 任务模板 ID */
        String id,
        /** 显示名（已去色码） */
        String name,
        /** 分类：shixu / jian / can（时序 / 间章 / 残流），由 meta.type 映射 */
        String category,
        /** 描述（多行，已去色码） */
        List<String> description,
        /** 各目标 */
        List<Objective> objectives,
        /** 奖励（可能为空） */
        List<Reward> rewards) {

    /** 单个目标 */
    public record Objective(
            String id,
            String name,
            double value,
            double target,
            double percent,
            boolean completed) {

        public String numbers() {
            if (target <= 0) {
                return trim(value);
            }
            return trim(value) + "/" + trim(target);
        }

        private static String trim(double d) {
            if (Math.abs(d - Math.rint(d)) < 1e-6) {
                return String.valueOf((long) Math.rint(d));
            }
            return String.format("%.1f", d);
        }
    }

    /** 单个奖励（面板只做展示，实际发放仍由 Chemdah agent 负责） */
    public record Reward(String name, String amount) {
    }

    /**
     * 该任务是否全部目标完成。
     */
    public boolean allCompleted() {
        if (objectives.isEmpty()) {
            return false;
        }
        for (Objective o : objectives) {
            if (!o.completed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 已完成的成员数 / 总数，用于列表右侧显示 "1/3"。
     */
    public String progressText() {
        int done = 0;
        for (Objective o : objectives) {
            if (o.completed()) {
                done++;
            }
        }
        return done + "/" + objectives.size();
    }
}
