package emeraldwarriors.mixin;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.horn.HornGroupManager;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mercenary.MercenaryTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

@Mixin(InstrumentItem.class)
public class InstrumentItemMixin {

    private static final double COMMAND_RADIUS = 128.0;

    @Inject(at = @At("RETURN"), method = "use")
    private void afterHornUse(Level level, Player player, InteractionHand hand,
                               CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
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
            player.displayClientMessage(Component.translatable("emerald_warriors.horn.command",
                            MercenaryTranslations.mercenaries(commanded),
                            order.getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            player.displayClientMessage(Component.translatable("emerald_warriors.horn.none_in_range")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }
}
