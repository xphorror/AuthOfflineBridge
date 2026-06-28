package com.authofflinebridge;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class OfflineBridgeHandler {

    private OfflineBridgeHandler() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> logPlayerUuid(handler.player));
    }

    private static void logPlayerUuid(ServerPlayer player) {
        String playerName = player.getName().getString();
        var currentUUID = player.getUUID();
        var expectedOfflineUUID = UserCacheLoader.getOfflineUUID(playerName);
        var onlineUUID = UserCacheLoader.getOnlineUUIDMap().get(playerName);

        if (currentUUID.equals(expectedOfflineUUID)) {
            Authofflinebridge.LOGGER.warn("{} still has OFFLINE UUID: {} (bridge may have failed)", playerName, currentUUID);
        } else if (onlineUUID != null && currentUUID.equals(onlineUUID)) {
            Authofflinebridge.LOGGER.info("{} has ONLINE UUID: {} (bridge successful)", playerName, currentUUID);
        } else {
            Authofflinebridge.LOGGER.info("{} logged in with UUID: {}", playerName, currentUUID);
        }
    }
}
