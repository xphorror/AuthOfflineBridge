package com.authofflinebridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class UserCacheLoader {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final Duration PROFILE_LOOKUP_TIMEOUT = Duration.ofSeconds(5);
    private static final String PROFILE_LOOKUP_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static final String LEGACY_PROFILE_LOOKUP_URL = "https://api.mojang.com/users/profiles/minecraft/";

    private static Map<String, UUID> cachedOnlineMap = new LinkedHashMap<>();
    private static Path manualConfigFile;

    private UserCacheLoader() {
    }

    public static void init(Path userCacheFile, Path manualConfigFile) {
        UserCacheLoader.manualConfigFile = manualConfigFile;
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
                    cacheMapping(name, uuid, "manual config");
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
                    cacheMapping(name, uuid, "usercache.json");
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

    public static synchronized Optional<UUID> resolveOnlineUUID(String playerName) {
        UUID cachedUUID = cachedOnlineMap.get(playerName);
        if (cachedUUID != null) {
            return Optional.of(cachedUUID);
        }

        Optional<UUID> fetchedUUID = fetchOnlineUUID(playerName);
        fetchedUUID.ifPresent(uuid -> {
            cacheMapping(playerName, uuid, "Mojang API");
            persistFetchedMapping(playerName, uuid);
        });
        return fetchedUUID;
    }

    public static UUID getOfflineUUID(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    private static void cacheMapping(String playerName, UUID uuid, String source) {
        findExistingName(uuid, playerName).ifPresent(existingName ->
                Authofflinebridge.LOGGER.warn(
                        "Names '{}' and '{}' both map to online UUID {} from {}. This may be a Mojang name change; player data is keyed by UUID.",
                        existingName,
                        playerName,
                        uuid,
                        source
                )
        );
        cachedOnlineMap.put(playerName, uuid);
    }

    private static Optional<String> findExistingName(UUID uuid, String currentName) {
        for (Map.Entry<String, UUID> entry : cachedOnlineMap.entrySet()) {
            if (!entry.getKey().equals(currentName) && entry.getValue().equals(uuid)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static Optional<UUID> fetchOnlineUUID(String playerName) {
        Optional<UUID> servicesResult = fetchOnlineUUID(playerName, PROFILE_LOOKUP_URL);
        if (servicesResult.isPresent()) {
            return servicesResult;
        }

        return fetchOnlineUUID(playerName, LEGACY_PROFILE_LOOKUP_URL);
    }

    private static Optional<UUID> fetchOnlineUUID(String playerName, String endpoint) {
        String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + encodedName))
                .timeout(PROFILE_LOOKUP_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 204 || response.statusCode() == 404) {
                Authofflinebridge.LOGGER.info("No Mojang profile found for {}", playerName);
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Authofflinebridge.LOGGER.warn("Mojang profile lookup for {} returned HTTP {}", playerName, response.statusCode());
                return Optional.empty();
            }

            JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!obj.has("id")) {
                Authofflinebridge.LOGGER.warn("Mojang profile lookup for {} returned no UUID", playerName);
                return Optional.empty();
            }

            UUID uuid = parseMojangUUID(obj.get("id").getAsString());
            Authofflinebridge.LOGGER.info("Fetched online UUID for {} from Mojang API: {}", playerName, uuid);
            return Optional.of(uuid);
        } catch (Exception e) {
            Authofflinebridge.LOGGER.warn("Failed to fetch online UUID for {} from {}", playerName, endpoint, e);
            return Optional.empty();
        }
    }

    private static UUID parseMojangUUID(String value) {
        if (value.length() == 36) {
            return UUID.fromString(value);
        }
        if (value.length() != 32) {
            throw new IllegalArgumentException("Expected 32 or 36 character UUID, got: " + value);
        }

        String hyphenated = value.substring(0, 8)
                + "-" + value.substring(8, 12)
                + "-" + value.substring(12, 16)
                + "-" + value.substring(16, 20)
                + "-" + value.substring(20);
        return UUID.fromString(hyphenated);
    }

    private static void persistFetchedMapping(String playerName, UUID uuid) {
        if (manualConfigFile == null) {
            return;
        }

        try {
            JsonObject obj;
            if (Files.exists(manualConfigFile)) {
                String content = Files.readString(manualConfigFile, StandardCharsets.UTF_8);
                obj = JsonParser.parseString(content).getAsJsonObject();
            } else {
                Files.createDirectories(manualConfigFile.getParent());
                obj = new JsonObject();
                obj.addProperty("_comment", "Add player name -> online UUID mappings here. UUID must be the player's Mojang online UUID.");
            }

            obj.addProperty(playerName, uuid.toString());
            String output = new GsonBuilder().setPrettyPrinting().create().toJson(obj) + System.lineSeparator();
            Files.writeString(manualConfigFile, output, StandardCharsets.UTF_8);
            Authofflinebridge.LOGGER.info("Cached fetched online UUID for {} in {}", playerName, manualConfigFile);
        } catch (Exception e) {
            Authofflinebridge.LOGGER.warn("Failed to cache fetched online UUID for {} in {}", playerName, manualConfigFile, e);
        }
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

            if (Files.exists(target) && Files.mismatch(source, target) == -1L) {
                Files.writeString(marker, "Already matched " + source.getFileName() + System.lineSeparator(), StandardCharsets.UTF_8);
                return;
            }

            if (Files.exists(target)) {
                Authofflinebridge.LOGGER.warn(
                        "Skipped offline {} migration for {} because target already exists: {}. Remove or back up the target file manually if you really want to replace it.",
                        label,
                        playerName,
                        target
                );
                return;
            }

            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
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
