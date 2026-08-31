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

        if (config.configVersion < 7) {
            if (config.solitarySpawn != null && config.solitarySpawn.maxGroup == 1) {
                config.solitarySpawn.maxGroup = 4;
            }
            config.configVersion = 7;
        }

        if (config.configVersion < 8) {
            if (config.camp != null && config.camp.rarityChance == 450) {
                config.camp.rarityChance = 530;
            }
            if (config.solitarySpawn != null
                    && (config.solitarySpawn.naturalSpawnChance <= 0.0F
                    || config.solitarySpawn.naturalSpawnChance > 1.0F)) {
                config.solitarySpawn.naturalSpawnChance = 0.90F;
            }
            config.configVersion = 8;
        }

        if (config.configVersion < 9) {
            // Village-like camp density (~34-chunk spacing equivalent) and fewer wanderers.
            // Force defaults: older playtest configs used very high density (e.g. chance 25, weight 10).
            if (config.camp != null) {
                config.camp.rarityChance = 1200;
            }
            if (config.solitarySpawn != null) {
                config.solitarySpawn.weight = 1;
                config.solitarySpawn.naturalSpawnChance = 0.27F;
            }
            config.configVersion = 9;
        }

        if (config.configVersion < 10) {
            // Slightly denser than v9 (too sparse in playtest), still far below old playtest extremes.
            if (config.camp != null) {
                config.camp.rarityChance = 500;
            }
            if (config.solitarySpawn != null) {
                config.solitarySpawn.weight = 1;
                config.solitarySpawn.naturalSpawnChance = 0.50F;
            }
            config.configVersion = 10;
        }

        if (config.configVersion < 11) {
            if (config.camp != null) {
                config.camp.rarityChance = 280;
            }
            if (config.solitarySpawn != null) {
                config.solitarySpawn.weight = 2;
                config.solitarySpawn.naturalSpawnChance = 0.70F;
            }
            config.configVersion = 11;
        }

        if (config.configVersion < 12) {
            // +15% density vs v11 (lower rarityChance, higher spawn chance).
            if (config.camp != null) {
                config.camp.rarityChance = 243;
            }
            if (config.solitarySpawn != null) {
                config.solitarySpawn.weight = 2;
                config.solitarySpawn.naturalSpawnChance = 0.80F;
            }
            config.configVersion = 12;
        }

        if (config.configVersion < 13) {
            // +10% density vs v12.
            if (config.camp != null) {
                config.camp.rarityChance = 219;
            }
            if (config.solitarySpawn != null) {
                config.solitarySpawn.weight = 2;
                config.solitarySpawn.naturalSpawnChance = 0.88F;
            }
            config.configVersion = 13;
        }

        if (config.configVersion < 14) {
            // Campaments 25% more common than v13 (219 -> 164).
            if (config.camp != null) {
                config.camp.rarityChance = 164;
            }
            config.configVersion = 14;
        }

        if (config.configVersion < 15) {
            if (config.camp != null) {
                config.camp.rarityChance = 116;
            }
            config.configVersion = 15;
        }

        if (config.configVersion < 16) {
            if (config.camp != null) {
                config.camp.rarityChance = 132;
            }
            config.configVersion = 16;
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
