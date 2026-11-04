package com.example.itemframehud.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.minecraft.client.gui.screen.Screen;

@Config(name = "itemframe-hud")
public class ModConfig implements ConfigData {

    /** 标签缩放（默认 1.0，越大文字越大） */
    public float labelScale = 1.0F;

    /** 标签水平位置（屏幕宽度百分比，0.0~1.0，0.5 为居中） */
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int labelXPercent = 50;

    /** 标签垂直位置（屏幕高度百分比，0.0~1.0，0.62 为中央偏下） */
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int labelYPercent = 62;

    /** 是否显示物品的完整 tooltip（名字 + lore + 属性） */
    public boolean showTooltip = false;

    /** 是否渲染颜色（保留服务端设定的 § 颜色代码） */
    public boolean showColor = true;

    /** 背景透明度（0~255，255 为完全不透明） */
    @ConfigEntry.BoundedDiscrete(min = 0, max = 255)
    public int backgroundAlpha = 170;

    public static ModConfig get() {
        return AutoConfig.getConfigHolder(ModConfig.class).getConfig();
    }

    public static void register() {
        AutoConfig.register(ModConfig.class, JanksonConfigSerializer::new);
    }

    public static Screen createScreen(Screen parent) {
        return AutoConfig.getConfigScreen(ModConfig.class, parent).get();
    }
}
