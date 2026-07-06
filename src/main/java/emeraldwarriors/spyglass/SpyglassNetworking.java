package emeraldwarriors.spyglass;

import emeraldwarriors.network.SpyglassMarkGlowPayload;
import emeraldwarriors.network.SpyglassTacticalCommandPayload;
import emeraldwarriors.network.SpyglassTacticalCommandPayload.Action;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class SpyglassNetworking {

    private SpyglassNetworking() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(
                SpyglassTacticalCommandPayload.TYPE, SpyglassTacticalCommandPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                SpyglassMarkGlowPayload.TYPE, SpyglassMarkGlowPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SpyglassTacticalCommandPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handleTacticalCommand(player, payload));
        });
    }

    public static void sendMarkGlow(ServerPlayer player, UUID entityUuid) {
        ServerPlayNetworking.send(player, SpyglassMarkGlowPayload.of(entityUuid));
    }

    private static void handleTacticalCommand(ServerPlayer player, SpyglassTacticalCommandPayload payload) {
        ItemStack spyglass = resolveSpyglassStack(player);
        if (!SpyglassGroupManager.isSpyglass(spyglass)) {
            return;
        }

        if (payload.action() == Action.MOVE) {
            if (!isWithinCommandRange(player, Vec3.atCenterOf(payload.blockPos()))) {
                return;
            }
            SpyglassCommandHandler.issueMove(player, spyglass, payload.blockPos());
            return;
        }

        if (payload.targetEntityUuid() == null) {
            return;
        }
        LivingEntity living = findLivingTarget(player, payload.targetEntityUuid());
        if (living == null) {
            return;
        }
        if (!isWithinCommandRange(player, living.getEyePosition())) {
            return;
        }
        SpyglassCommandHandler.issueAttack(player, spyglass, living);
    }

    private static LivingEntity findLivingTarget(ServerPlayer player, UUID entityUuid) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        double range = SpyglassCommandHandler.COMMAND_RADIUS;
        AABB box = player.getBoundingBox().inflate(range);
        List<LivingEntity> matches = level.getEntitiesOfClass(
                LivingEntity.class, box, e -> e.getUUID().equals(entityUuid) && e.isAlive());
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static ItemStack resolveSpyglassStack(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (SpyglassGroupManager.isSpyglass(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isWithinCommandRange(ServerPlayer player, Vec3 target) {
        double max = SpyglassCommandHandler.COMMAND_RADIUS;
        return player.getEyePosition().distanceToSqr(target) <= max * max;
    }
}
