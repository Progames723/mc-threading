package dev.progames723.mc_threading.forge;

import dev.progames723.mc_threading.MinecraftThreading;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MinecraftThreading.MOD_ID)
public class MinecraftThreadingForge {
    public MinecraftThreadingForge() {
        MinecraftThreading.init();
    }
}