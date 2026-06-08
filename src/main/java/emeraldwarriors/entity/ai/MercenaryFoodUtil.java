package emeraldwarriors.entity.ai;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
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
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.POISONOUS_POTATO)) {
            return false;
        }
        if (stack.is(FOOD_POISONING)) {
            return false;
        }
        return stack.get(DataComponents.FOOD) != null;
    }
}
