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

    /**
     * Matches the mercenary GUI hearts ({@code Math.ceil} on health and max health).
     * A mob can show full hearts while {@code getHealth() < getMaxHealth()} by a fraction.
     */
    public static boolean isAtFullHealth(LivingEntity entity) {
        return Math.ceil(entity.getHealth()) >= Math.ceil(entity.getMaxHealth());
    }

    public static float getMissingHealth(LivingEntity entity) {
        return Math.max(0.0F, entity.getMaxHealth() - entity.getHealth());
    }

    public static void snapHealthToMax(LivingEntity entity) {
        if (isAtFullHealth(entity)) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    public static boolean applyFoodHealing(LivingEntity entity, ItemStack stack) {
        if (isAtFullHealth(entity)) {
            return false;
        }
        float amount = getFoodHealingAmount(stack);
        if (amount <= 0.0F) {
            return false;
        }
        entity.heal(Math.min(amount, entity.getMaxHealth() - entity.getHealth()));
        snapHealthToMax(entity);
        return true;
    }
}
