package com.example.itemframehud.hud;

import com.example.itemframehud.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
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
 * 准星指向隐形展示框时显示物品名（带颜色）。
 * 编辑模式：F6 进入/退出，拖拽标签改变位置，拖拽右下角改变大小。
 */
public class ItemFrameHudRenderer implements HudRenderCallback {

    private static final double SCAN_RADIUS = 3.0D;
    private static final double RAYCAST_DISTANCE = 8.0D;

    private boolean editing = false;
    private boolean dragging = false;
    private boolean resizing = false;
    private int dragStartMouseX = 0;
    private int dragStartMouseY = 0;
    private int dragStartOffsetX = 0;
    private int dragStartOffsetY = 0;
    private int dragStartScale = 100;
    private boolean f6WasDown = false;

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        handleEditToggle(client);

        PlayerEntity player = client.player;
        ItemFrameEntity target = findTargetItemFrame(client, player);
        List<Text> lines = null;
        if (target != null) {
            ItemStack stack = target.getHeldItemStack();
            if (!stack.isEmpty()) {
                lines = new ArrayList<>();
                lines.add(stack.getName());
            }
        }

        if (editing) {
            renderEditOverlay(drawContext, client, lines);
        } else if (lines != null) {
            renderLabel(drawContext, client, lines);
        }
    }

    /** F6 切换编辑模式 */
    private void handleEditToggle(MinecraftClient client) {
        boolean f6Down = InputUtil.isKeyPressed(client.getWindow().getHandle(), InputUtil.GLFW_KEY_F6);
        boolean pressed = f6Down && !f6WasDown;
        f6WasDown = f6Down;

        if (pressed) {
            editing = !editing;
            if (editing) {
                client.player.sendMessage(Text.literal("[ItemFrameHUD] 编辑模式：拖动标签移动位置，拖红色手柄改大小，F6 保存退出"), false);
            } else {
                client.player.sendMessage(Text.literal("[ItemFrameHUD] 已退出编辑模式"), false);
            }
        }
    }

    private void renderEditOverlay(DrawContext context, MinecraftClient client, List<Text> lines) {
        ModConfig cfg = ModConfig.get();
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int[] pos = calcPosition(client, lines, cfg);
        int x = pos[0], y = pos[1], w = pos[2], h = pos[3];

        handleDrag(client, x, y, w, h, cfg);

        pos = calcPosition(client, lines, cfg);
        x = pos[0]; y = pos[1]; w = pos[2]; h = pos[3];

        // 渲染内容
        if (lines != null && !lines.isEmpty()) {
            renderLabelAt(context, client, lines, x, y, w, h, cfg);
        } else {
            List<Text> preview = new ArrayList<>();
            preview.add(Text.literal("示例物品名"));
            renderLabelAt(context, client, preview, x, y, w, h, cfg);
        }

        // 黄色边框
        int border = 0xFFFFFF00;
        context.fill(x - 1, y - 1, x + w + 1, y, border);
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, border);
        context.fill(x - 1, y, x, y + h, border);
        context.fill(x + w, y, x + w + 1, y + h, border);

        // 右下角红色拖拽手柄
        int hs = 8;
        context.fill(x + w - hs, y + h - hs, x + w, y + h, 0xFFFF0000);

        context.drawTextWithShadow(client.textRenderer,
                Text.literal("编辑模式 (F6保存) 拖动移动 / 拖红角改大小"),
                5, screenH - 15, 0xFFFFFF);
    }

    private void handleDrag(MinecraftClient client, int x, int y, int w, int h, ModConfig cfg) {
        // 鼠标坐标（转换为 GUI 缩放坐标）
        double mx = client.mouse.getX();
        double my = client.mouse.getY();
        int scaledW = client.getWindow().getScaledWidth();
        int scaledH = client.getWindow().getScaledHeight();
        int winW = client.getWindow().getWidth();
        int winH = client.getWindow().getHeight();
        int mouseX = (int) (mx * scaledW / winW);
        int mouseY = (int) (my * scaledH / winH);

        boolean mouseDown = client.options.attackKey.isPressed();

        int hs = 8;
        boolean onHandle = mouseX >= x + w - hs && mouseX <= x + w && mouseY >= y + h - hs && mouseY <= y + h;
        boolean onBody = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        if (mouseDown && !dragging && !resizing) {
            if (onHandle) {
                resizing = true;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragStartScale = cfg.scalePercent;
            } else if (onBody) {
                dragging = true;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragStartOffsetX = cfg.offsetX;
                dragStartOffsetY = cfg.offsetY;
            }
        }

        if (!mouseDown) {
            dragging = false;
            resizing = false;
        }

        if (dragging) {
            cfg.offsetX = dragStartOffsetX + (mouseX - dragStartMouseX);
            cfg.offsetY = dragStartOffsetY + (mouseY - dragStartMouseY);
        }

        if (resizing) {
            int delta = (mouseX - dragStartMouseX) + (dragStartMouseY - mouseY);
            int newScale = dragStartScale + delta / 2;
            cfg.scalePercent = Math.max(30, Math.min(300, newScale));
        }
    }

    private int[] calcPosition(MinecraftClient client, List<Text> lines, ModConfig cfg) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        float scale = cfg.scalePercent / 100.0F;
        int padding = (int) (6 * scale);
        int lineHeight = client.textRenderer.fontHeight;
        int lineSpacing = (int) (2 * scale);

        int maxWidth = 0;
        int lineCount = 1;
        if (lines != null && !lines.isEmpty()) {
            lineCount = lines.size();
            for (Text line : lines) {
                int w = client.textRenderer.getWidth(line);
                if (w > maxWidth) {
                    maxWidth = w;
                }
            }
        } else {
            maxWidth = client.textRenderer.getWidth("示例物品名");
        }
        int boxW = maxWidth + padding * 2;
        int boxH = lineHeight * lineCount + lineSpacing * (lineCount - 1) + padding * 2;

        int anchorX = anchorX(screenW, cfg.anchor);
        int anchorY = anchorY(screenH, cfg.anchor);

        int x = anchorX + cfg.offsetX - boxW / 2;
        int y = anchorY + cfg.offsetY - boxH / 2;

        return new int[]{x, y, boxW, boxH};
    }

    private int anchorX(int screenW, int anchor) {
        switch (anchor) {
            case 0: case 3: case 6: return 0;
            case 1: case 4: case 7: return screenW / 2;
            default: return screenW;
        }
    }

    private int anchorY(int screenH, int anchor) {
        switch (anchor) {
            case 0: case 1: case 2: return 0;
            case 3: case 4: case 5: return screenH / 2;
            default: return screenH;
        }
    }

    private void renderLabel(DrawContext context, MinecraftClient client, List<Text> lines) {
        ModConfig cfg = ModConfig.get();
        int[] pos = calcPosition(client, lines, cfg);
        renderLabelAt(context, client, lines, pos[0], pos[1], pos[2], pos[3], cfg);
    }

    private void renderLabelAt(DrawContext context, MinecraftClient client, List<Text> lines, int x, int y, int boxW, int boxH, ModConfig cfg) {
        float scale = cfg.scalePercent / 100.0F;
        int padding = (int) (6 * scale);
        int lineHeight = client.textRenderer.fontHeight;
        int lineSpacing = (int) (2 * scale);

        int bgColor = (cfg.backgroundAlpha << 24) | 0x000000;
        context.fill(x, y, x + boxW, y + boxH, bgColor);

        int textY = y + padding;
        for (Text line : lines) {
            context.drawTextWithShadow(client.textRenderer, line, x + padding, textY, 0xFFFFFFFF);
            textY += lineHeight + lineSpacing;
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
}
