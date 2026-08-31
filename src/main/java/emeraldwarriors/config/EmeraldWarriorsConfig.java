package emeraldwarriors.config;

import java.util.ArrayList;
import java.util.List;

public final class EmeraldWarriorsConfig {

    public int configVersion = 16;

    public Toggles toggles = new Toggles();
    public Camp camp = new Camp();
    public SolitarySpawn solitarySpawn = new SolitarySpawn();
    public VillageSpawn villageSpawn = new VillageSpawn();

    public static final class Toggles {
        public boolean camps = true;
        public boolean solitarySpawns = true;
        public boolean villageSpawns = true;
    }

    public static final class Camp {
        /** ~1/N chunks; lower = more common. Still far above old playtest (25). */
        public int rarityChance = 132;
    }

    public static final class SolitarySpawn {
        public int weight = 2;
        public int minGroup = 1;
        public int maxGroup = 4;
        public float naturalSpawnChance = 0.88F;

        public List<String> biomeWhitelist = new ArrayList<>();

        public List<String> biomeBlacklist = new ArrayList<>();
    }

    public static final class VillageSpawn {
        public float firstMercSpawnChanceOnVillageEntry = 0.50F;

        public int checkIntervalTicks = 12000;
        public float spawnChancePerCheck = 0.50F;
        public int maxNearbyMercs = 4;

        public float thirdMercSpawnChanceMultiplier = 0.45F;

        public boolean requireActivePlayer = true;
        public int activePlayerWindowTicks = 12000;
        public int activePlayerMinMoveBlocks = 2;
    }
}
