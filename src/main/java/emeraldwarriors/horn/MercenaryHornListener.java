package emeraldwarriors.horn;

import emeraldwarriors.mercenary.MercenaryOrder;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
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

    private static final String ORDER_SYMBOL = "♪";

    public static void register() {
        registerItemUse();
    }

    // ──────────────────────────────────────────────────────────────────
    // Shift + Right-click in air → cycle stored order (suppress vanilla)
    // Normal Right-click         → PASS (vanilla animation + sound)
    // ──────────────────────────────────────────────────────────────────
    private static void registerItemUse() {
        UseItemCallback.EVENT.register((Player player, Level level, InteractionHand hand) -> {

            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            if (!HornGroupManager.isGoatHorn(stack)) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;

            // Shift + click: suppress vanilla, cycle order
            if (level.isClientSide()) return InteractionResult.SUCCESS;

            MercenaryOrder newOrder = HornGroupManager.cycleOrder(stack);
            int count = HornGroupManager.getLinkedCount(stack);
            player.displayClientMessage(Component.literal("§e[" + ORDER_SYMBOL + "] Orden: §f" +
                    newOrder.getDisplayName() + " §7(" + count + " mercenario" + (count != 1 ? "s" : "") + ")"), true);
            return InteractionResult.SUCCESS;
        });
    }
}
