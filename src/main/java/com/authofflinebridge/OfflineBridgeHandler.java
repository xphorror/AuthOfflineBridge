package com.authofflinebridge;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 玩家登录日志：验证UUID替换是否生效。
 * Mixin已在登录流程早期将离线UUID替换为正版UUID，
 * 此处仅做日志确认。
 */
@Mod.EventBusSubscriber(modid = Authofflinebridge.MODID)
public class OfflineBridgeHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String playerName = player.getName().getString();
        var currentUUID = player.getUUID();
        var expectedOfflineUUID = UserCacheLoader.getOfflineUUID(playerName);
        var onlineUUID = UserCacheLoader.getOnlineUUIDMap().get(playerName);

        if (currentUUID.equals(expectedOfflineUUID)) {
            LOGGER.warn("[AuthOfflineBridge] {} still has OFFLINE UUID: {} (bridge may have failed)", playerName, currentUUID);
        } else if (onlineUUID != null && currentUUID.equals(onlineUUID)) {
            LOGGER.info("[AuthOfflineBridge] {} has ONLINE UUID: {} (bridge successful)", playerName, currentUUID);
        } else {
            LOGGER.info("[AuthOfflineBridge] {} logged in with UUID: {}", playerName, currentUUID);
        }
    }
}
