package com.example.itemframehud.util;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 把 Text 对象还原成带 § 颜色代码的字符串，以便 TextRenderer 渲染时保留颜色和格式。
 */
public final class TextSerializer {

    private TextSerializer() {}

    /** 把 Text 及其所有兄弟节点序列化为带 § 代码的字符串 */
    public static String toLegacy(Text text) {
        StringBuilder sb = new StringBuilder();
        append(text, sb);
        return sb.toString();
    }

    private static void append(Text text, StringBuilder sb) {
        // 先输出当前节点的样式代码
        String styleCodes = styleToLegacy(text.getStyle());
        sb.append(styleCodes);
        // 输出文本内容
        sb.append(text.getString());
        // 递归处理兄弟节点（每个兄弟会重新输出自己的样式）
        for (Text sibling : text.getSiblings()) {
            append(sibling, sb);
        }
    }

    /** 把 Style 转成 § 代码前缀（颜色 + 格式） */
    private static String styleToLegacy(Style style) {
        StringBuilder codes = new StringBuilder();
        if (style.getColor() != null) {
            Formatting color = colorToFormatting(style.getColor().getRgb());
            if (color != null) {
                codes.append('§').append(color.getCode());
            }
        }
        if (style.isBold()) codes.append("§l");
        if (style.isItalic()) codes.append("§o");
        if (style.isUnderlined()) codes.append("§n");
        if (style.isStrikethrough()) codes.append("§m");
        if (style.isObfuscated()) codes.append("§k");
        return codes.toString();
    }

    /** 把 RGB 颜色近似映射到最接近的 Formatting 颜色代码 */
    private static Formatting colorToFormatting(int rgb) {
        Formatting best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Formatting f : Formatting.values()) {
            if (!f.isColor()) {
                continue;
            }
            Integer c = f.getColorValue();
            if (c == null) {
                continue;
            }
            int dist = colorDistance(rgb, c);
            if (dist < bestDist) {
                bestDist = dist;
                best = f;
            }
        }
        return best;
    }

    private static int colorDistance(int a, int b) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int dr = ar - br, dg = ag - bg, db = ab - bb;
        return dr * dr + dg * dg + db * db;
    }
}
