package com.stevekung.pufferbee.config;

import com.stevekung.pufferbee.PufferbeeMod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.config.ModConfig;

public class BestKunGConfig
{
    public static final ForgeConfigSpec.Builder GENERAL_BUILDER = new ForgeConfigSpec.Builder();
    public static final BestKunGConfig.General GENERAL = new BestKunGConfig.General(BestKunGConfig.GENERAL_BUILDER);

    public static class General
    {
        public final ForgeConfigSpec.BooleanValue enableEatAll;
        public final ForgeConfigSpec.ConfigValue<Double> livingSlimeMotion;
        public final ForgeConfigSpec.BooleanValue allBlockSlime;

        private General(ForgeConfigSpec.Builder builder)
        {
            builder.comment("General settings")
            .push("general");

            this.enableEatAll = builder
                    .translation("Eat all item")
                    .define("enableEatAll", false);
            this.livingSlimeMotion = builder
                    .translation("Living Entity Slime Motion")
                    .define("livingSlimeMotion", 1.0D);
            this.allBlockSlime = builder
                    .translation("All Block Slime")
                    .define("allBlockSlime", false);
            builder.pop();
        }
    }

    @SubscribeEvent
    public static void onLoad(ModConfig.Loading event)
    {
        PufferbeeMod.LOGGER.info("Loaded config file {}", event.getConfig().getFileName());
    }

    @SubscribeEvent
    public static void onFileChange(ModConfig.Reloading event)
    {
        PufferbeeMod.LOGGER.info("BestKunG config just got changed on the file system");
    }
}