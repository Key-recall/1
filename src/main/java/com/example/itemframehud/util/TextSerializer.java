package com.example.itemframehud.util;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 把 Text 对象还原成带 § 颜色代码的字符串。
 * 使用 Text.visit 正确遍历所有内容节点及其样式，避免文字重复。
 * 支持精确 RGB 颜色（§x hex 代码）和标准格式代码。
 */
public final class TextSerializer {

    private TextSerializer() {}

    /** 把 Text 及其所有内容节点序列化为带 § 代码的字符串 */
    public static String toLegacy(Text text) {
        StringBuilder sb = new StringBuilder();
        final Style[] lastStyle = new Style[1];

        text.visit((style, content) -> {
            sb.append(styleDiff(lastStyle[0], style));
            lastStyle[0] = style;
            sb.append(content);
            return java.util.Optional.empty();
        }, Style.EMPTY);

        return sb.toString();
    }

    /** 输出从 old 到 new 的样式差异 */
    private static String styleDiff(Style old, Style newStyle) {
        if (old == null) {
            old = Style.EMPTY;
        }
        StringBuilder codes = new StringBuilder();

        // 颜色变化
        if (!java.util.Objects.equals(old.getColor(), newStyle.getColor())) {
            if (newStyle.getColor() != null) {
                codes.append(rgbToLegacy(newStyle.getColor().getRgb()));
            } else {
                codes.append("§r");
            }
        }

        // 格式变化
        appendFormatChange(codes, old.isBold(), newStyle.isBold(), 'l');
        appendFormatChange(codes, old.isItalic(), newStyle.isItalic(), 'o');
        appendFormatChange(codes, old.isUnderlined(), newStyle.isUnderlined(), 'n');
        appendFormatChange(codes, old.isStrikethrough(), newStyle.isStrikethrough(), 'm');
        appendFormatChange(codes, old.isObfuscated(), newStyle.isObfuscated(), 'k');

        return codes.toString();
    }

    private static void appendFormatChange(StringBuilder codes, boolean oldV, boolean newV, char code) {
        if (oldV != newV && newV) {
            codes.append('§').append(code);
        }
    }

    /**
     * 把 RGB 颜色转成 § 代码：
     * 优先用精确的 §x hex 代码（1.16+ 支持），保证颜色完全一致。
     */
    private static String rgbToLegacy(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // 先检查是否是标准 16 色（用短代码更简洁）
        Formatting std = nearestColor(r, g, b);
        if (std != null) {
            int c = std.getColorValue();
            if (c == rgb || (r == ((c >> 16) & 0xFF) && g == ((c >> 8) & 0xFF) && b == (c & 0xFF))) {
                return "§" + std.getCode();
            }
        }

        // 否则用精确 hex 代码：§x§R§R§G§G§B§B
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder("§x");
        sb.append('§').append(hex[(r >> 4) & 0xF]).append('§').append(hex[r & 0xF]);
        sb.append('§').append(hex[(g >> 4) & 0xF]).append('§').append(hex[g & 0xF]);
        sb.append('§').append(hex[(b >> 4) & 0xF]).append('§').append(hex[b & 0xF]);
        return sb.toString();
    }

    /** 找最接近的标准颜色（用于判断是否能用短代码） */
    private static Formatting nearestColor(int r, int g, int b) {
        Formatting best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Formatting f : Formatting.values()) {
            if (!f.isColor() || f.getColorValue() == null) {
                continue;
            }
            int c = f.getColorValue();
            int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
            int dr = r - cr, dg = g - cg, db = b - cb;
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = f;
            }
        }
        return best;
    }
}
