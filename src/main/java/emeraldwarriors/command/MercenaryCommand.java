package emeraldwarriors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryRank;
import emeraldwarriors.mercenary.MercenaryTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public class MercenaryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mercenary")
            .then(Commands.literal("addexp")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                    .executes(MercenaryCommand::addExpCommand))));
    }

    private static int addExpCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(context, "amount");

        double radius = 10.0;
        var nearbyMercenaries = player.level().getEntitiesOfClass(EmeraldMercenaryEntity.class,
            player.getBoundingBox().inflate(radius))
            .stream()
            .filter(mercenary -> player.getUUID().equals(mercenary.getOwnerUuid()))
            .toList();

        if (nearbyMercenaries.isEmpty()) {
            source.sendFailure(Component.translatable("emerald_warriors.command.no_mercenaries", (int) radius));
            return 0;
        }

        int rankUps = 0;

        for (EmeraldMercenaryEntity mercenary : nearbyMercenaries) {
            MercenaryRank oldRank = mercenary.getRank();
            mercenary.addExperience(amount);
            MercenaryRank newRank = mercenary.getRank();

            if (oldRank != newRank) {
                rankUps++;
                String mercName = mercenary.getMercenaryName();
                source.sendSuccess(() -> Component.translatable("emerald_warriors.command.rank_up",
                        mercName, newRank.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
            }
        }

        final int finalRankUps = rankUps;
        final int mercenaryCount = nearbyMercenaries.size();

        source.sendSuccess(() -> Component.translatable("emerald_warriors.command.exp_added",
                amount, MercenaryTranslations.mercenaries(mercenaryCount)).withStyle(ChatFormatting.GREEN), false);

        if (finalRankUps > 0) {
            player.displayClientMessage(Component.translatable("emerald_warriors.command.rank_ups",
                            MercenaryTranslations.mercenaries(finalRankUps))
                    .withStyle(ChatFormatting.GOLD), true);
        }

        return nearbyMercenaries.size();
    }
}
