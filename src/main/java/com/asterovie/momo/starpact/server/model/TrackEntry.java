package com.asterovie.momo.starpact.server.model;

/**
 * 一条「追踪目标」的显示快照。
 *
 * <p>由 {@link com.asterovie.momo.starpact.server.chemdah.ChemdahHook} 从 Chemdah 读出来，
 * 交给渲染层（ArcartX HUD 或退化的 BossBar）。渲染层不应该再去碰 Chemdah 的类。
 */
public record TrackEntry(
        String questId,
        String taskId,
        /** 目标标题（已去掉颜色代码的纯文本版另存于 titlePlain） */
        String title,
        /** 去掉 § 颜色代码后的纯文本，用于计算长度 */
        String titlePlain,
        double value,
        double target,
        /** 0.0 ~ 1.0 */
        double percent,
        boolean completed) {

    public static TrackEntry empty() {
        return new TrackEntry("", "", "", "", 0, 0, 0, false);
    }

    public boolean isBlank() {
        return questId.isEmpty() && taskId.isEmpty();
    }

    /** "3/10"；target 为 0 时只显示 value。 */
    public String numbers() {
        if (target <= 0) {
            return trimNum(value);
        }
        return trimNum(value) + "/" + trimNum(target);
    }

    private static String trimNum(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-6) {
            return String.valueOf((long) Math.rint(d));
        }
        return String.format("%.1f", d);
    }

    /**
     * 生成进度条字符串，例如 3/10 长度 10 时 "|||......."。
     */
    public String bar(int length, String fill, String empty) {
        int filled;
        if (target > 0) {
            filled = (int) Math.round(percent * length);
        } else {
            filled = completed ? length : 0;
        }
        filled = Math.max(0, Math.min(length, filled));
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(i < filled ? fill : empty);
        }
        return sb.toString();
    }
}
