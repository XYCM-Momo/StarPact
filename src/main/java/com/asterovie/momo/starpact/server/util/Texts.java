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

    /** 按「视觉宽度」算长度：中文算 2，其余算 1。用于截断标题。 */
    public static int visualWidth(String s) {
        String plain = strip(s);
        int w = 0;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            w += (c > 0xFF) ? 2 : 1;
        }
        return w;
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
