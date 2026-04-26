package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.inventory.MercenaryInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.EnumSet;

/**
 * When the mercenary's HP falls below the retreat threshold, it searches its
 * backpack for a healing item (golden apple, enchanted golden apple, golden
 * carrot, healing / regeneration potion) and consumes it with the vanilla
 * eating / drinking animation, sound and particles. After finishing, the
 * original main-hand weapon is restored.
 */
public class UseHealingItemGoal extends Goal {

    private static final ResourceKey<MobEffect> INSTANT_HEALTH_KEY =
            ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace("instant_health"));
    private static final ResourceKey<MobEffect> REGENERATION_KEY =
            ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace("regeneration"));

    private static final int OUT_OF_COMBAT_COOLDOWN = 400; // 20 s between out-of-combat uses

    private final EmeraldMercenaryEntity mercenary;
    private ItemStack savedWeapon = ItemStack.EMPTY;
    private ItemStack consumedItem = ItemStack.EMPTY;
    private int healSlot = -1;
    private int cooldown = 0;
    private boolean consuming = false;

    public UseHealingItemGoal(EmeraldMercenaryEntity mercenary) {
        this.mercenary = mercenary;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (!this.mercenary.isAlive()) return false;
        if (this.mercenary.isUsingItem()) return false;
        if (this.mercenary.getHealth() >= this.mercenary.getMaxHealth()) return false;

        // Only out-of-combat healing; low-HP combat healing is handled by RetreatLowHpGoal
        if (this.mercenary.isOutOfCombatForHeal()) {
            return findHealingSlot(true) != -1;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.consuming && this.mercenary.isUsingItem();
    }

    @Override
    public void start() {
        this.healSlot = findHealingSlot(true);
        if (this.healSlot == -1) return;

        MercenaryInventory inv = this.mercenary.getMercenaryInventory();

        this.savedWeapon = inv.getItem(MercenaryInventory.SLOT_MAIN_HAND).copy();

        ItemStack bagStack = inv.getItem(this.healSlot);
        ItemStack healItem = bagStack.copyWithCount(1);
        bagStack.shrink(1);
        if (bagStack.isEmpty()) {
            inv.setItem(this.healSlot, ItemStack.EMPTY);
        }

        inv.setItem(MercenaryInventory.SLOT_MAIN_HAND, healItem);

        this.consumedItem = healItem.copy();

        this.mercenary.startUsingItem(InteractionHand.MAIN_HAND);
        this.consuming = true;
    }

    @Override
    public void stop() {
        ItemStack toReturn = ItemStack.EMPTY;
        boolean didConsume = false;
        if (this.consuming) {
            ItemStack currentMain = this.mercenary.getMercenaryInventory().getItem(MercenaryInventory.SLOT_MAIN_HAND);
            if (!currentMain.isEmpty()) {
                if (isOutOfCombatHealingItem(currentMain)
                        || currentMain.is(Items.GLASS_BOTTLE)
                        || currentMain.is(Items.BOWL)) {
                    toReturn = currentMain.copy();
                }
            } else {
                didConsume = true;
            }

            if (currentMain.is(Items.GLASS_BOTTLE) || currentMain.is(Items.BOWL)) {
                didConsume = true;
            }
        }

        if (this.mercenary.isUsingItem()) {
            this.mercenary.stopUsingItem();
        }
        this.mercenary.getMercenaryInventory()
                .setItem(MercenaryInventory.SLOT_MAIN_HAND, this.savedWeapon);

        if (!toReturn.isEmpty()) {
            returnToBag(toReturn);
        }

        if (didConsume && !this.consumedItem.isEmpty() && isSafeFood(this.consumedItem) && !isHealingItem(this.consumedItem)) {
            this.mercenary.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, false, false));
        }

        this.savedWeapon = ItemStack.EMPTY;
        this.consumedItem = ItemStack.EMPTY;
        this.healSlot = -1;
        this.consuming = false;
        this.cooldown = OUT_OF_COMBAT_COOLDOWN;
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

    private int findHealingSlot(boolean excludeEnchantedApple) {
        MercenaryInventory inv = this.mercenary.getMercenaryInventory();
        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && isOutOfCombatHealingItem(stack)) {
                if (excludeEnchantedApple && stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    private static boolean isSafeFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item == Items.ROTTEN_FLESH || item == Items.POISONOUS_POTATO) {
            return false;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null;
    }

    private static boolean isOutOfCombatHealingItem(ItemStack stack) {
        return isHealingItem(stack) || isSafeFood(stack);
    }

    private static boolean isHealingEffect(MobEffectInstance eff) {
        return eff.getEffect().is(INSTANT_HEALTH_KEY) || eff.getEffect().is(REGENERATION_KEY);
    }

    public static boolean isHealingItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.GOLDEN_CARROT) {
            return true;
        }
        if (item == Items.POTION) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null) {
                for (MobEffectInstance eff : contents.getAllEffects()) {
                    if (isHealingEffect(eff)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
