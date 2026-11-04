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
 * 准星指向隐形 ItemFrame（展示框）时，在屏幕中央下方显示其承载物品名。
 * 同时把玩家周围所有 ItemFrameEntity 的坐标和物品名打印到日志，便于诊断。
 */
public class ItemFrameHudRenderer implements HudRenderCallback {

    private static final double SCAN_RADIUS = 3.0D;
    private static final double RAYCAST_DISTANCE = 8.0D;
    private static final double LOG_SCAN_RADIUS = 24.0D;

    // 每 100 帧打印一次诊断日志，避免刷屏
    private int logCooldown = 0;

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

        // 周期性诊断日志
        logCooldown++;
        if (logCooldown >= 100) {
            logCooldown = 0;
            dumpItemFrames(client);
        }

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

    /** 把周围所有 ItemFrameEntity 打印到日志，用于诊断数据到底在哪 */
    private void dumpItemFrames(MinecraftClient client) {
        Vec3d cam = client.player.getEyePos();
        Box box = new Box(
                cam.x - LOG_SCAN_RADIUS, cam.y - LOG_SCAN_RADIUS, cam.z - LOG_SCAN_RADIUS,
                cam.x + LOG_SCAN_RADIUS, cam.y + LOG_SCAN_RADIUS, cam.z + LOG_SCAN_RADIUS
        );
        List<ItemFrameEntity> frames = client.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true);
        System.out.println("[ItemFrameHUD] 周围 ItemFrame 数量: " + frames.size());
        for (ItemFrameEntity f : frames) {
            ItemStack s = f.getHeldItemStack();
            String nm = s.isEmpty() ? "(空)" : s.getName().getString();
            System.out.println("[ItemFrameHUD] ItemFrame @ " + f.getBlockX() + "," + f.getBlockY() + "," + f.getBlockZ()
                    + " invisible=" + f.isInvisible() + " item=" + nm);
        }
    }

    private ItemFrameEntity findTargetItemFrame(MinecraftClient client, PlayerEntity player) {
        HitResult hit = client.crosshairTarget;

        // 情况 1：直接命中实体
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            if (entity instanceof ItemFrameEntity frame) {
                return frame;
            }
        }

        // 情况 2：命中方块，在命中点附近找 ItemFrame
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            ItemFrameEntity nearby = findNearestItemFrame(client, hitPos);
            if (nearby != null) {
                return nearby;
            }
        }

        // 情况 3：沿视线方向扫描最近的 ItemFrame
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
