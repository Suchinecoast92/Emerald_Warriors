package emeraldwarriors.spyglass;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mercenary.MercenaryTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public final class SpyglassCommandHandler {

    public static final double COMMAND_RADIUS = 64.0;

    private SpyglassCommandHandler() {
    }

    public static void applyPersistentOrder(ServerPlayer player, ItemStack spyglass, MercenaryOrder order) {
        List<UUID> linked = SpyglassGroupManager.getLinkedMercenaries(spyglass);
        if (linked.isEmpty()) {
            return;
        }

        AABB box = player.getBoundingBox().inflate(COMMAND_RADIUS);
        UUID commanderId = player.getUUID();
        List<EmeraldMercenaryEntity> mercs = player.level().getEntitiesOfClass(
                EmeraldMercenaryEntity.class, box,
                e -> e.isAlive() && linked.contains(e.getUUID()) && commanderId.equals(e.getOwnerUuid()));

        for (EmeraldMercenaryEntity merc : mercs) {
            merc.setCurrentOrderFromSpyglass(order, player);
        }

        if (!mercs.isEmpty()) {
            player.displayClientMessage(Component.translatable("emerald_warriors.spyglass.order_applied",
                            order.getDisplayName(),
                            MercenaryTranslations.mercenaries(mercs.size()))
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    public static void issueMove(ServerPlayer player, ItemStack spyglass, BlockPos targetPos) {
        List<EmeraldMercenaryEntity> mercs = findLinkedMercenariesInRange(player, spyglass);
        if (mercs.isEmpty()) {
            player.displayClientMessage(Component.translatable("emerald_warriors.spyglass.none_linked")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        int commanded = 0;
        MercenaryOrder order = SpyglassGroupManager.getOrder(spyglass);
        for (EmeraldMercenaryEntity merc : mercs) {
            if (merc.applyTacticalMove(targetPos, player, order)) {
                commanded++;
            }
        }

        if (commanded > 0 && player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.0);
            for (EmeraldMercenaryEntity merc : mercs) {
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        merc.getX(), merc.getY() + 1.0, merc.getZ(), 4, 0.2, 0.3, 0.2, 0.05);
            }
            player.displayClientMessage(Component.translatable("emerald_warriors.spyglass.move",
                            MercenaryTranslations.mercenaries(commanded))
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    public static void issueAttack(ServerPlayer player, ItemStack spyglass, LivingEntity target) {
        if (!isValidAttackTarget(player, target)) {
            return;
        }

        SpyglassNetworking.sendMarkGlow(player, target.getUUID());

        List<EmeraldMercenaryEntity> mercs = findLinkedMercenariesInRange(player, spyglass);
        if (mercs.isEmpty()) {
            player.displayClientMessage(Component.translatable("emerald_warriors.spyglass.none_linked")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        int commanded = 0;
        MercenaryOrder order = SpyglassGroupManager.getOrder(spyglass);
        for (EmeraldMercenaryEntity merc : mercs) {
            if (merc.applyTacticalAttack(target, player, order)) {
                commanded++;
            }
        }

        if (commanded > 0 && player.level() instanceof ServerLevel sl) {
            for (EmeraldMercenaryEntity merc : mercs) {
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        merc.getX(), merc.getY() + 1.0, merc.getZ(), 4, 0.2, 0.3, 0.2, 0.05);
            }
            Component targetName = target.getDisplayName();
            player.displayClientMessage(Component.translatable("emerald_warriors.spyglass.attack",
                            MercenaryTranslations.mercenaries(commanded), targetName)
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static List<EmeraldMercenaryEntity> findLinkedMercenariesInRange(Player player, ItemStack spyglass) {
        List<UUID> linked = SpyglassGroupManager.getLinkedMercenaries(spyglass);
        if (linked.isEmpty()) {
            return List.of();
        }
        AABB box = player.getBoundingBox().inflate(COMMAND_RADIUS);
        UUID commanderId = player.getUUID();
        return player.level().getEntitiesOfClass(
                EmeraldMercenaryEntity.class, box,
                e -> e.isAlive() && linked.contains(e.getUUID()) && commanderId.equals(e.getOwnerUuid()));
    }

    private static boolean isValidAttackTarget(Player commander, LivingEntity target) {
        if (target == commander || !target.isAlive()) {
            return false;
        }
        if (target instanceof EmeraldMercenaryEntity merc) {
            return merc.getOwnerUuid() == null || !merc.getOwnerUuid().equals(commander.getUUID());
        }
        return true;
    }
}
