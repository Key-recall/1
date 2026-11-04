package com.example.itemframehud;

import com.example.itemframehud.hud.ItemFrameHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class ItemFrameHudMod implements ClientModInitializer {
    public static final String MOD_ID = "itemframe-hud";

    @Override
    public void onInitializeClient() {
        ItemFrameHudRenderer renderer = new ItemFrameHudRenderer();
        HudRenderCallback.EVENT.register(renderer);
    }
}
