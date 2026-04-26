package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.inventory.MercenaryInventory;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * Cuando la vida del mercenario baja del umbral de su rango:
 *  1. Detiene el combate y busca items de curación en su inventario.
 *  2. Los consume (con animación) hasta alcanzar el 75% de HP.
 *  3. Solo si no hay items de curación disponibles, huye hacia su punto de anclaje.
 */
public class RetreatLowHpGoal extends Goal {

    private static final float HEAL_TARGET_FRACTION = 0.75f;
    private static final int HEAL_COOLDOWN_TICKS = 40; // 2s entre usos

    private final EmeraldMercenaryEntity mercenary;
    private final double speedModifier;

    private boolean isHealing = false;
    private ItemStack savedWeapon = ItemStack.EMPTY;
    private int healSlot = -1;
    private int healCooldown = 0;

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
        return fraction < HEAL_TARGET_FRACTION;
    }

    @Override
    public void start() {
        this.mercenary.setTarget(null);
        this.mercenary.getNavigation().stop();
        this.isHealing = false;
        this.healCooldown = 0;
        tryStartHealing();
    }

    @Override
    public void tick() {
        if (this.healCooldown > 0) this.healCooldown--;

        if (this.isHealing) {
            if (!this.mercenary.isUsingItem()) {
                // Item consumed — restore weapon
                restoreWeapon();
                this.isHealing = false;
                this.healCooldown = HEAL_COOLDOWN_TICKS;
                // Try another item if still below target
                float fraction = this.mercenary.getHealth() / this.mercenary.getMaxHealth();
                if (fraction < HEAL_TARGET_FRACTION) {
                    tryStartHealing();
                }
            }
            // Still consuming: do nothing, wait for animation
        } else {
            // Retreating toward anchor
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
            restoreWeapon();
            this.isHealing = false;
        }
        this.mercenary.getNavigation().stop();
    }

    private void tryStartHealing() {
        if (this.mercenary.isUsingItem()) return;
        if (this.healCooldown > 0) {
            moveToSafePoint();
            return;
        }
        int slot = findHealingSlot();
        if (slot == -1) {
            // No items — fall back to retreating
            moveToSafePoint();
            return;
        }

        MercenaryInventory inv = this.mercenary.getMercenaryInventory();
        this.savedWeapon = inv.getItem(MercenaryInventory.SLOT_MAIN_HAND).copy();
        this.healSlot = slot;

        ItemStack bagStack = inv.getItem(slot);
        ItemStack healItem = bagStack.copyWithCount(1);
        bagStack.shrink(1);
        if (bagStack.isEmpty()) {
            inv.setItem(slot, ItemStack.EMPTY);
        }
        inv.setItem(MercenaryInventory.SLOT_MAIN_HAND, healItem);

        this.mercenary.getNavigation().stop();
        this.mercenary.startUsingItem(InteractionHand.MAIN_HAND);
        this.isHealing = true;
    }

    private void restoreWeapon() {
        this.mercenary.getMercenaryInventory()
                .setItem(MercenaryInventory.SLOT_MAIN_HAND, this.savedWeapon);
        this.savedWeapon = ItemStack.EMPTY;
        this.healSlot = -1;
    }

    private int findHealingSlot() {
        MercenaryInventory inv = this.mercenary.getMercenaryInventory();
        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && UseHealingItemGoal.isHealingItem(stack)) {
                return i;
            }
        }
        return -1;
    }

    private void moveToSafePoint() {
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
}
