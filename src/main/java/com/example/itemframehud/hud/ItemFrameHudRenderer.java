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
 * 准星指向隐形 ItemFrame（展示框）时，在屏幕指定位置显示其承载物品名。
 * 支持：颜色代码还原、完整 tooltip、可调位置/大小、背景透明度。
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

        ModConfig cfg = ModConfig.get();

        // 组装要显示的行
        List<String> lines = new ArrayList<>();
        if (cfg.showTooltip) {
            // 完整 tooltip
            List<Text> tooltip = stack.getTooltip(
                    net.minecraft.item.Item.TooltipContext.DEFAULT,
                    client.player,
                    net.minecraft.item.tooltip.TooltipType.BASIC
            );
            for (Text t : tooltip) {
                lines.add(serialize(t, cfg));
            }
        } else {
            // 只显示名字
            lines.add(serialize(stack.getName(), cfg));
        }

        renderLabel(drawContext, client, lines, cfg);
    }

    /** 根据配置决定是否保留颜色代码 */
    private String serialize(Text text, ModConfig cfg) {
        if (cfg.showColor) {
            return TextSerializer.toLegacy(text);
        }
        return text.getString();
    }

    private ItemFrameEntity findTargetItemFrame(MinecraftClient client, PlayerEntity player) {
        HitResult hit = client.crosshairTarget;

        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            if (entity instanceof ItemFrameEntity frame) {
                return frame;
            }
        }

        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            ItemFrameEntity nearby = findNearestItemFrame(client, hitPos);
            if (nearby != null) {
                return nearby;
            }
        }

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

        // 文字保持原始大小，scale 影响内边距（避免矩阵缩放导致 1.21.11 编译问题）
        int padding = (int) (6 * cfg.labelScale);
        int lineHeight = client.textRenderer.fontHeight;
        int lineSpacing = (int) (2 * cfg.labelScale);

        // 计算每行宽度和总宽高
        int maxWidth = 0;
        for (String line : lines) {
            int w = client.textRenderer.getWidth(line);
            if (w > maxWidth) {
                maxWidth = w;
            }
        }
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = lineHeight * lines.size() + lineSpacing * (lines.size() - 1) + padding * 2;

        // 根据百分比计算位置
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int x = (int) (screenWidth * cfg.labelXPercent / 100.0) - boxWidth / 2;
        int y = (int) (screenHeight * cfg.labelYPercent / 100.0) - boxHeight / 2;

        // 半透明背景（透明度可调）
        int bgColor = (cfg.backgroundAlpha << 24) | 0x000000;
        context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        // 逐行渲染
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
