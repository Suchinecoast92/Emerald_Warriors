package emeraldwarriors.horn;

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
 * Registers Fabric event handlers for the goat horn mercenary group system.
 *
 * Link / Unlink mercenary  →  handled in EmeraldMercenaryEntity.mobInteract()
 *                             (Shift + Right-click on mercenary while holding horn)
 *
 * Shift + Right-click in air (UseItem)  →  Cycle stored order (vanilla suppressed)
 * Normal Right-click        (UseItem)   →  PASS → vanilla handles animation + cooldown
 *                                          InstrumentItemMixin commands linked mercs after vanilla fires
 */
public class MercenaryHornListener {

    public static void register() {
        registerItemUse();
    }

    private static void registerItemUse() {
        UseItemCallback.EVENT.register((Player player, Level level, InteractionHand hand) -> {

            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            if (!HornGroupManager.isGoatHorn(stack)) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;

            if (level.isClientSide()) return InteractionResult.SUCCESS;

            MercenaryOrder newOrder = HornGroupManager.cycleOrder(stack);
            int count = HornGroupManager.getLinkedCount(stack);
            player.displayClientMessage(Component.translatable("emerald_warriors.horn.order_cycle",
                            newOrder.getDisplayName(),
                            MercenaryTranslations.mercenaries(count))
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        });
    }
}
