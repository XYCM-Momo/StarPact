package com.asterovie.momo.starpact.server.util;

import java.util.regex.Pattern;

/**
 * 文本工具：去色码、视觉宽度、截断、颜色转换。
 */
public final class Texts {
    private static final Pattern SECTION = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern AMP = Pattern.compile("(?i)&[0-9A-FK-ORX]");

    private Texts() {
    }

    public static String strip(String string) {
        if (string == null || string.isEmpty()) {
            return "";
        }
        String string2 = SECTION.matcher(string).replaceAll("");
        string2 = AMP.matcher(string2).replaceAll("");
        return string2;
    }

    public static int visualWidth(String string) {
        String string2 = Texts.strip(string);
        int n = 0;
        for (int i = 0; i < string2.length(); ++i) {
            char c = string2.charAt(i);
            n += c > 'ÿ' ? 2 : 1;
        }
        return n;
    }

    public static String ellipsis(String string, int n) {
        if (string == null) {
            return "";
        }
        if (Texts.visualWidth(string) <= n) {
            return string;
        }
        String string2 = Texts.strip(string);
        StringBuilder stringBuilder = new StringBuilder();
        int n2 = 0;
        for (int i = 0; i < string2.length(); ++i) {
            char c = string2.charAt(i);
            int n3 = c > 'ÿ' ? 2 : 1;
            int n4 = n3;
            if (n2 + n3 > n - 1) break;
            stringBuilder.append(c);
            n2 += n3;
        }
        return String.valueOf(stringBuilder) + "…";
    }

    public static String colorize(String string) {
        return string == null ? "" : string.replace('&', '§');
    }
}

