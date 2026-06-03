package com.authofflinebridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 加载并缓存玩家名与正版UUID的映射关系。
 * 优先级：手动配置文件 > usercache.json
 */
public class UserCacheLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 缓存的 name → 正版UUID 映射 */
    private static Map<String, UUID> cachedOnlineMap = new LinkedHashMap<>();

    /**
     * 初始化：先加载手动配置文件，再加载 usercache.json。
     * 手动配置优先级更高。
     *
     * @param userCacheFile usercache.json 路径
     * @param manualConfigFile 手动UUID映射文件路径 (config/authofflinebridge-uuids.json)
     */
    public static void init(Path userCacheFile, Path manualConfigFile) {
        cachedOnlineMap = new LinkedHashMap<>();

        // 1. 先加载手动配置文件（优先级最高）
        loadManualConfig(manualConfigFile);

        // 2. 再加载 usercache.json（不覆盖手动配置）
        loadUserCache(userCacheFile);

        // 3. 如果没有手动配置文件，自动创建模板
        if (!Files.exists(manualConfigFile)) {
            createTemplateConfig(manualConfigFile);
        }
    }

    /**
     * 加载手动UUID映射配置文件。
     * 格式：{"playerName": "uuid-v4", ...}
     */
    private static void loadManualConfig(Path configFile) {
        if (!Files.exists(configFile)) {
            LOGGER.info("[AuthOfflineBridge] Manual UUID config not found at: {}, will create template", configFile);
            return;
        }

        try {
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();

            int count = 0;
            for (var entry : obj.entrySet()) {
                String name = entry.getKey();
                String uuidStr = entry.getValue().getAsString();
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    cachedOnlineMap.put(name, uuid); // 手动配置直接覆盖
                    count++;
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[AuthOfflineBridge] Invalid UUID '{}' for player '{}' in manual config", uuidStr, name);
                }
            }

            LOGGER.info("[AuthOfflineBridge] Loaded {} manual UUID mappings from {}", count, configFile);
        } catch (Exception e) {
            LOGGER.error("[AuthOfflineBridge] Failed to read manual UUID config: {}", configFile, e);
        }
    }

    /**
     * 从 usercache.json 加载映射（跳过离线UUID，不覆盖手动配置）。
     */
    private static void loadUserCache(Path userCacheFile) {
        if (!Files.exists(userCacheFile)) {
            LOGGER.warn("[AuthOfflineBridge] usercache.json not found at: {}", userCacheFile);
            return;
        }

        try {
            String content = Files.readString(userCacheFile, StandardCharsets.UTF_8);
            JsonArray array = JsonParser.parseString(content).getAsJsonArray();

            int count = 0;
            for (JsonElement element : array) {
                var obj = element.getAsJsonObject();
                String name = obj.get("name").getAsString();
                String uuidStr = obj.get("uuid").getAsString();
                UUID uuid = UUID.fromString(uuidStr);

                // 跳过离线UUID（version 3）
                if (uuid.version() == 3) continue;

                // 不覆盖手动配置中已有的映射
                if (!cachedOnlineMap.containsKey(name)) {
                    cachedOnlineMap.put(name, uuid);
                    count++;
                }
            }

            LOGGER.info("[AuthOfflineBridge] Loaded {} online UUID mappings from usercache.json", count);
        } catch (Exception e) {
            LOGGER.error("[AuthOfflineBridge] Failed to read usercache.json", e);
        }
    }

    /**
     * 创建模板配置文件，方便用户手动添加UUID映射。
     */
    private static void createTemplateConfig(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            String template = "{\n"
                    + "  \"_comment\": \"Add player name -> online UUID mappings here. UUID must be version 4 (from Mojang).\",\n"
                    + "  \"_example\": \"ddcbc31d-d299-4a67-a278-915363363004\"\n"
                    + "}";
            Files.writeString(configFile, template, StandardCharsets.UTF_8);
            LOGGER.info("[AuthOfflineBridge] Created template UUID config at: {}", configFile);
        } catch (IOException e) {
            LOGGER.warn("[AuthOfflineBridge] Failed to create template config: {}", configFile, e);
        }
    }

    /** 获取缓存的映射表 */
    public static Map<String, UUID> getOnlineUUIDMap() {
        return cachedOnlineMap;
    }

    /**
     * 根据玩家名计算离线模式下的UUID（与Minecraft服务端算法一致）。
     */
    public static UUID getOfflineUUID(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 打印映射表到控制台，用于调试。
     */
    public static void printMappings() {
        LOGGER.info("[AuthOfflineBridge] ===== Player UUID Mapping Table =====");
        for (Map.Entry<String, UUID> entry : cachedOnlineMap.entrySet()) {
            String name = entry.getKey();
            UUID onlineUUID = entry.getValue();
            UUID offlineUUID = getOfflineUUID(name);
            LOGGER.info("[AuthOfflineBridge] Name: {} | Online UUID: {} | Offline UUID: {}", name, onlineUUID, offlineUUID);
        }
        LOGGER.info("[AuthOfflineBridge] ===== End of Table ({} entries) =====", cachedOnlineMap.size());
    }
}
