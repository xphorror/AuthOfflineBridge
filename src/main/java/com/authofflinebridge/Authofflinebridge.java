package com.authofflinebridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Authofflinebridge implements ModInitializer {

    public static final String MODID = "authofflinebridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            var serverDir = server.getServerDirectory();
            var userCacheFile = serverDir.resolve("usercache.json");
            var manualConfigFile = serverDir.resolve("config").resolve("authofflinebridge-uuids.json");

            UserCacheLoader.init(userCacheFile, manualConfigFile);
            UserCacheLoader.printMappings();
        });

        OfflineBridgeHandler.register();
    }
}
