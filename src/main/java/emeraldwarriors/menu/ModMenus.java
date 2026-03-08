package emeraldwarriors.menu;

import emeraldwarriors.Emerald_Warriors;
import emeraldwarriors.inventory.MercenaryInventory;
import emeraldwarriors.inventory.MercenaryMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public class ModMenus {

    public static final MenuType<MercenaryMenu> MERCENARY_MENU = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Emerald_Warriors.MOD_ID, "mercenary_menu"),
            new MenuType<>(
                    (syncId, playerInventory) -> new MercenaryMenu(syncId, playerInventory, new MercenaryInventory()),
                    FeatureFlags.DEFAULT_FLAGS
            )
    );

    public static void register() {
        Emerald_Warriors.LOGGER.info("Emerald Warriors: menus registered.");
    }
}
