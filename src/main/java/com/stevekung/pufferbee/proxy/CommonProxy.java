package com.stevekung.pufferbee.proxy;

import com.stevekung.pufferbee.events.CommonEvents;
import com.stevekung.stevekungslib.utils.CommonUtils;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public class CommonProxy
{
    public void init()
    {
        CommonUtils.registerModEventBus(this);
        CommonUtils.registerEventHandler(new CommonEvents());
        CommonUtils.addModListener(this::commonSetup);
        CommonUtils.addModListener(this::clientRegistries);
    }

    public void commonSetup(FMLCommonSetupEvent event)
    {
    }

    public void clientRegistries(FMLClientSetupEvent event)
    {

    }
}