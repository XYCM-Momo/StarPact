package com.asterovie.momo.starpact.server.model;

/**
 * 单条追踪目标（HUD 显示用）。
 */
public record TrackEntry(String questId, String taskId, String title, String titlePlain, double value, double target, double percent, boolean completed) {
    public static TrackEntry empty() {
        return new TrackEntry("", "", "", "", 0.0, 0.0, 0.0, false);
    }

    public boolean isBlank() {
        return this.questId.isEmpty() && this.taskId.isEmpty();
    }

    public String numbers() {
        if (this.target <= 0.0) {
            return TrackEntry.trimNum(this.value);
        }
        return TrackEntry.trimNum(this.value) + "/" + TrackEntry.trimNum(this.target);
    }

    private static String trimNum(double d) {
        if (Math.abs(d - Math.rint(d)) < 1.0E-6) {
            return String.valueOf((long)Math.rint(d));
        }
        return String.format("%.1f", d);
    }

    public String bar(int n, String string, String string2) {
        int n2 = this.target > 0.0 ? (int)Math.round(this.percent * (double)n) : (this.completed ? n : 0);
        n2 = Math.max(0, Math.min(n, n2));
        StringBuilder stringBuilder = new StringBuilder(n);
        for (int i = 0; i < n; ++i) {
            stringBuilder.append(i < n2 ? string : string2);
        }
        return stringBuilder.toString();
    }
}

