package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

public class MercenaryIdleStrollGoal extends WaterAvoidingRandomStrollGoal {

    private final EmeraldMercenaryEntity mercenary;

    public MercenaryIdleStrollGoal(EmeraldMercenaryEntity mercenary, double speedModifier) {
        super(mercenary, speedModifier);
        this.mercenary = mercenary;
    }

    @Override
    public boolean canUse() {
        if (this.mercenary.getOwnerUuid() != null) {
            return false;
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mercenary.getOwnerUuid() != null) {
            return false;
        }
        return super.canContinueToUse();
    }
}
