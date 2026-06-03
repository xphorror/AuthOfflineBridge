package com.authofflinebridge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Authofflinebridge.MODID)
public class Authofflinebridge {

    public static final String MODID = "authofflinebridge";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Authofflinebridge() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        var serverDir = event.getServer().getServerDirectory().toPath();
        var userCacheFile = serverDir.resolve("usercache.json");
        var manualConfigFile = serverDir.resolve("config").resolve("authofflinebridge-uuids.json");
        UserCacheLoader.init(userCacheFile, manualConfigFile);
        UserCacheLoader.printMappings();
    }
}
