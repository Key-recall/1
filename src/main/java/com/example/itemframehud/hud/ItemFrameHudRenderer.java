package com.example.itemframehud.hud;

import com.example.itemframehud.config.ModConfig;
import com.example.itemframehud.util.TextSerializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 准星指向隐形展示框（含 GlowItemFrame，即发光展示框）时，
 * 在屏幕指定位置显示其承载物品名（带颜色）、可选完整 tooltip。
 * 位置、大小、背景透明度、tooltip、颜色均可通过配置界面调整。
 */
public class ItemFrameHudRenderer implements HudRenderCallback {

    private static final double SCAN_RADIUS = 3.0D;
    private static final double RAYCAST_DISTANCE = 8.0D;

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        if (client.getDebugHud().shouldShowDebugHud()) {
            return;
        }

        PlayerEntity player = client.player;
        ItemFrameEntity target = findTargetItemFrame(client, player);
        if (target == null) {
            return;
        }

        ItemStack stack = target.getHeldItemStack();
        if (stack.isEmpty()) {
            return;
        }

        // 安全获取配置
        ModConfig cfg;
        try {
            cfg = ModConfig.get();
        } catch (Throwable t) {
            cfg = null;
        }

        boolean showTooltip = cfg != null && cfg.showTooltip;
        boolean showColor = cfg == null || cfg.showColor;

        List<String> lines = new ArrayList<>();
        // 第一行：名字（用最稳妥的方式获取文本）
        String nameText;
        try {
            nameText = showColor ? TextSerializer.toLegacy(stack.getName()) : stack.getName().getString();
        } catch (Throwable t) {
            nameText = stack.getName().getString();
        }
        if (nameText == null || nameText.isEmpty()) {
            nameText = stack.getName().getString();
        }
        lines.add(nameText);

        // 可选：追加完整 tooltip 的后续行（lore、属性等）
        if (showTooltip) {
            try {
                List<Text> tooltip = stack.getTooltip(
                        net.minecraft.item.Item.TooltipContext.DEFAULT,
                        client.player,
                        net.minecraft.item.tooltip.TooltipType.BASIC
                );
                for (int i = 1; i < tooltip.size(); i++) {
                    lines.add(toDisplay(tooltip.get(i), showColor));
                }
            } catch (Throwable t) {
                // tooltip 失败就只用名字，不影响显示
            }
        }

        renderLabel(drawContext, client, lines, cfg);
    }

    /** 根据 showColor 决定是否还原颜色代码 */
    private String toDisplay(Text text, boolean showColor) {
        if (showColor) {
            try {
                String colored = TextSerializer.toLegacy(text);
                if (colored != null && !colored.isEmpty()) {
                    return colored;
                }
            } catch (Throwable ignored) {
            }
        }
        return text.getString();
    }

    private ItemFrameEntity findTargetItemFrame(MinecraftClient client, PlayerEntity player) {
        HitResult hit = client.crosshairTarget;

        // 情况 1：准星直接命中实体（对 GlowItemFrame 也有效，因为它是 ItemFrameEntity 子类）
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            if (entity instanceof ItemFrameEntity frame) {
                return frame;
            }
        }

        // 情况 2：命中方块，在命中点附近找展示框
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            ItemFrameEntity nearby = findNearestItemFrame(client, hitPos);
            if (nearby != null) {
                return nearby;
            }
        }

        // 情况 3：沿视线方向扫描
        return raycastForItemFrame(client, player);
    }

    private ItemFrameEntity findNearestItemFrame(MinecraftClient client, Vec3d center) {
        Box box = new Box(
                center.x - SCAN_RADIUS, center.y - SCAN_RADIUS, center.z - SCAN_RADIUS,
                center.x + SCAN_RADIUS, center.y + SCAN_RADIUS, center.z + SCAN_RADIUS
        );
        List<ItemFrameEntity> frames = client.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true);
        if (frames.isEmpty()) {
            return null;
        }
        ItemFrameEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemFrameEntity f : frames) {
            double dx = f.getX() - center.x;
            double dy = f.getY() - center.y;
            double dz = f.getZ() - center.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = f;
            }
        }
        return best;
    }

    private ItemFrameEntity raycastForItemFrame(MinecraftClient client, PlayerEntity player) {
        Vec3d start = player.getEyePos();
        Vec3d dir = Vec3d.fromPolar(player.getPitch(), player.getYaw());
        Vec3d end = start.add(dir.multiply(RAYCAST_DISTANCE));

        Box rayBox = new Box(start, end).expand(1.5D);
        List<ItemFrameEntity> frames = client.world.getEntitiesByClass(ItemFrameEntity.class, rayBox, e -> true);

        ItemFrameEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemFrameEntity f : frames) {
            double d = distanceToRay(f, start, dir);
            if (d < bestDist) {
                bestDist = d;
                best = f;
            }
        }
        if (best != null && bestDist < 1.5D) {
            return best;
        }
        return null;
    }

    private double distanceToRay(ItemFrameEntity entity, Vec3d rayStart, Vec3d rayDir) {
        Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        Vec3d toEntity = entityPos.subtract(rayStart);
        double t = toEntity.dotProduct(rayDir);
        if (t < 0) {
            return toEntity.length();
        }
        Vec3d closest = rayStart.add(rayDir.multiply(t));
        return closest.distanceTo(entityPos);
    }

    /** 渲染带背景的标签，支持位置、透明度、多行 */
    private void renderLabel(DrawContext context, MinecraftClient client, List<String> lines, ModConfig cfg) {
        if (lines.isEmpty()) {
            return;
        }

        float scale = cfg != null ? cfg.labelScale : 1.0F;
        int xPercent = cfg != null ? cfg.labelXPercent : 50;
        int yPercent = cfg != null ? cfg.labelYPercent : 62;
        int bgAlpha = cfg != null ? cfg.backgroundAlpha : 170;

        int padding = (int) (6 * scale);
        int lineHeight = client.textRenderer.fontHeight;
        int lineSpacing = (int) (2 * scale);

        int maxWidth = 0;
        for (String line : lines) {
            int w = client.textRenderer.getWidth(line);
            if (w > maxWidth) {
                maxWidth = w;
            }
        }
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = lineHeight * lines.size() + lineSpacing * (lines.size() - 1) + padding * 2;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int x = (int) (screenWidth * xPercent / 100.0) - boxWidth / 2;
        int y = (int) (screenHeight * yPercent / 100.0) - boxHeight / 2;

        int bgColor = (bgAlpha << 24) | 0x000000;
        context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        int textY = y + padding;
        for (String line : lines) {
            context.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(line),
                    x + padding,
                    textY,
                    0xFFFFFFFF
            );
            textY += lineHeight + lineSpacing;
        }
    }
}
