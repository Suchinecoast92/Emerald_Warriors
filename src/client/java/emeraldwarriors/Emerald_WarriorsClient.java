package emeraldwarriors;

import emeraldwarriors.client.render.EmeraldMercenaryRenderer;
import emeraldwarriors.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class Emerald_WarriorsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		EntityRenderers.register(ModEntities.EMERALD_MERCENARY,
				context -> new EmeraldMercenaryRenderer(context));
	}
}