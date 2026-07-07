package emeraldwarriors.mount;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * Utilidades compartidas para monturas soportadas: caballo, burro, mula y camello
 * (todos extienden {@link AbstractHorse} en 1.21.11).
 */
public final class MercenaryMounts {

    private static final double EQUINE_RIDE_ATTACH_Y = 0.58D;
    private static final double CAMEL_RIDE_ATTACH_Y = 0.82D;
    private static final double EQUINE_RENDER_OFFSET_Y = 0.05D;
    private static final double CAMEL_RENDER_OFFSET_Y = 0.12D;
    /** Ritmo de referencia de equinos domados (caballo medio en vanilla). */
    private static final double EQUINE_REFERENCE_MOVEMENT_SPEED = 0.225D;
    /**
     * El camello camina más lento con IA que un equino al mismo goalSpeed; compensamos
     * para que el ritmo montado se sienta similar tras los boosts globales.
     */
    private static final double CAMEL_MOUNTED_NAV_FLOOR = 1.55D;

    private MercenaryMounts() {
    }

    public static boolean isSupportedMount(Entity entity) {
        return entity instanceof AbstractHorse;
    }

    public static boolean isCamel(AbstractHorse mount) {
        return mount instanceof Camel;
    }

    public static double getRideAttachmentYOffset(Entity vehicle) {
        if (vehicle instanceof Camel) {
            return CAMEL_RIDE_ATTACH_Y;
        }
        if (vehicle instanceof AbstractHorse) {
            return EQUINE_RIDE_ATTACH_Y;
        }
        return 0.0D;
    }

    public static double getRenderYOffsetY(AbstractHorse mount) {
        return isCamel(mount) ? CAMEL_RENDER_OFFSET_Y : EQUINE_RENDER_OFFSET_Y;
    }

    /**
     * Escala extra de pathfinding montado según la velocidad base de la montura.
     * Equinos: 1.0. Camello: ratio respecto al caballo medio, con piso para no ir a cámara lenta.
     */
    public static double getMountedNavigationScale(AbstractHorse mount) {
        if (!isCamel(mount)) {
            return 1.0D;
        }
        double baseSpeed = mount.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (baseSpeed <= 0.0D) {
            return CAMEL_MOUNTED_NAV_FLOOR;
        }
        return Math.max(CAMEL_MOUNTED_NAV_FLOOR, EQUINE_REFERENCE_MOVEMENT_SPEED / baseSpeed);
    }

    /** Levanta camellos sentados antes de montar o moverse con ellos. */
    public static void prepareForMount(AbstractHorse mount) {
        if (mount instanceof Camel camel && camel.isCamelSitting()) {
            camel.standUpInstantly();
        }
    }
}
