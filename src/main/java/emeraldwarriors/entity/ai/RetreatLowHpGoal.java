package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.inventory.MercenaryInventory;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Cuando la vida del mercenario baja del umbral de su rango:
 *  1. Detiene el combate y busca items de curación en su inventario.
 *  2. Los consume (con animación) hasta alcanzar el 75% de HP.
 *  3. Solo si no hay items de curación disponibles, huye hacia su punto de anclaje.
 */
public class RetreatLowHpGoal extends Goal {

    private static final int HEAL_COOLDOWN_TICKS = 40; // 2s entre usos

    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;

    private boolean isHealing = false;
    private ItemStack savedWeapon = ItemStack.EMPTY;
    private ItemStack consumedItem = ItemStack.EMPTY;
    private int healSlot = -1;
    private int healCooldown = 0;

    private LivingEntity threat;
    private BlockPos retreatAnchor;

    public RetreatLowHpGoal(EmeraldMercenaryEntity mercenary, double speedModifier) {
        this.mercenary = mercenary;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.mercenary.isAlive()) return false;
        float fraction = this.mercenary.getHealth() / this.mercenary.getMaxHealth();
        double threshold = this.mercenary.getRank().getRetreatHpFraction();
        return fraction < threshold;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mercenary.isAlive()) return false;
        if (this.isHealing && this.mercenary.isUsingItem()) return true;
        float fraction = this.mercenary.getHealth() / this.mercenary.getMaxHealth();
        return fraction < this.getHealExitFraction();
    }

    @Override
    public void start() {
        this.threat = resolveThreat();
        this.retreatAnchor = resolveRetreatAnchor();
        this.mercenary.setTarget(null);
        this.mercenary.getNavigation().stop();
        this.isHealing = false;
        this.healCooldown = 0;
        moveToSafePoint();
        tryStartHealing();
    }

    @Override
    public void tick() {
        if (this.healCooldown > 0) this.healCooldown--;

        LivingEntity resolvedThreat = resolveThreat();
        if (resolvedThreat != null && resolvedThreat.isAlive()) {
            this.threat = resolvedThreat;
        } else if (this.threat != null && !this.threat.isAlive()) {
            this.threat = null;
        }

        if (this.isHealing) {
            if (!this.mercenary.isUsingItem()) {
                MercenaryInventory inv = this.mercenary.getMercenaryInventory();
                ItemStack currentMain = inv.getItem(MercenaryInventory.SLOT_MAIN_HAND);
                ItemStack toReturn = ItemStack.EMPTY;
                boolean didConsume = false;

                if (!currentMain.isEmpty()) {
                    if (currentMain.is(Items.GLASS_BOTTLE) || currentMain.is(Items.BOWL)) {
                        toReturn = currentMain.copy();
                        didConsume = true;
                    } else if (UseHealingItemGoal.isHealingItem(currentMain) || isSafeFood(currentMain)) {
                        toReturn = currentMain.copy();
                    }
                } else {
                    didConsume = true;
                }

                restoreWeapon();

                if (!toReturn.isEmpty()) {
                    returnToBag(toReturn);
                }

                if (didConsume && !this.consumedItem.isEmpty() && isSafeFood(this.consumedItem) && !UseHealingItemGoal.isHealingItem(this.consumedItem)) {
                    this.mercenary.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, false, false));
                }

                this.isHealing = false;
                this.consumedItem = ItemStack.EMPTY;
                this.healSlot = -1;
                this.healCooldown = HEAL_COOLDOWN_TICKS;
                // Try another item if still below target
                float fraction = this.mercenary.getHealth() / this.mercenary.getMaxHealth();
                if (fraction < this.getHealExitFraction()) {
                    tryStartHealing();
                }
            }
            // Still consuming: do nothing, wait for animation
            if (this.mercenary.getNavigation().isDone()) {
                moveToSafePoint();
            }
        } else {
            // Retreating toward anchor
            if (this.healCooldown <= 0) {
                tryStartHealing();
            }
            if (this.mercenary.getNavigation().isDone()) {
                moveToSafePoint();
            }
        }
    }

    @Override
    public void stop() {
        if (this.isHealing) {
            if (this.mercenary.isUsingItem()) {
                this.mercenary.stopUsingItem();
            }
            MercenaryInventory inv = this.mercenary.getMercenaryInventory();
            ItemStack currentMain = inv.getItem(MercenaryInventory.SLOT_MAIN_HAND);
            if (!currentMain.isEmpty() && (UseHealingItemGoal.isHealingItem(currentMain) || isSafeFood(currentMain)
                    || currentMain.is(Items.GLASS_BOTTLE) || currentMain.is(Items.BOWL))) {
                returnToBag(currentMain.copy());
            }
            restoreWeapon();
            this.isHealing = false;
            this.consumedItem = ItemStack.EMPTY;
            this.healSlot = -1;
        }
        this.mercenary.getNavigation().stop();
        this.threat = null;
        this.retreatAnchor = null;
    }

    private void tryStartHealing() {
        if (this.mercenary.isUsingItem()) return;
        if (this.healCooldown > 0) {
            if (this.mercenary.getNavigation().isDone()) {
                moveToSafePoint();
            }
            return;
        }
        int slot = findHealingSlot();
        if (slot == -1) {
            // No items — fall back to retreating
            if (this.mercenary.getNavigation().isDone()) {
                moveToSafePoint();
            }
            return;
        }

        MercenaryInventory inv = this.mercenary.getMercenaryInventory();
        this.savedWeapon = inv.getItem(MercenaryInventory.SLOT_MAIN_HAND).copy();

        ItemStack bagStack = inv.getItem(slot);
        ItemStack healItem = bagStack.copyWithCount(1);
        bagStack.shrink(1);
        if (bagStack.isEmpty()) {
            inv.setItem(slot, ItemStack.EMPTY);
        }
        inv.setItem(MercenaryInventory.SLOT_MAIN_HAND, healItem);

        this.consumedItem = healItem.copy();
        this.healSlot = slot;

        this.mercenary.startUsingItem(InteractionHand.MAIN_HAND);
        this.isHealing = true;
    }

    private void restoreWeapon() {
        this.mercenary.getMercenaryInventory()
                .setItem(MercenaryInventory.SLOT_MAIN_HAND, this.savedWeapon);
        this.savedWeapon = ItemStack.EMPTY;
    }

    private float getHealExitFraction() {
        float trigger = (float) this.mercenary.getRank().getRetreatHpFraction();
        return Math.max(0.55f, trigger + 0.30f);
    }

    private int findHealingSlot() {
        MercenaryInventory inv = this.mercenary.getMercenaryInventory();
        boolean allowFood = this.mercenary.getCurrentOrder() == MercenaryOrder.NEUTRAL;
        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && (UseHealingItemGoal.isHealingItem(stack) || (allowFood && isSafeFood(stack)))) {
                return i;
            }
        }
        return -1;
    }

    private void returnToBag(ItemStack stack) {
        MercenaryInventory inv = this.mercenary.getMercenaryInventory();
        if (this.healSlot != -1 && inv.getItem(this.healSlot).isEmpty()) {
            inv.setItem(this.healSlot, stack);
            return;
        }
        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                return;
            }
        }
    }

    private static boolean isSafeFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.POISONOUS_POTATO)) {
            return false;
        }
        return stack.get(DataComponents.FOOD) != null;
    }

    private void moveToSafePoint() {
        if (this.threat != null && this.threat.isAlive()) {
            if (moveAwayFromThreat(this.threat)) {
                return;
            }
            if (this.retreatAnchor != null) {
                this.mercenary.getNavigation().moveTo(
                        this.retreatAnchor.getX() + 0.5, this.retreatAnchor.getY(), this.retreatAnchor.getZ() + 0.5, this.speedModifier);
                return;
            }
        }
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        switch (order) {
            case GUARD -> {
                BlockPos guard = this.mercenary.getGuardPos();
                if (guard != null) {
                    this.mercenary.getNavigation().moveTo(
                            guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5, this.speedModifier);
                }
            }
            case PATROL, NEUTRAL -> {
                BlockPos center = this.mercenary.getPatrolCenter();
                if (center != null) {
                    this.mercenary.getNavigation().moveTo(
                            center.getX() + 0.5, center.getY(), center.getZ() + 0.5, this.speedModifier);
                }
            }
            default -> {
                // FOLLOW: huir hacia el dueño
                LivingEntity owner = this.mercenary.getOwner();
                if (owner != null) {
                    this.mercenary.getNavigation().moveTo(owner, this.speedModifier);
                }
            }
        }
    }

    private BlockPos resolveRetreatAnchor() {
        MercenaryOrder order = this.mercenary.getCurrentOrder();
        switch (order) {
            case GUARD -> {
                BlockPos guard = this.mercenary.getGuardPos();
                if (guard != null) {
                    return guard;
                }
            }
            case PATROL, NEUTRAL -> {
                BlockPos center = this.mercenary.getPatrolCenter();
                if (center != null) {
                    return center;
                }
            }
            default -> {
            }
        }
        return new BlockPos(Mth.floor(this.mercenary.getX()), Mth.floor(this.mercenary.getY()), Mth.floor(this.mercenary.getZ()));
    }

    private LivingEntity resolveThreat() {
        LivingEntity t = this.mercenary.getTarget();
        if (t != null && t.isAlive()) {
            return t;
        }
        LivingEntity lastHurtBy = this.mercenary.getLastHurtByMob();
        if (lastHurtBy != null && lastHurtBy.isAlive()
                && (this.mercenary.tickCount - this.mercenary.getLastHurtByMobTimestamp()) < 100) {
            return lastHurtBy;
        }
        return null;
    }

    private boolean moveAwayFromThreat(LivingEntity threat) {
        BlockPos anchor = this.retreatAnchor;
        if (anchor == null) {
            anchor = new BlockPos(Mth.floor(this.mercenary.getX()), Mth.floor(this.mercenary.getY()), Mth.floor(this.mercenary.getZ()));
        }

        double ax = anchor.getX() + 0.5;
        double az = anchor.getZ() + 0.5;

        double dx = ax - threat.getX();
        double dz = az - threat.getZ();
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 1.0E-4) {
            dx = this.mercenary.getX() - threat.getX();
            dz = this.mercenary.getZ() - threat.getZ();
            lenSq = dx * dx + dz * dz;
        }
        if (lenSq < 1.0E-4) {
            return false;
        }

        double len = Math.sqrt(lenSq);
        dx /= len;
        dz /= len;

        LivingEntity owner = this.mercenary.getOwner();
        double maxOwnerDist = Math.min(this.mercenary.getRank().getMaxChaseFromAnchor() * 1.25, 16.0);
        double maxOwnerDistSqr = maxOwnerDist * maxOwnerDist;

        double dist = 10.0;
        double[] angles = new double[]{0.0, 0.5235987755982988, -0.5235987755982988, 1.0471975511965976, -1.0471975511965976, 1.5707963267948966, -1.5707963267948966};

        for (double angle : angles) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double rdx = dx * cos - dz * sin;
            double rdz = dx * sin + dz * cos;

            double x = ax + rdx * dist;
            double z = az + rdz * dist;
            double y = anchor.getY();

            if (owner != null) {
                double odx = x - owner.getX();
                double odz = z - owner.getZ();
                double oLenSq = odx * odx + odz * odz;
                if (oLenSq > maxOwnerDistSqr && oLenSq > 1.0E-4) {
                    double oLen = Math.sqrt(oLenSq);
                    odx /= oLen;
                    odz /= oLen;
                    x = owner.getX() + odx * maxOwnerDist;
                    z = owner.getZ() + odz * maxOwnerDist;
                }
            }

            double currentDistSq = threat.distanceToSqr(this.mercenary);
            double candidateDistSq = threat.distanceToSqr(x, y, z);
            if (candidateDistSq <= currentDistSq + 4.0) {
                continue;
            }

            BlockPos target = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));

            BlockPos ground = target;
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos check = target.offset(0, dy, 0);
                if (this.mercenary.level().getBlockState(check.below()).isSolid()
                        && !this.mercenary.level().getBlockState(check).isSolid()) {
                    ground = check;
                    break;
                }
            }

            if (this.mercenary.getNavigation().moveTo(
                    ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, this.speedModifier)) {
                return true;
            }
        }

        return false;
    }
}
