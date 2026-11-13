package com.asterovie.momo.starpact.server.util;

import java.util.regex.Pattern;

/** 颜色代码 → 纯文本，以及长度计算。 */
public final class Texts {

    private static final Pattern SECTION = Pattern.compile("(?i)\u00a7[0-9A-FK-ORX]");
    private static final Pattern AMP = Pattern.compile("(?i)&[0-9A-FK-ORX]");

    private Texts() {
    }

    /** 去掉 §x / &x 颜色代码。 */
    public static String strip(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String out = SECTION.matcher(s).replaceAll("");
        out = AMP.matcher(out).replaceAll("");
        return out;
    }

    /** 按「视觉宽度」算长度：中文算 2，几何符号算 1，其余算 1。用于截断标题。 */
    public static int visualWidth(String s) {
        String plain = strip(s);
        int w = 0;
        for (int i = 0; i < plain.length(); i++) {
            w += charWidth(plain.charAt(i));
        }
        return w;
    }

    /**
     * 单个字符占几格。
     *
     * <p>不能简单用 {@code c > 0xFF ? 2 : 1}：进度条用的 ▰/▱(U+25B0/25B1)、
     * 项目符号 ◈(U+25C8)、✔(U+2714) 都大于 0xFF，但在原版字体里是**半角**。
     * 按 2 格算会让宽度估算偏大，导致右对齐的数字位置漂移。
     */
    private static int charWidth(char c) {
        // 明确是全角的区块
        if ((c >= 0x4E00 && c <= 0x9FFF)      // CJK 统一汉字
                || (c >= 0x3000 && c <= 0x303F)   // CJK 标点
                || (c >= 0xFF00 && c <= 0xFF60)   // 全角 ASCII
                || (c >= 0xFFE0 && c <= 0xFFE6)) {
            return 2;
        }
        // 几何图形 / 制表符 / 箭头等：原版字体里是半角
        if ((c >= 0x2190 && c <= 0x21FF)      // 箭头
                || (c >= 0x2500 && c <= 0x257F)   // 制表符
                || (c >= 0x25A0 && c <= 0x25FF)   // 几何图形 ■ □ ▰ ▱ ◈
                || (c >= 0x2600 && c <= 0x27BF)   // 杂项符号 ✔ ✕
                || (c >= 0x2B00 && c <= 0x2BFF)) { // 杂项符号和箭头
            return 1;
        }
        return (c > 0xFF) ? 2 : 1;
    }

    /** 超过 maxVisual 宽度就截断并加省略号。 */
    public static String ellipsis(String s, int maxVisual) {
        if (s == null) {
            return "";
        }
        if (visualWidth(s) <= maxVisual) {
            return s;
        }
        String plain = strip(s);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            int cw = (c > 0xFF) ? 2 : 1;
            if (w + cw > maxVisual - 1) {
                break;
            }
            sb.append(c);
            w += cw;
        }
        return sb + "…";
    }

    /** & → §。 */
    public static String colorize(String s) {
        return s == null ? "" : s.replace('&', '\u00a7');
    }
}
