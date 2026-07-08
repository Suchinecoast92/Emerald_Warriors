package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Carga con lanza para mercenarios montados. Puerto fiel de {@code SpearUseGoal}
 * (zombis/jinetes con lanza en 1.21.11), adaptado a {@link EmeraldMercenaryEntity}
 * (PathfinderMob, no Monster) y a la navegación de la montura vía
 * {@link EmeraldMercenaryEntity#getEffectiveNavigation()}.
 *
 * <p>Daño de carga: lo aplica el componente vanilla {@link DataComponents#KINETIC_WEAPON}
 * al mantener la lanza en guardia ({@code startUsingItem}) con velocidad relativa.
 * Este goal solo aproxima, embiste y reposiciona — igual que vanilla.</p>
 *
 * <p>Convergencia carga/jab (dos goals vanilla, no lógica de golpe custom):</p>
 * <ul>
 *   <li><b>Carga</b> — este goal (puerto de {@code SpearUseGoal}), prioridad 2.</li>
 *   <li><b>Jab</b> — {@link EmeraldMeleeAttackGoal} (puerto de {@code MeleeAttackGoal}),
 *       misma prioridad; toma el control cuando este goal cede al estar pegado y sin
 *       velocidad de carrerilla (el zombi vanilla solo reposiciona; aquí cedemos al melee).</li>
 * </ul>
 */
public class MercenaryLanceChargeGoal extends Goal {

    static final int MIN_REPOSITION_DISTANCE = 6;
    static final int MAX_REPOSITION_DISTANCE = 7;
    static final int MIN_COOLDOWN_DISTANCE = 9;
    static final int MAX_COOLDOWN_DISTANCE = 11;
    static final double MAX_FLEEING_TIME = reducedTickDelay(100);

    private final EmeraldMercenaryEntity mob;
    private State state;
    private final double speedModifierWhenCharging;
    private final double speedModifierWhenRepositioning;
    private final float approachDistanceSq;
    private final float targetInRangeRadiusSq;

    public MercenaryLanceChargeGoal(EmeraldMercenaryEntity mob,
                                    double speedModifierWhenCharging,
                                    double speedModifierWhenRepositioning,
                                    float approachDistance,
                                    float targetInRangeRadius) {
        this.mob = mob;
        this.speedModifierWhenCharging = speedModifierWhenCharging;
        this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
        this.approachDistanceSq = approachDistance * approachDistance;
        this.targetInRangeRadiusSq = targetInRangeRadius * targetInRangeRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return this.ableToAttack()
                && !this.mob.isUsingItem()
                && this.shouldPreferCharge(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return this.state != null
                && !this.state.done
                && this.ableToAttack()
                && this.shouldPreferCharge(target);
    }

    private boolean ableToAttack() {
        return this.isMountedOnHorse()
                && this.mob.getTarget() != null
                && this.mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON);
    }

    private boolean isMountedOnHorse() {
        return this.mob.isPassenger() && this.mob.getVehicle() instanceof AbstractHorse;
    }

    /**
     * Cede el control al melee cuando el enemigo está dentro del radio de reposición
     * vanilla ({@code targetInRangeRadius}) y la montura no lleva velocidad de carga.
     * Fuera de ese caso, replica el criterio de {@code SpearUseGoal} (seguir cargando).
     */
    private boolean shouldPreferCharge(LivingEntity target) {
        if (target == null) {
            return false;
        }
        double distSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        if (distSq > (double) this.targetInRangeRadiusSq) {
            return true;
        }
        Entity rootVehicle = this.mob.getRootVehicle();
        Entity ref = rootVehicle != null ? rootVehicle : this.mob;
        Vec3 velocity = ref.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        return horizontalSpeed >= 0.15D;
    }

    private int getKineticWeaponUseDuration() {
        int duration = Optional.ofNullable(this.mob.getMainHandItem().get(DataComponents.KINETIC_WEAPON))
                .map(KineticWeapon::computeDamageUseDuration)
                .orElse(0);
        return reducedTickDelay(duration);
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
        this.state = new State();
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.getEffectiveNavigation().stop();
        this.mob.setAggressive(false);
        this.state = null;
        this.mob.stopUsingItem();
    }

    @Override
    public void tick() {
        if (this.state == null) {
            return;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        double distSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());

        Entity rootVehicle = this.mob.getRootVehicle();
        float chargeSpeedModifier = 1.0F;
        if (rootVehicle instanceof Mob mobVehicle) {
            chargeSpeedModifier = mobVehicle.chargeSpeedModifier();
        }

        int passengerBonus = this.mob.isPassenger() ? 2 : 0;

        this.mob.lookAt(target, 30.0F, 30.0F);
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        PathNavigation navigation = this.mob.getEffectiveNavigation();

        if (this.state.notEngagedYet()) {
            if (distSq > (double) this.approachDistanceSq) {
                navigation.moveTo(target, this.mob.resolveNavigationSpeed((double) chargeSpeedModifier * this.speedModifierWhenRepositioning));
                return;
            }
            this.state.startEngagement(this.getKineticWeaponUseDuration());
            this.mob.startUsingItem(InteractionHand.MAIN_HAND);
        }

        if (this.state.tickAndCheckEngagement()) {
            this.mob.stopUsingItem();
            double dist = Math.sqrt(distSq);
            this.state.awayPos = LandRandomPos.getPosAway(
                    this.mob,
                    Math.max(0.0D, (double) (MIN_COOLDOWN_DISTANCE + passengerBonus) - dist),
                    Math.max(1.0D, (double) (MAX_COOLDOWN_DISTANCE + passengerBonus) - dist),
                    7,
                    target.position());
            this.state.fleeingTime = 1;
        }

        if (this.state.tickAndCheckFleeing()) {
            return;
        }

        if (this.state.awayPos != null) {
            navigation.moveTo(this.state.awayPos.x, this.state.awayPos.y, this.state.awayPos.z,
                    this.mob.resolveNavigationSpeed((double) chargeSpeedModifier * this.speedModifierWhenRepositioning));
            if (navigation.isDone()) {
                if (this.state.fleeingTime > 0) {
                    this.state.done = true;
                    return;
                }
                this.state.awayPos = null;
            }
        } else {
            navigation.moveTo(target, this.mob.resolveNavigationSpeed((double) chargeSpeedModifier * this.speedModifierWhenCharging));
            if (distSq < (double) this.targetInRangeRadiusSq || navigation.isDone()) {
                double dist = Math.sqrt(distSq);
                this.state.awayPos = LandRandomPos.getPosAway(
                        this.mob,
                        (double) (MIN_REPOSITION_DISTANCE + passengerBonus) - dist,
                        (double) (MAX_REPOSITION_DISTANCE + passengerBonus) - dist,
                        7,
                        target.position());
            }
        }
    }

    private static final class State {
        private int engageTime = -1;
        int fleeingTime = -1;
        Vec3 awayPos;
        boolean done;

        boolean notEngagedYet() {
            return this.engageTime < 0;
        }

        void startEngagement(int duration) {
            this.engageTime = duration;
        }

        boolean tickAndCheckEngagement() {
            if (this.engageTime > 0) {
                this.engageTime--;
                return this.engageTime == 0;
            }
            return false;
        }

        boolean tickAndCheckFleeing() {
            if (this.fleeingTime > 0) {
                this.fleeingTime++;
                if ((double) this.fleeingTime > MAX_FLEEING_TIME) {
                    this.done = true;
                    return true;
                }
            }
            return false;
        }
    }
}
