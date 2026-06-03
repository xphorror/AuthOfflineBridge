package com.authofflinebridge.mixin;

import com.authofflinebridge.UserCacheLoader;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

/**
 * 注入到服务器登录流程中，在离线UUID生成时替换为正版UUID。
 * 拦截 createFakeProfile 方法，该方法在离线模式下为玩家生成UUID。
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public class PlayerLoginMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 拦截 createFakeProfile，在生成离线UUID前检查是否有正版UUID。
     * 如果有，直接返回带正版UUID的GameProfile；否则走原始逻辑。
     */
    @Inject(method = "createFakeProfile", at = @At("HEAD"), cancellable = true)
    private void authofflinebridge$replaceOfflineUUID(GameProfile original, CallbackInfoReturnable<GameProfile> cir) {
        String playerName = original.getName();
        Map<String, UUID> onlineMap = UserCacheLoader.getOnlineUUIDMap();
        UUID onlineUUID = onlineMap.get(playerName);

        if (onlineUUID != null) {
            LOGGER.info("[AuthOfflineBridge] Replaced offline UUID with online UUID for {}: {}", playerName, onlineUUID);
            cir.setReturnValue(new GameProfile(onlineUUID, playerName));
        }
    }
}
