package com.example.itemframehud.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.minecraft.client.gui.screen.Screen;

@Config(name = "itemframe-hud")
public class ModConfig implements ConfigData {

    /** 参照点：0=左上 1=上中 2=右上 3=左中 4=中心 5=右中 6=左下 7=下中 8=右下 */
    @ConfigEntry.BoundedDiscrete(min = 0, max = 8)
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public int anchor = 4;

    /** 相对参照点的水平偏移（像素，正数向右） */
    public int offsetX = 0;

    /** 相对参照点的垂直偏移（像素，正数向下） */
    public int offsetY = 0;

    /** 文字缩放（1.0 为默认大小） */
    @ConfigEntry.BoundedDiscrete(min = 0, max = 300)
    public int scalePercent = 100;

    /** 背景透明度（0~255） */
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
