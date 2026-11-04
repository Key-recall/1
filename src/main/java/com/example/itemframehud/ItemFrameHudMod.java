package com.example.itemframehud;

import com.example.itemframehud.config.ModConfig;
import com.example.itemframehud.hud.ItemFrameHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class ItemFrameHudMod implements ClientModInitializer {
    public static final String MOD_ID = "itemframe-hud";

    @Override
    public void onInitializeClient() {
        // 先注册 HUD 回调（核心功能，绝不能因为配置失败而中断）
        ItemFrameHudRenderer renderer = new ItemFrameHudRenderer();
        HudRenderCallback.EVENT.register(renderer);

        // 再注册配置（即使失败也不影响 HUD）
        try {
            ModConfig.register();
        } catch (Throwable t) {
            System.err.println("[ItemFrameHUD] 配置注册失败: " + t);
        }
    }
}
