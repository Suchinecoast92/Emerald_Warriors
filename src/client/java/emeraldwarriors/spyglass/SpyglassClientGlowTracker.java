package emeraldwarriors.spyglass;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks entities that should render with a glowing outline for the local player only.
 */
public final class SpyglassClientGlowTracker {

    private static final Map<UUID, Long> GLOW_UNTIL_GAME_TIME = new HashMap<>();

    private SpyglassClientGlowTracker() {
    }

    public static void mark(UUID entityUuid, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long until = mc.level.getGameTime() + Math.max(1, durationTicks);
        GLOW_UNTIL_GAME_TIME.put(entityUuid, until);
    }

    public static boolean shouldGlow(UUID entityUuid) {
        Long until = GLOW_UNTIL_GAME_TIME.get(entityUuid);
        if (until == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        if (mc.level.getGameTime() >= until) {
            GLOW_UNTIL_GAME_TIME.remove(entityUuid);
            return false;
        }
        return true;
    }

    public static void tickCleanup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            GLOW_UNTIL_GAME_TIME.clear();
            return;
        }
        long now = mc.level.getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = GLOW_UNTIL_GAME_TIME.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= now) {
                it.remove();
            }
        }
    }
}
