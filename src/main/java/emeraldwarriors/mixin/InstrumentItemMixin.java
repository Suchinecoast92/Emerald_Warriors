package emeraldwarriors.mixin;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.horn.HornGroupManager;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

/**
 * Hooks into InstrumentItem.use() to command linked mercenaries whenever
 * the vanilla horn actually fires (i.e. the cooldown check passed and the
 * sound is about to play, signalled by a CONSUME return value).
 *
 * This preserves the full vanilla animation + cooldown while adding our
 * mercenary command logic on top.
 */
@Mixin(InstrumentItem.class)
public class InstrumentItemMixin {

    private static final double COMMAND_RADIUS = 64.0;
    private static final String ORDER_SYMBOL   = "♪";

    @Inject(at = @At("RETURN"), method = "use")
    private void afterHornUse(Level level, Player player, InteractionHand hand,
                               CallbackInfoReturnable<InteractionResult> cir) {
        // Only proceed when vanilla actually played the sound (CONSUME = success)
        if (!cir.getReturnValue().consumesAction()) return;
        // Skip on client side and skip shift-clicks (those cycle order, not command)
        if (level.isClientSide() || player.isShiftKeyDown()) return;

        ItemStack stack = player.getItemInHand(hand);
        if (!HornGroupManager.isGoatHorn(stack)) return;

        List<UUID> linked = HornGroupManager.getLinkedMercenaries(stack);
        if (linked.isEmpty()) return;

        MercenaryOrder order = HornGroupManager.getOrder(stack);
        AABB searchBox = new AABB(
                player.getX() - COMMAND_RADIUS, player.getY() - COMMAND_RADIUS, player.getZ() - COMMAND_RADIUS,
                player.getX() + COMMAND_RADIUS, player.getY() + COMMAND_RADIUS, player.getZ() + COMMAND_RADIUS
        );

        List<EmeraldMercenaryEntity> nearbyMercs = level.getEntitiesOfClass(
                EmeraldMercenaryEntity.class, searchBox,
                e -> e.isAlive() && linked.contains(e.getUUID()));

        int commanded = 0;
        for (EmeraldMercenaryEntity merc : nearbyMercs) {
            merc.setCurrentOrderFromHorn(order, player);
            commanded++;
        }

        if (commanded > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§6[" + ORDER_SYMBOL + "] §f" + commanded +
                    " mercenario" + (commanded != 1 ? "s" : "") +
                    " → §e" + order.getDisplayName()), true);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7[" + ORDER_SYMBOL + "] Ningún mercenario vinculado en rango."), true);
        }
    }
}
