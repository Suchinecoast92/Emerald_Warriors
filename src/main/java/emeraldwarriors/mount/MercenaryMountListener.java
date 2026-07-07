package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Paso 2 del vínculo: Shift + clic derecho con correa en la montura.
 * El paso 1 (seleccionar mercenario) se maneja en {@link EmeraldMercenaryEntity#mobInteract}.
 */
public final class MercenaryMountListener {

    private MercenaryMountListener() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.LEAD)) {
                return InteractionResult.PASS;
            }
            if (!MercenaryMounts.isSupportedMount(entity)) {
                return InteractionResult.PASS;
            }
            AbstractHorse mount = (AbstractHorse) entity;
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }

            EmeraldMercenaryEntity merc = findPendingMercenary(player, mount, serverLevel);
            if (merc == null) {
                player.displayClientMessage(
                        Component.translatable("emerald_warriors.mount.no_pending_merc")
                                .withStyle(ChatFormatting.RED),
                        false
                );
                return InteractionResult.CONSUME;
            }

            if (!mount.isSaddled()) {
                player.displayClientMessage(
                        Component.translatable("emerald_warriors.mount.needs_saddle")
                                .withStyle(ChatFormatting.RED),
                        false
                );
                return InteractionResult.CONSUME;
            }

            if (mount.getUUID().equals(merc.getBoundHorseUuid())) {
                merc.clearHorseBinding();
                player.displayClientMessage(
                        Component.translatable(
                                "emerald_warriors.mount.unbound",
                                MercenaryMountHelper.mountDisplayName(mount)
                        ).withStyle(ChatFormatting.YELLOW),
                        false
                );
                return InteractionResult.CONSUME;
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            MercenaryMountHelper.claimHorseForMercenary(serverLevel, merc, mount);
            merc.clearHorseBindSelection();
            player.displayClientMessage(
                    Component.translatable(
                            "emerald_warriors.mount.bound",
                            MercenaryMountHelper.mountDisplayName(mount)
                    ).withStyle(ChatFormatting.GREEN),
                    false
            );
            return InteractionResult.CONSUME;
        });
    }

    private static EmeraldMercenaryEntity findPendingMercenary(
            Player player,
            AbstractHorse mount,
            ServerLevel level
    ) {
        EmeraldMercenaryEntity best = null;
        double bestDist = MercenaryMountHelper.BIND_RANGE * MercenaryMountHelper.BIND_RANGE;
        for (EmeraldMercenaryEntity merc : level.getEntitiesOfClass(
                EmeraldMercenaryEntity.class,
                mount.getBoundingBox().inflate(MercenaryMountHelper.BIND_RANGE),
                m -> m.isAlive() && m.isAwaitingHorseBindFrom(player)
        )) {
            double dist = merc.distanceToSqr(mount);
            if (dist <= bestDist) {
                bestDist = dist;
                best = merc;
            }
        }
        return best;
    }
}
