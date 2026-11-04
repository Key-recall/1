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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 准星指向隐形 ItemFrame 时显示物品名。
 * 诊断模式：在屏幕左侧列出玩家周围所有 ItemFrameEntity（含隐形）的坐标和物品名，
 * 用于确认酒水数据到底在哪。
 */
public class ItemFrameHudRenderer implements HudRenderCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger("itemframe-hud");
    private static final double SCAN_RADIUS = 3.0D;
    private static final double RAYCAST_DISTANCE = 8.0D;
    private static final double DIAG_SCAN_RADIUS = 16.0D;

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        PlayerEntity player = client.player;

        // 诊断：在屏幕左上角列出周围所有 ItemFrame
        renderDiagnostic(drawContext, client, player);

        // 正常功能：准星指向时显示物品名
        ItemFrameEntity target = findTargetItemFrame(client, player);
        if (target != null) {
            ItemStack stack = target.getHeldItemStack();
            if (!stack.isEmpty()) {
                String text = stack.getName().getString();
                if (text != null && !text.isEmpty()) {
                    renderLabel(drawContext, client, text);
                }
            }
        }
    }

    /** 诊断：列出玩家周围 16 格内所有 ItemFrameEntity */
    private void renderDiagnostic(DrawContext context, MinecraftClient client, PlayerEntity player) {
        Vec3d cam = player.getEyePos();
        Box box = new Box(
                cam.x - DIAG_SCAN_RADIUS, cam.y - DIAG_SCAN_RADIUS, cam.z - DIAG_SCAN_RADIUS,
                cam.x + DIAG_SCAN_RADIUS, cam.y + DIAG_SCAN_RADIUS, cam.z + DIAG_SCAN_RADIUS
        );
        List<ItemFrameEntity> frames = client.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true);

        // 同时打印到日志
        LOGGER.info("[诊断] 周围 ItemFrame 数量: {}", frames.size());

        List<String> lines = new ArrayList<>();
        lines.add("[诊断] ItemFrame 数量: " + frames.size());
        for (ItemFrameEntity f : frames) {
            ItemStack s = f.getHeldItemStack();
            String nm = s.isEmpty() ? "(空)" : s.getName().getString();
            String line = f.getBlockX() + "," + f.getBlockY() + "," + f.getBlockZ()
                    + " inv=" + f.isInvisible() + " " + nm;
            lines.add(line);
            LOGGER.info("[诊断] ItemFrame @ {} inv={} item={}", f.getBlockPos(), f.isInvisible(), nm);
        }

        // 渲染到屏幕左上角
        int y = 20;
        for (String line : lines) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(line), 10, y, 0xFFFFFF);
            y += 12;
        }
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

    private void renderLabel(DrawContext context, MinecraftClient client, String text) {
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        int textWidth = client.textRenderer.getWidth(text);
        int padding = 6;
        int boxWidth = textWidth + padding * 2;
        int boxHeight = client.textRenderer.fontHeight + padding * 2;

        int x = (width - boxWidth) / 2;
        int y = (int) (height * 0.62) - boxHeight / 2;

        context.fill(x, y, x + boxWidth, y + boxHeight, 0xAA000000);
        context.fill(x, y, x + boxWidth, y + 1, 0xFFFFFFFF);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFFFFFFFF);
        context.fill(x, y, x + 1, y + boxHeight, 0xFFFFFFFF);
        context.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFFFFFFFF);

        context.drawTextWithShadow(
                client.textRenderer,
                Text.literal(text),
                x + padding,
                y + padding,
                0xFFFFFFFF
        );
    }
}
