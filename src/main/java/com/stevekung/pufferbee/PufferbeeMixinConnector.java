package com.stevekung.pufferbee;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class PufferbeeMixinConnector implements IMixinConnector
{
    @Override
    public void connect()
    {
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.pufferbee.json");
    }
}