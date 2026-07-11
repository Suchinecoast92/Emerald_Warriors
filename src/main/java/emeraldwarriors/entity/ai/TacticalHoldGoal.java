package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Move to a spyglass-marked position and hold until the tactical command is cleared.
 *
 * <p>El punto marcado es un destino <em>aproximado</em>: cada mercenario apunta a un
 * pequeño desplazamiento personal alrededor del punto (ver
 * {@link EmeraldMercenaryEntity#getTacticalHoldTarget()}), de modo que un grupo enviado
 * al mismo sitio se reparte de forma natural en un radio corto en vez de amontonarse en
 * el mismo bloque. Se apoya por completo en el pathfinding vanilla.</p>
 */
public class TacticalHoldGoal extends Goal {

    private static final double ARRIVE_DISTANCE_SQR = 2.25D;

    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;

    public TacticalHoldGoal(EmeraldMercenaryEntity mercenary, double speedModifier) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.mercenary.isTacticalHoldActive()) {
            return false;
        }
        if (!this.mercenary.canObeyTacticalCommands()) {
            return false;
        }
        Vec3 target = this.mercenary.getTacticalHoldTarget();
        if (target == null) {
            return false;
        }
        return this.distanceToTargetSqr(target) > ARRIVE_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mercenary.isTacticalHoldActive() || !this.mercenary.canObeyTacticalCommands()) {
            return false;
        }
        Vec3 target = this.mercenary.getTacticalHoldTarget();
        if (target == null) {
            return false;
        }
        return this.distanceToTargetSqr(target) > ARRIVE_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.moveToTarget();
    }

    @Override
    public void tick() {
        if (this.mercenary.getEffectiveNavigation().isDone()) {
            this.moveToTarget();
        }
    }

    @Override
    public void stop() {
        this.mercenary.getEffectiveNavigation().stop();
    }

    private void moveToTarget() {
        Vec3 target = this.mercenary.getTacticalHoldTarget();
        if (target != null) {
            this.mercenary.getEffectiveNavigation().moveTo(
                    target.x, target.y, target.z, this.resolveHoldSpeed());
        }
    }

    /** Misma velocidad que follow: goal 1.0 + boosts montados (equino/camello). */
    private double resolveHoldSpeed() {
        return this.mercenary.resolveNavigationSpeed(this.speedModifier);
    }

    private double distanceToTargetSqr(Vec3 target) {
        return this.mercenary.distanceToSqr(target.x, target.y, target.z);
    }
}
