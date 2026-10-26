package com.asterovie.momo.starpact.server.model;

import java.util.List;

/**
 * 一个「玩家接取中的任务」的完整快照，供任务面板渲染。
 */
public record QuestInfo(String id, String name, String category, List<String> description, List<Objective> objectives, List<Reward> rewards) {
    public boolean allCompleted() {
        if (this.objectives.isEmpty()) {
            return false;
        }
        for (Objective objective : this.objectives) {
            if (objective.completed()) continue;
            return false;
        }
        return true;
    }

    public String progressText() {
        int n = 0;
        for (Objective objective : this.objectives) {
            if (!objective.completed()) continue;
            ++n;
        }
        return n + "/" + this.objectives.size();
    }

    public record Objective(String id, String name, double value, double target, double percent, boolean completed) {
        public String numbers() {
            if (this.target <= 0.0) {
                return Objective.trim(this.value);
            }
            return Objective.trim(this.value) + "/" + Objective.trim(this.target);
        }

        private static String trim(double d) {
            if (Math.abs(d - Math.rint(d)) < 1.0E-6) {
                return String.valueOf((long)Math.rint(d));
            }
            return String.format("%.1f", d);
        }
    }

    public record Reward(String name, String amount) {
    }
}

