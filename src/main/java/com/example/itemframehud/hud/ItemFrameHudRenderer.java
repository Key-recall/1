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

import java.util.ArrayList;
import java.util.List;

/**
 * 诊断版：扫描玩家周围所有实体，打印实体类型、坐标、是否隐形，
 * 直接显示在屏幕左上角，用于确定酒水到底是什么实体。
 */
public class ItemFrameHudRenderer implements HudRenderCallback {

    private static final double SCAN_RADIUS = 3.0D;
    private static final double RAYCAST_DISTANCE = 8.0D;
    private static final double DIAG_RADIUS = 20.0D;

    private int diagTick = 0;

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        // 诊断：每 60 帧（约 3 秒）无条件发到聊天栏 + 屏幕
        if (diagTick++ % 60 == 0) {
            dumpEntities(client);
        }
        renderEntityDiagnostic(drawContext, client);

        // 正常功能
        PlayerEntity player = client.player;
        ItemFrameEntity target = findTargetItemFrame(client, player);
        if (target == null) {
            return;
        }
        ItemStack stack = target.getHeldItemStack();
        if (stack.isEmpty()) {
            return;
        }
        String text = stack.getName().getString();
        if (text == null || text.isEmpty()) {
            return;
        }
        renderLabel(drawContext, client, text);
    }

    /** 无条件把周围实体发到聊天栏（一定能看到） */
    private void dumpEntities(MinecraftClient client) {
        try {
            Vec3d cam = client.player.getEyePos();
            Box box = new Box(
                    cam.x - DIAG_RADIUS, cam.y - DIAG_RADIUS, cam.z - DIAG_RADIUS,
                    cam.x + DIAG_RADIUS, cam.y + DIAG_RADIUS, cam.z + DIAG_RADIUS
            );
            List<Entity> entities = client.world.getEntitiesByClass(Entity.class, box, e -> e != client.player);
            client.player.sendMessage(Text.literal("[诊断] 周围实体数: " + entities.size()), false);
            int n = 0;
            for (Entity e : entities) {
                if (n >= 8) {
                    client.player.sendMessage(Text.literal("[诊断] ... 还有 " + (entities.size() - 8) + " 个"), false);
                    break;
                }
                String type = e.getType().toString();
                String pos = (int) e.getX() + "," + (int) e.getY() + "," + (int) e.getZ();
                String extra = "";
                if (e instanceof ItemFrameEntity f) {
                    ItemStack s = f.getHeldItemStack();
                    extra = s.isEmpty() ? " 空框" : " 物品=" + s.getName().getString();
                }
                client.player.sendMessage(Text.literal("[诊断] " + type + " @" + pos + " inv=" + e.isInvisible() + extra), false);
                n++;
            }
        } catch (Throwable t) {
            client.player.sendMessage(Text.literal("[诊断] 出错: " + t.getMessage()), false);
        }
    }

    /** 诊断：列出玩家周围 20 格内所有实体 */
    private void renderEntityDiagnostic(DrawContext context, MinecraftClient client) {
        Vec3d cam = client.player.getEyePos();
        Box box = new Box(
                cam.x - DIAG_RADIUS, cam.y - DIAG_RADIUS, cam.z - DIAG_RADIUS,
                cam.x + DIAG_RADIUS, cam.y + DIAG_RADIUS, cam.z + DIAG_RADIUS
        );

        // 扫描所有实体类型
        List<Entity> entities = client.world.getEntitiesByClass(Entity.class, box, e -> e != client.player);

        List<String> lines = new ArrayList<>();
        lines.add("[诊断] 周围实体总数: " + entities.size());
        int count = 0;
        for (Entity e : entities) {
            if (count >= 20) {
                lines.add("... (还有更多)");
                break;
            }
            String type = e.getType().getName().getString();
            String pos = (int) e.getX() + "," + (int) e.getY() + "," + (int) e.getZ();
            String inv = e.isInvisible() ? "隐形" : "";
            String extra = "";
            if (e instanceof ItemFrameEntity f) {
                ItemStack s = f.getHeldItemStack();
                extra = s.isEmpty() ? "(空框)" : "物品=" + s.getName().getString();
            }
            lines.add(type + " @" + pos + " " + inv + " " + extra);
            count++;
        }

        int y = 15;
        for (String line : lines) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(line), 5, y, 0x00FF00);
            y += 11;
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
