package emeraldwarriors.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import emeraldwarriors.Emerald_Warriors;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = Emerald_Warriors.MOD_ID + ".json";

    private static EmeraldWarriorsConfig config = new EmeraldWarriorsConfig();

    private ModConfig() {
    }

    public static EmeraldWarriorsConfig get() {
        return config;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (!Files.exists(path)) {
            save(path, config);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            EmeraldWarriorsConfig loaded = GSON.fromJson(reader, EmeraldWarriorsConfig.class);
            if (loaded != null) {
                config = loaded;
            }
        } catch (Exception e) {
            Emerald_Warriors.LOGGER.error("Failed to load config: " + path, e);
        }

        if (config.configVersion < 2) {
            if (config.villageSpawn != null && config.villageSpawn.checkIntervalTicks == 2400) {
                config.villageSpawn.checkIntervalTicks = 18000;
            }
            config.configVersion = 2;
        }

        if (config.configVersion < 3) {
            if (config.villageSpawn != null && config.villageSpawn.maxNearbyMercs == 2) {
                config.villageSpawn.maxNearbyMercs = 3;
            }
            config.configVersion = 3;
        }

        if (config.configVersion < 4) {
            if (config.villageSpawn != null) {
                float v = config.villageSpawn.thirdMercSpawnChanceMultiplier;
                if (v <= 0.0F || v == 0.35F) {
                    config.villageSpawn.thirdMercSpawnChanceMultiplier = 0.50F;
                }
            }
            config.configVersion = 4;
        }

        if (config.configVersion < 5) {
            if (config.villageSpawn != null) {
                if (config.villageSpawn.checkIntervalTicks == 18000) {
                    config.villageSpawn.checkIntervalTicks = 12000;
                }
                if (config.villageSpawn.spawnChancePerCheck == 0.20F) {
                    config.villageSpawn.spawnChancePerCheck = 0.50F;
                }
                if (config.villageSpawn.thirdMercSpawnChanceMultiplier == 0.50F) {
                    config.villageSpawn.thirdMercSpawnChanceMultiplier = 0.35F;
                }
                if (config.villageSpawn.firstMercSpawnChanceOnVillageEntry <= 0.0F
                        || config.villageSpawn.firstMercSpawnChanceOnVillageEntry > 1.0F) {
                    config.villageSpawn.firstMercSpawnChanceOnVillageEntry = 0.50F;
                }
            }
            config.configVersion = 5;
        }

        if (config.configVersion < 6) {
            if (config.villageSpawn != null) {
                if (config.villageSpawn.maxNearbyMercs == 3) {
                    config.villageSpawn.maxNearbyMercs = 4;
                }
                if (config.villageSpawn.thirdMercSpawnChanceMultiplier == 0.35F) {
                    config.villageSpawn.thirdMercSpawnChanceMultiplier = 0.45F;
                }
            }
            config.configVersion = 6;
        }

        save(path, config);
    }

    private static void save(Path path, EmeraldWarriorsConfig cfg) {
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            Emerald_Warriors.LOGGER.error("Failed to create config directory", e);
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(cfg, writer);
        } catch (Exception e) {
            Emerald_Warriors.LOGGER.error("Failed to save config: " + path, e);
        }
    }
}
