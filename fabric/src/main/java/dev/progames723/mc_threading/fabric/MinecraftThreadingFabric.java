package dev.progames723.mc_threading.fabric;

import dev.progames723.mc_threading.MinecraftThreading;
import net.fabricmc.api.ModInitializer;

public class MinecraftThreadingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MinecraftThreading.init();
    }
}