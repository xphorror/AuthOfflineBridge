package com.authofflinebridge.mixin;

import com.authofflinebridge.Authofflinebridge;
import com.authofflinebridge.UserCacheLoader;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.UUID;

@Mixin(ServerLoginPacketListenerImpl.class)
public class PlayerLoginMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Redirect(
            method = "handleHello",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/UUIDUtil;createOfflineProfile(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;"
            )
    )
    private GameProfile authofflinebridge$replaceOfflineUUID(String playerName) {
        Map<String, UUID> onlineMap = UserCacheLoader.getOnlineUUIDMap();
        UUID onlineUUID = onlineMap.get(playerName);

        if (onlineUUID != null) {
            UserCacheLoader.migrateOfflinePlayerFiles(this.server, playerName, onlineUUID);
            Authofflinebridge.LOGGER.info("Replaced offline UUID with online UUID for {}: {}", playerName, onlineUUID);
            return new GameProfile(onlineUUID, playerName);
        }

        return UUIDUtil.createOfflineProfile(playerName);
    }
}
