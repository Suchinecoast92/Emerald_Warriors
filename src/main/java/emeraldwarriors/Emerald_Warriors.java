package emeraldwarriors;

import emeraldwarriors.command.MercenaryCommand;
import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.entity.ModEntities;
import emeraldwarriors.horn.MercenaryHornListener;
import emeraldwarriors.menu.ModMenus;
import emeraldwarriors.worldgen.ModWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
		ModWorldgen.register();

		// Prevent Iron Golems from hurting mercenaries, and prevent mercenary
		// projectiles/attacks from harming villagers or golems (so golems don't retaliate)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			var attacker = source.getEntity();
			var direct = source.getDirectEntity();
			Player responsiblePlayer = null;
			if (attacker instanceof Player p) {
				responsiblePlayer = p;
			} else if (direct instanceof Projectile proj && proj.getOwner() instanceof Player p) {
				responsiblePlayer = p;
			}
			if (entity instanceof EmeraldMercenaryEntity merc && responsiblePlayer != null) {
				var owner = merc.getOwnerUuid();
				if (owner != null && owner.equals(responsiblePlayer.getUUID())) {
					boolean usedWeapon = isWeaponForDiscipline(responsiblePlayer.getMainHandItem());
					merc.onOwnerMeleeHit(responsiblePlayer, usedWeapon);
					return true;
				}
			}
			if (entity instanceof AbstractVillager villager && responsiblePlayer != null) {
				boolean usedWeapon = isWeaponForDiscipline(responsiblePlayer.getMainHandItem());
				for (EmeraldMercenaryEntity merc : villager.level().getEntitiesOfClass(EmeraldMercenaryEntity.class,
							villager.getBoundingBox().inflate(20.0D))) {
					var owner = merc.getOwnerUuid();
					if (owner != null && owner.equals(responsiblePlayer.getUUID())) {
						if (!merc.isNeutralOrder() || merc.hasLineOfSight(villager)) {
							merc.onOwnerMeleeHit(responsiblePlayer, usedWeapon);
						}
					}
				}
			}
			// Block IronGolem → mercenary damage (prevents accidental-hit retaliation)
			if (entity instanceof EmeraldMercenaryEntity && attacker instanceof IronGolem) {
				return false;
			}
			// Block mercenary → villager/golem damage (stray arrows, AOE)
			if (attacker instanceof EmeraldMercenaryEntity) {
				if (entity instanceof Player p) {
					var owner = ((EmeraldMercenaryEntity) attacker).getOwnerUuid();
					if (owner != null && owner.equals(p.getUUID()) && !((EmeraldMercenaryEntity) attacker).isDisciplineSlapDamageAllowed()) {
						return false;
					}
				}
				if (entity instanceof EmeraldMercenaryEntity other) {
					var owner = ((EmeraldMercenaryEntity) attacker).getOwnerUuid();
					var otherOwner = other.getOwnerUuid();
					if (owner != null && owner.equals(otherOwner)) {
						return false;
					}
				}
				if (entity instanceof AbstractVillager || entity instanceof IronGolem) {
					return false;
				}
			}
			// Also catch indirect damage (arrows): directEntity is the arrow, entity is who fired it
			var directEntity = source.getDirectEntity();
			if (directEntity != null && directEntity != attacker) {
				var ownerEntity = source.getEntity();
				if (ownerEntity instanceof EmeraldMercenaryEntity) {
					if (entity instanceof Player p) {
						var owner = ((EmeraldMercenaryEntity) ownerEntity).getOwnerUuid();
						if (owner != null && owner.equals(p.getUUID()) && !((EmeraldMercenaryEntity) ownerEntity).isDisciplineSlapDamageAllowed()) {
							return false;
						}
					}
					if (entity instanceof EmeraldMercenaryEntity other) {
						var owner = ((EmeraldMercenaryEntity) ownerEntity).getOwnerUuid();
						var otherOwner = other.getOwnerUuid();
						if (owner != null && owner.equals(otherOwner)) {
							return false;
						}
					}
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

	private static boolean isWeaponForDiscipline(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		if (item instanceof BowItem || item instanceof CrossbowItem) {
			return true;
		}
		final double[] extraDamage = new double[1];
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute != null && attribute.equals(Attributes.ATTACK_DAMAGE)) {
				extraDamage[0] += modifier.amount();
			}
		});
		return (1.0D + extraDamage[0]) > 1.0D;
	}
}