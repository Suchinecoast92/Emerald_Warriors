package emeraldwarriors.spyglass;

import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mercenary.MercenaryTranslations;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Shift + right-click in air with spyglass: cycle stored order only (like horn).
 * Tactical commands (move / attack) are issued with left-click while using the spyglass.
 */
public final class MercenarySpyglassListener {

    private MercenarySpyglassListener() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((Player player, Level level, InteractionHand hand) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            if (!SpyglassGroupManager.isSpyglass(stack)) {
                return InteractionResult.PASS;
            }
            if (!player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }

            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            MercenaryOrder newOrder = SpyglassGroupManager.cycleOrder(stack);
            int count = SpyglassGroupManager.getLinkedCount(stack);
            player.displayClientMessage(Component.translatable("emerald_warriors.spyglass.order_cycle",
                            newOrder.getDisplayName(),
                            MercenaryTranslations.mercenaries(count))
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        });
    }
}
