package emeraldwarriors;

import emeraldwarriors.command.MercenaryCommand;
import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.entity.ModEntities;
import emeraldwarriors.horn.MercenaryHornListener;
import emeraldwarriors.menu.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Emerald_Warriors implements ModInitializer {
	public static final String MOD_ID = "emerald_warriors";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ModEntities.registerAttributes();
		ModMenus.register();
		MercenaryHornListener.register();

		// Prevent Iron Golems from hurting mercenaries, and prevent mercenary
		// projectiles/attacks from harming villagers or golems (so golems don't retaliate)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			var attacker = source.getEntity();
			// Block IronGolem → mercenary damage (prevents accidental-hit retaliation)
			if (entity instanceof EmeraldMercenaryEntity && attacker instanceof IronGolem) {
				return false;
			}
			// Block mercenary → villager/golem damage (stray arrows, AOE)
			if (attacker instanceof EmeraldMercenaryEntity) {
				if (entity instanceof AbstractVillager || entity instanceof IronGolem) {
					return false;
				}
			}
			// Also catch indirect damage (arrows): directEntity is the arrow, entity is who fired it
			var directEntity = source.getDirectEntity();
			if (directEntity != null && directEntity != attacker) {
				var ownerEntity = source.getEntity();
				if (ownerEntity instanceof EmeraldMercenaryEntity) {
					if (entity instanceof AbstractVillager || entity instanceof IronGolem) {
						return false;
					}
				}
			}
			return true;
		});

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			MercenaryCommand.register(dispatcher);
		});
		
		LOGGER.info("Emerald Warriors: mercenary system initialized (entities registered).");
	}
}