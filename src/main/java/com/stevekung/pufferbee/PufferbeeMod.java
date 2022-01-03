package com.stevekung.pufferbee;

import com.stevekung.pufferbee.config.BestKunGConfig;
import com.stevekung.pufferbee.proxy.ClientProxy;
import com.stevekung.pufferbee.proxy.CommonProxy;
import com.stevekung.stevekungslib.utils.CommonRegistryUtils;
import com.stevekung.stevekungslib.utils.CommonUtils;
import com.stevekung.stevekungslib.utils.LoggerBase;

import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(PufferbeeMod.MOD_ID)
public class PufferbeeMod
{
    public static final String MOD_ID = "pufferbee";
    public static final CommonRegistryUtils COMMON = new CommonRegistryUtils(MOD_ID);
    public static final LoggerBase LOGGER = new LoggerBase("Pufferbee");
    public static CommonProxy PROXY;

    public PufferbeeMod()
    {
        CommonUtils.registerConfig(ModConfig.Type.COMMON, BestKunGConfig.GENERAL_BUILDER);
        CommonUtils.registerModEventBus(BestKunGConfig.class);
        PROXY = DistExecutor.safeRunForDist(() -> ClientProxy::new, () -> CommonProxy::new);
        PROXY.init();
        COMMON.registerAll();
    }
}