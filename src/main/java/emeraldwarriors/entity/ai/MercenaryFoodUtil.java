package emeraldwarriors.entity.ai;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Food checks for mercenary healing goals. Uses vanilla {@code DataComponents.FOOD}
 * so any mod item registered as edible works without a hard dependency. Unsafe items
 * are excluded explicitly and via the common {@code c:foods/food_poisoning} tag
 * when present in loaded datapacks.
 */
public final class MercenaryFoodUtil {

    private static final TagKey<Item> FOOD_POISONING = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("c", "foods/food_poisoning")
    );

    private MercenaryFoodUtil() {
    }

    public static boolean isSafeFood(ItemStack stack) {
        return getFoodProperties(stack) != null;
    }

    public static FoodProperties getFoodProperties(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.POISONOUS_POTATO)) {
            return null;
        }
        if (stack.is(FOOD_POISONING)) {
            return null;
        }
        return stack.get(DataComponents.FOOD);
    }

    public static float getFoodHealingAmount(ItemStack stack) {
        FoodProperties food = getFoodProperties(stack);
        if (food == null) {
            return 0.0F;
        }
        float amount = food.nutrition() + (food.saturation() * 0.5F);
        return Math.max(1.0F, Math.min(10.0F, amount));
    }

    public static boolean applyFoodHealing(LivingEntity entity, ItemStack stack) {
        float amount = getFoodHealingAmount(stack);
        if (amount <= 0.0F || entity.getHealth() >= entity.getMaxHealth()) {
            return false;
        }
        entity.heal(Math.min(amount, entity.getMaxHealth() - entity.getHealth()));
        return true;
    }
}
