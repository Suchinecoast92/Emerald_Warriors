package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.inventory.MercenaryInventory;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Cuando la vida del mercenario baja del umbral de su rango:
 *  1. Detiene el combate y busca items de curación en su inventario.
 *  2. Los consume (con animación) hasta alcanzar ~55-60% de HP según rango.
 *  3. Prioridad: encantada > dorada > pociones > comida (atributos vanilla).
 *  4. Solo si no hay items de curación disponibles, huye hacia su punto de anclaje.
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
                    } else if (!this.consumedItem.isEmpty()
                            && ItemStack.isSameItemSameComponents(currentMain, this.consumedItem)) {
                        didConsume = true;
                    } else if (UseHealingItemGoal.isHealingItem(currentMain) || MercenaryFoodUtil.isSafeFood(currentMain)) {
                        toReturn = currentMain.copy();
                    }
                } else {
                    didConsume = true;
                }

                restoreWeapon();

                if (!toReturn.isEmpty()) {
                    returnToBag(toReturn);
                }

                if (didConsume && !this.consumedItem.isEmpty() && MercenaryFoodUtil.isSafeFood(this.consumedItem) && !UseHealingItemGoal.isHealingItem(this.consumedItem)) {
                    MercenaryFoodUtil.applyFoodHealing(this.mercenary, this.consumedItem);
                } else if (didConsume) {
                    MercenaryFoodUtil.snapHealthToMax(this.mercenary);
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
            if (!currentMain.isEmpty() && (UseHealingItemGoal.isHealingItem(currentMain) || MercenaryFoodUtil.isSafeFood(currentMain)
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
        if (MercenaryFoodUtil.isAtFullHealth(this.mercenary)) return;
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
        float missingHealth = MercenaryFoodUtil.getMissingHealth(this.mercenary);
        if (missingHealth <= 0.0F) {
            return -1;
        }
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            int score = getEmergencyHealingScore(stack, missingHealth);
            if (score > bestScore) {
                bestSlot = i;
                bestScore = score;
            }
        }
        return bestSlot;
    }

    private static int getEmergencyHealingScore(ItemStack stack, float missingHealth) {
        if (stack.isEmpty() || missingHealth <= 0.0F) {
            return Integer.MIN_VALUE;
        }
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return 4000;
        }
        if (stack.is(Items.GOLDEN_APPLE)) {
            return 3000;
        }
        if (UseHealingItemGoal.isHealingItem(stack)) {
            return 2000;
        }
        if (!MercenaryFoodUtil.isSafeFood(stack)) {
            return Integer.MIN_VALUE;
        }
        float foodHealing = MercenaryFoodUtil.getFoodHealingAmount(stack);
        if (foodHealing <= 0.0F) {
            return Integer.MIN_VALUE;
        }
        int score = 1000 - Math.round(Math.abs(foodHealing - missingHealth) * 25.0F);
        if (foodHealing <= missingHealth + 1.0F) {
            score += 100;
        }
        return score;
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

    private void moveToSafePoint() {
        if (this.threat != null && this.threat.isAlive()) {
            if (moveAwayFromThreat(this.threat)) {
                return;
            }
        }

        MercenaryOrder order = this.mercenary.getCurrentOrder();
        switch (order) {
            case GUARD -> {
                BlockPos guard = this.mercenary.getGuardPos();
                if (guard != null && isRetreatPointSafer(guard, this.threat)) {
                    this.mercenary.getNavigation().moveTo(
                            guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5, this.speedModifier);
                } else if (this.threat != null && this.threat.isAlive()) {
                    moveAwayFromThreat(this.threat);
                }
            }
            case PATROL, NEUTRAL -> {
                BlockPos center = this.mercenary.getPatrolCenter();
                if (center != null && isRetreatPointSafer(center, this.threat)) {
                    this.mercenary.getNavigation().moveTo(
                            center.getX() + 0.5, center.getY(), center.getZ() + 0.5, this.speedModifier);
                } else if (this.threat != null && this.threat.isAlive()) {
                    moveAwayFromThreat(this.threat);
                }
            }
            default -> {
                LivingEntity owner = this.mercenary.getOwner();
                if (owner != null && isRetreatPointSafer(owner.blockPosition(), this.threat)) {
                    this.mercenary.getNavigation().moveTo(owner, this.speedModifier);
                } else if (this.threat != null && this.threat.isAlive()) {
                    moveAwayFromThreat(this.threat);
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

    private boolean isRetreatPointSafer(BlockPos point, LivingEntity threat) {
        if (threat == null || !threat.isAlive()) {
            return true;
        }
        double currentDistSqr = threat.distanceToSqr(this.mercenary);
        double pointDistSqr = threat.distanceToSqr(point.getX() + 0.5, point.getY(), point.getZ() + 0.5);
        return pointDistSqr > currentDistSqr + 4.0;
    }

    private boolean moveAwayFromThreat(LivingEntity threat) {
        double mx = this.mercenary.getX();
        double mz = this.mercenary.getZ();
        double tx = threat.getX();
        double tz = threat.getZ();

        double dx = mx - tx;
        double dz = mz - tz;
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 1.0E-4) {
            double angle = this.mercenary.getRandom().nextDouble() * Math.PI * 2.0D;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            lenSq = 1.0D;
        }

        double len = Math.sqrt(lenSq);
        dx /= len;
        dz /= len;

        BlockPos anchor = this.retreatAnchor;
        if (anchor != null) {
            double ax = anchor.getX() + 0.5 - mx;
            double az = anchor.getZ() + 0.5 - mz;
            double aLenSq = ax * ax + az * az;
            if (aLenSq > 1.0E-4) {
                double aLen = Math.sqrt(aLenSq);
                ax /= aLen;
                az /= aLen;
                double dot = dx * ax + dz * az;
                if (dot > 0.0D) {
                    dx = dx * 0.7D + ax * 0.3D;
                    dz = dz * 0.7D + az * 0.3D;
                    len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 1.0E-4) {
                        dx /= len;
                        dz /= len;
                    }
                }
            }
        }

        LivingEntity owner = this.mercenary.getOwner();
        double maxOwnerDist = Math.min(this.mercenary.getRank().getMaxChaseFromAnchor() * 1.25, 16.0);
        double maxOwnerDistSqr = maxOwnerDist * maxOwnerDist;

        double dist = 10.0;
        double[] angles = new double[]{0.0, 0.4, -0.4, 0.8, -0.8, 1.2, -1.2};
        double currentDistSqr = threat.distanceToSqr(this.mercenary);

        for (double angle : angles) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double rdx = dx * cos - dz * sin;
            double rdz = dx * sin + dz * cos;

            double x = mx + rdx * dist;
            double z = mz + rdz * dist;
            double y = this.mercenary.getY();

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

            double candidateDistSqr = threat.distanceToSqr(x, y, z);
            if (candidateDistSqr <= currentDistSqr + 1.0) {
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
