package emeraldwarriors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mercenary.MercenaryRank;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comando /mercenary para gestionar mercenarios.
 * Subcomandos:
 *   - addexp <cantidad>: Añade experiencia al mercenario que estés mirando
 */
public class MercenaryCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mercenary")
            .then(Commands.literal("addexp")
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                    .executes(MercenaryCommand::addExpCommand))));
    }

    private static int addExpCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(context, "amount");

        // Buscar mercenarios contratados en un radio de 10 bloques
        double radius = 10.0;
        var nearbyMercenaries = player.level().getEntitiesOfClass(EmeraldMercenaryEntity.class, 
            player.getBoundingBox().inflate(radius))
            .stream()
            .filter(mercenary -> player.getUUID().equals(mercenary.getOwnerUuid()))
            .toList();

        if (nearbyMercenaries.isEmpty()) {
            source.sendFailure(Component.literal("No hay mercenarios tuyos cerca (radio " + (int)radius + " bloques)."));
            return 0;
        }

        int rankUps = 0;
        
        // Añadir experiencia a todos los mercenarios cercanos
        for (EmeraldMercenaryEntity mercenary : nearbyMercenaries) {
            MercenaryRank oldRank = mercenary.getRank();
            mercenary.addExperience(amount);
            MercenaryRank newRank = mercenary.getRank();
            
            if (oldRank != newRank) {
                rankUps++;
                source.sendSuccess(() -> Component.literal("§6★ Mercenario " + mercenary.getId() + 
                    " subió de rango: " + oldRank.name() + " → " + newRank.name()), false);
            }
        }

        // Variables finales para lambdas
        final int finalRankUps = rankUps;
        final int mercenaryCount = nearbyMercenaries.size();
        
        // Mensaje de resumen
        source.sendSuccess(() -> Component.literal("§a✓ Añadido " + amount + " EXP a " + 
            mercenaryCount + " mercenario" + (mercenaryCount == 1 ? "" : "s")), false);
        
        if (finalRankUps > 0) {
            source.sendSuccess(() -> Component.literal("§6★ " + finalRankUps + " mercenario" + 
                (finalRankUps == 1 ? "" : "s") + " subió de rango!"), false);
        }

        return nearbyMercenaries.size();
    }
}
