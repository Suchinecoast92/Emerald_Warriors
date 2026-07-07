package emeraldwarriors.network;

import emeraldwarriors.Emerald_Warriors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Server → client (single player): show a client-only glowing outline on a marked attack target.
 */
public record SpyglassMarkGlowPayload(UUID entityUuid, int durationTicks) implements CustomPacketPayload {

    public static final int DEFAULT_DURATION_TICKS = 60;

    public static final CustomPacketPayload.Type<SpyglassMarkGlowPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Emerald_Warriors.MOD_ID, "spyglass_mark_glow")
            );

    public static final StreamCodec<FriendlyByteBuf, SpyglassMarkGlowPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.entityUuid());
                        buf.writeVarInt(payload.durationTicks());
                    },
                    buf -> new SpyglassMarkGlowPayload(buf.readUUID(), buf.readVarInt())
            );

    public static SpyglassMarkGlowPayload of(UUID entityUuid) {
        return new SpyglassMarkGlowPayload(entityUuid, DEFAULT_DURATION_TICKS);
    }

    public static SpyglassMarkGlowPayload clear(UUID entityUuid) {
        return new SpyglassMarkGlowPayload(entityUuid, 0);
    }

    @Override
    public CustomPacketPayload.Type<SpyglassMarkGlowPayload> type() {
        return TYPE;
    }
}
