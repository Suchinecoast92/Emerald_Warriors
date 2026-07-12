package emeraldwarriors.spyglass;

import emeraldwarriors.network.SpyglassMarkGlowPayload;
import emeraldwarriors.network.SpyglassTacticalCommandPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Intercepts left-click while scoping with a spyglass and sends a tactical command to the server.
 */
public final class MercenarySpyglassClient {

    private static final int COMMAND_COOLDOWN_TICKS = 8;
    private static int lastCommandTick = -1000;

    private MercenarySpyglassClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SpyglassMarkGlowPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    SpyglassClientGlowTracker.mark(payload.entityUuid(), payload.durationTicks()));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> SpyglassClientGlowTracker.tickCleanup());

        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (clickCount <= 0) {
                return false;
            }
            if (!player.isUsingItem()) {
                return false;
            }
            ItemStack useItem = player.getUseItem();
            if (!SpyglassGroupManager.isSpyglass(useItem)) {
                return false;
            }
            int now = player.tickCount;
            // Tras salir y volver a entrar al mundo, el LocalPlayer reinicia tickCount a 0,
            // pero este campo estático conserva el valor de la sesión anterior. Sin este
            // ajuste, el cooldown quedaría "activo" durante minutos y las órdenes no se
            // enviarían (aunque vincular/desvincular sí funciona porque no usa este cooldown).
            if (now < lastCommandTick) {
                lastCommandTick = now - COMMAND_COOLDOWN_TICKS;
            }
            if (now - lastCommandTick < COMMAND_COOLDOWN_TICKS) {
                return true;
            }

            double range = SpyglassCommandHandler.COMMAND_RADIUS;
            LivingEntity livingTarget = resolveLivingTarget(client, player, range);
            if (livingTarget != null) {
                lastCommandTick = player.tickCount;
                ClientPlayNetworking.send(SpyglassTacticalCommandPayload.attack(livingTarget.getUUID()));
                return true;
            }

            BlockHitResult blockHit = SpyglassTargeting.findBlockTarget(player, range);
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                lastCommandTick = player.tickCount;
                ClientPlayNetworking.send(SpyglassTacticalCommandPayload.move(blockHit.getBlockPos()));
                return true;
            }

            return false;
        });
    }

    private static LivingEntity resolveLivingTarget(Minecraft client, net.minecraft.world.entity.player.Player player, double range) {
        HitResult crosshair = client.hitResult;
        if (crosshair instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                && living != player
                && player.distanceToSqr(living) <= range * range) {
            return living;
        }

        EntityHitResult extended = SpyglassTargeting.findEntityHit(player, range);
        if (extended != null
                && extended.getEntity() instanceof LivingEntity living
                && SpyglassTargeting.isEntityCloserThanBlock(player, range, extended)) {
            return living;
        }
        return null;
    }
}
