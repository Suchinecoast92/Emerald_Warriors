package emeraldwarriors;

import emeraldwarriors.client.gui.MercenaryScreen;
import emeraldwarriors.client.render.EmeraldMercenaryRenderer;
import emeraldwarriors.entity.ModEntities;
import emeraldwarriors.inventory.MercenaryMenu;
import emeraldwarriors.menu.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class Emerald_WarriorsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.EMERALD_MERCENARY,
				context -> new EmeraldMercenaryRenderer(context));

		// Asociar MenuType con su Screen; Fabric + Minecraft abren la pantalla
		// automáticamente cuando el servidor llama a ServerPlayer.openMenu().
		MenuScreens.register(ModMenus.MERCENARY_MENU,
				(MenuScreens.ScreenConstructor<MercenaryMenu, MercenaryScreen>) MercenaryScreen::new);
	}
}