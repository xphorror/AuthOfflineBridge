package com.authofflinebridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class UserCacheLoader {

    private static Map<String, UUID> cachedOnlineMap = new LinkedHashMap<>();

    private UserCacheLoader() {
    }

    public static void init(Path userCacheFile, Path manualConfigFile) {
        cachedOnlineMap = new LinkedHashMap<>();

        loadManualConfig(manualConfigFile);
        loadUserCache(userCacheFile);

        if (!Files.exists(manualConfigFile)) {
            createTemplateConfig(manualConfigFile);
        }
    }

    private static void loadManualConfig(Path configFile) {
        if (!Files.exists(configFile)) {
            Authofflinebridge.LOGGER.info("Manual UUID config not found at: {}, will create template", configFile);
            return;
        }

        try {
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();

            int count = 0;
            for (var entry : obj.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("_")) {
                    continue;
                }

                String uuidStr = entry.getValue().getAsString();
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    cachedOnlineMap.put(name, uuid);
                    count++;
                } catch (IllegalArgumentException e) {
                    Authofflinebridge.LOGGER.warn("Invalid UUID '{}' for player '{}' in manual config", uuidStr, name);
                }
            }

            Authofflinebridge.LOGGER.info("Loaded {} manual UUID mappings from {}", count, configFile);
        } catch (Exception e) {
            Authofflinebridge.LOGGER.error("Failed to read manual UUID config: {}", configFile, e);
        }
    }

    private static void loadUserCache(Path userCacheFile) {
        if (!Files.exists(userCacheFile)) {
            Authofflinebridge.LOGGER.warn("usercache.json not found at: {}", userCacheFile);
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

                if (uuid.version() == 3) {
                    continue;
                }

                if (!cachedOnlineMap.containsKey(name)) {
                    cachedOnlineMap.put(name, uuid);
                    count++;
                }
            }

            Authofflinebridge.LOGGER.info("Loaded {} online UUID mappings from usercache.json", count);
        } catch (Exception e) {
            Authofflinebridge.LOGGER.error("Failed to read usercache.json", e);
        }
    }

    private static void createTemplateConfig(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            String template = "{\n"
                    + "  \"_comment\": \"Add player name -> online UUID mappings here. UUID must be the player's Mojang online UUID.\",\n"
                    + "  \"_example\": \"ddcbc31d-d299-4a67-a278-915363363004\"\n"
                    + "}\n";
            Files.writeString(configFile, template, StandardCharsets.UTF_8);
            Authofflinebridge.LOGGER.info("Created template UUID config at: {}", configFile);
        } catch (IOException e) {
            Authofflinebridge.LOGGER.warn("Failed to create template config: {}", configFile, e);
        }
    }

    public static Map<String, UUID> getOnlineUUIDMap() {
        return cachedOnlineMap;
    }

    public static UUID getOfflineUUID(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    public static void migrateOfflinePlayerFiles(MinecraftServer server, String playerName, UUID onlineUUID) {
        UUID offlineUUID = getOfflineUUID(playerName);

        copyOnce(
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR),
                offlineUUID + ".dat",
                onlineUUID + ".dat",
                playerName,
                "player data"
        );
        copyOnce(
                server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR),
                offlineUUID + ".json",
                onlineUUID + ".json",
                playerName,
                "advancements"
        );
        copyOnce(
                server.getWorldPath(LevelResource.PLAYER_STATS_DIR),
                offlineUUID + ".json",
                onlineUUID + ".json",
                playerName,
                "stats"
        );
    }

    private static void copyOnce(Path directory, String offlineFileName, String onlineFileName, String playerName, String label) {
        Path source = directory.resolve(offlineFileName);
        Path target = directory.resolve(onlineFileName);
        Path marker = directory.resolve(onlineFileName + ".authofflinebridge.migrated");

        if (!Files.exists(source)) {
            return;
        }

        if (Files.exists(marker) && Files.exists(target)) {
            return;
        }

        try {
            Files.createDirectories(directory);

            if (Files.exists(target)) {
                if (Files.mismatch(source, target) == -1L) {
                    Files.writeString(marker, "Already matched " + source.getFileName() + System.lineSeparator(), StandardCharsets.UTF_8);
                    return;
                }

                Path backup = directory.resolve(onlineFileName + ".authofflinebridge.bak");
                if (Files.exists(backup)) {
                    backup = directory.resolve(onlineFileName + ".authofflinebridge." + System.currentTimeMillis() + ".bak");
                }
                Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES);
                Authofflinebridge.LOGGER.info("Backed up existing {} for {} to {}", label, playerName, backup);
            }

            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            Files.writeString(marker, "Copied from " + source.getFileName() + System.lineSeparator(), StandardCharsets.UTF_8);
            Authofflinebridge.LOGGER.info("Copied offline {} for {} from {} to {}", label, playerName, source, target);
        } catch (IOException e) {
            Authofflinebridge.LOGGER.error("Failed to copy offline {} for {} from {} to {}", label, playerName, source, target, e);
        }
    }

    public static void printMappings() {
        Authofflinebridge.LOGGER.info("===== Player UUID Mapping Table =====");
        for (Map.Entry<String, UUID> entry : cachedOnlineMap.entrySet()) {
            String name = entry.getKey();
            UUID onlineUUID = entry.getValue();
            UUID offlineUUID = getOfflineUUID(name);
            Authofflinebridge.LOGGER.info("Name: {} | Online UUID: {} | Offline UUID: {}", name, onlineUUID, offlineUUID);
        }
        Authofflinebridge.LOGGER.info("===== End of Table ({} entries) =====", cachedOnlineMap.size());
    }
}
