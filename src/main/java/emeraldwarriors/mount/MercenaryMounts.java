package emeraldwarriors.mount;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import java.util.UUID;

/**
 * Utilidades compartidas para monturas soportadas: caballo, burro, mula y camello
 * (todos extienden {@link AbstractHorse} en 1.21.11).
 */
public final class MercenaryMounts {

    private static final double EQUINE_RIDE_ATTACH_Y = 0.74D;
    private static final double EQUINE_RENDER_OFFSET_Y = 0.05D;
    /** Solo render: bajar levemente el modelo sobre la joroba (sin tocar posición lógica). */
    private static final double CAMEL_RENDER_OFFSET_Y = -0.08D;
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
        if (vehicle instanceof AbstractHorse && !(vehicle instanceof Camel)) {
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

    /** True when the entity is a horse/camel/donkey bound to any mercenary. */
    public static boolean isMercenaryBoundMount(LivingEntity entity) {
        if (!(entity instanceof AbstractHorse horse) || !horse.isAlive()) {
            return false;
        }
        if (!(horse.level() instanceof ServerLevel level)) {
            return false;
        }
        return !level.getEntitiesOfClass(
                EmeraldMercenaryEntity.class,
                horse.getBoundingBox().inflate(64.0D),
                merc -> merc.isAlive() && horse.getUUID().equals(merc.getBoundHorseUuid())
        ).isEmpty();
    }

    /**
     * True when the mount belongs to this mercenary or to another mercenary hired by the same owner.
     * Wild mercenaries treat every bound mount as protected.
     */
    public static boolean isAlliedMercenaryMount(LivingEntity entity, EmeraldMercenaryEntity mercenary) {
        if (!(entity instanceof AbstractHorse horse) || !horse.isAlive()) {
            return false;
        }
        if (horse.getUUID().equals(mercenary.getBoundHorseUuid())) {
            return true;
        }
        UUID ownerId = mercenary.getOwnerUuid();
        if (ownerId == null) {
            return isMercenaryBoundMount(entity);
        }
        if (!(horse.level() instanceof ServerLevel level)) {
            return false;
        }
        for (EmeraldMercenaryEntity other : level.getEntitiesOfClass(
                EmeraldMercenaryEntity.class,
                horse.getBoundingBox().inflate(64.0D),
                m -> m.isAlive() && horse.getUUID().equals(m.getBoundHorseUuid())
        )) {
            if (ownerId.equals(other.getOwnerUuid())) {
                return true;
            }
        }
        return false;
    }
}
