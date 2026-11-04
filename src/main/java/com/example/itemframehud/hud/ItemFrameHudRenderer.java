package com.example.itemframehud.hud;

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

import java.util.List;

/**
 * 每帧渲染时，检测准星指向的隐形 Item Frame（展示框），
 * 并把其中承载物品的显示名绘制在屏幕中央下方。
 */
public class ItemFrameHudRenderer implements HudRenderCallback {

    /** 扫描附近实体时的搜索半径（格） */
    private static final double SCAN_RADIUS = 1.5D;

    /** 射线最大距离 */
    private static final double RAYCAST_DISTANCE = 8.0D;

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        // 调试屏(F3)打开时不显示，避免遮挡
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

        Text name = stack.getName();
        String text = name.getString();
        if (text == null || text.isEmpty()) {
            return;
        }

        renderLabel(drawContext, client, text);
    }

    /**
     * 1) 先看准星直接命中的实体是否为 ItemFrame；
     * 2) 否则看命中的方块（假活板门/珊瑚扇）附近是否有 ItemFrame；
     * 3) 再不行，扫描玩家视线路径上近距离的 ItemFrame。
     */
    private ItemFrameEntity findTargetItemFrame(MinecraftClient client, PlayerEntity player) {
        HitResult hit = client.crosshairTarget;
        if (hit == null) {
            return null;
        }

        // 情况 1：直接命中实体
        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            if (entity instanceof ItemFrameEntity frame) {
                return frame;
            }
        }

        // 情况 2：命中方块，在命中点附近找 ItemFrame
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            ItemFrameEntity nearby = findNearestItemFrame(client, hitPos);
            if (nearby != null) {
                return nearby;
            }
        }

        // 情况 3：沿视线方向做一次自定义射线，找近距离的 ItemFrame
        return raycastForItemFrame(client, player);
    }

    /** 在给定位置附近 SCAN_RADIUS 格内找最近的 ItemFrame */
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

    /** 沿玩家视线方向做射线，命中 ItemFrame 则返回 */
    private ItemFrameEntity raycastForItemFrame(MinecraftClient client, PlayerEntity player) {
        // 相机位置：玩家眼睛位置（基础方法，跨版本稳定）
        Vec3d start = player.getEyePos();
        // 视线方向：由玩家的 pitch/yaw 计算
        Vec3d dir = Vec3d.fromPolar(player.getPitch(), player.getYaw());
        Vec3d end = start.add(dir.multiply(RAYCAST_DISTANCE));

        Box rayBox = new Box(start, end).expand(1.0D);
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
        // 只接受足够贴近视线的展示框
        if (best != null && bestDist < 1.0D) {
            return best;
        }
        return null;
    }

    /** 计算实体到射线的近似距离 */
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

    /** 在屏幕中央下方绘制带背景的文字标签 */
    private void renderLabel(DrawContext context, MinecraftClient client, String text) {
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        int textWidth = client.textRenderer.getWidth(text);
        int padding = 6;
        int boxWidth = textWidth + padding * 2;
        int boxHeight = client.textRenderer.fontHeight + padding * 2;

        // 标签位置：屏幕中央下方约 1/4 屏高
        int x = (width - boxWidth) / 2;
        int y = (int) (height * 0.62) - boxHeight / 2;

        // 半透明黑底
        context.fill(x, y, x + boxWidth, y + boxHeight, 0xAA000000);
        // 边框
        context.fill(x, y, x + boxWidth, y + 1, 0xFFFFFFFF);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFFFFFFFF);
        context.fill(x, y, x + 1, y + boxHeight, 0xFFFFFFFF);
        context.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFFFFFFFF);

        // 文字
        context.drawTextWithShadow(
                client.textRenderer,
                Text.literal(text),
                x + padding,
                y + padding,
                0xFFFFFFFF
        );
    }
}
