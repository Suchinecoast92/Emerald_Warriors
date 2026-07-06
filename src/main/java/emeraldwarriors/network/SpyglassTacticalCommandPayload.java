package emeraldwarriors.network;

import emeraldwarriors.Emerald_Warriors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Client → server: tactical move or attack issued while scoping with a spyglass.
 */
public record SpyglassTacticalCommandPayload(Action action, BlockPos blockPos, UUID targetEntityUuid)
        implements CustomPacketPayload {

    private static final UUID NO_ENTITY = new UUID(0L, 0L);

    public enum Action {
        MOVE,
        ATTACK
    }

    public static final CustomPacketPayload.Type<SpyglassTacticalCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Emerald_Warriors.MOD_ID, "spyglass_tactical_command")
            );

    public static final StreamCodec<FriendlyByteBuf, SpyglassTacticalCommandPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.action());
                        if (payload.action() == Action.MOVE) {
                            buf.writeBlockPos(payload.blockPos());
                        } else {
                            buf.writeUUID(payload.targetEntityUuid());
                        }
                    },
                    buf -> {
                        Action action = buf.readEnum(Action.class);
                        if (action == Action.MOVE) {
                            return new SpyglassTacticalCommandPayload(action, buf.readBlockPos(), NO_ENTITY);
                        }
                        return new SpyglassTacticalCommandPayload(action, BlockPos.ZERO, buf.readUUID());
                    }
            );

    public static SpyglassTacticalCommandPayload move(BlockPos pos) {
        return new SpyglassTacticalCommandPayload(Action.MOVE, pos, NO_ENTITY);
    }

    public static SpyglassTacticalCommandPayload attack(UUID entityUuid) {
        return new SpyglassTacticalCommandPayload(Action.ATTACK, BlockPos.ZERO, entityUuid);
    }

    @Override
    public CustomPacketPayload.Type<SpyglassTacticalCommandPayload> type() {
        return TYPE;
    }
}
