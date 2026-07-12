package emeraldwarriors.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;

public final class CombatTargets {
    private CombatTargets() {
    }

    public static boolean isEnderman(LivingEntity entity) {
        return entity instanceof EnderMan;
    }
}
