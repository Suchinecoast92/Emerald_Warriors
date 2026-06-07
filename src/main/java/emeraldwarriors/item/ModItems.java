package emeraldwarriors.item;

import emeraldwarriors.Emerald_Warriors;
import emeraldwarriors.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Function;

public class ModItems {

    public static final SpawnEggItem EMERALD_MERCENARY_SPAWN_EGG = register(
            "emerald_mercenary_spawn_egg",
            SpawnEggItem::new,
            new Item.Properties().spawnEgg(ModEntities.EMERALD_MERCENARY)
    );

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
        ResourceKey<Item> itemKey = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(Emerald_Warriors.MOD_ID, name)
        );
        T item = factory.apply(properties.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(itemGroup ->
                itemGroup.accept(EMERALD_MERCENARY_SPAWN_EGG));
        Emerald_Warriors.LOGGER.info("Emerald Warriors: items registered.");
    }
}
