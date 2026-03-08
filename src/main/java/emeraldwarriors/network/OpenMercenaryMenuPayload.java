package emeraldwarriors.network;

import emeraldwarriors.Emerald_Warriors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload enviado desde el servidor al cliente para indicar que debe abrir
 * el inventario del mercenario. Lleva el entity ID para que el cliente
 * pueda localizar al mercenario y pasarle su inventario al menú.
 */
public record OpenMercenaryMenuPayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenMercenaryMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Emerald_Warriors.MOD_ID, "open_mercenary_menu")
            );

    public static final StreamCodec<FriendlyByteBuf, OpenMercenaryMenuPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeInt(payload.entityId()),
                    buf -> new OpenMercenaryMenuPayload(buf.readInt())
            );

    @Override
    public CustomPacketPayload.Type<OpenMercenaryMenuPayload> type() {
        return TYPE;
    }
}
