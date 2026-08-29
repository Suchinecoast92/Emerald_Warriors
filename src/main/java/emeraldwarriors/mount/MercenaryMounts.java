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
     * El camello tiene ~0.09 de MOVEMENT_SPEED (equino medio ~0.225). Sin escala, la IA
     * lo deja a cámara lenta. Compensamos para igualar el ritmo de un caballo medio;
     * caballo/burro/mula siguen en 1.0 y conservan su atributo individual en MoveControl.
     */
    private static final double CAMEL_MOUNTED_NAV_FLOOR = 1.55D;
    /** Extra por desvíos del pathfinding (el camello pagaba más con nodos anchos). */
    private static final double CAMEL_PATHFIND_COMPENSATION = 1.10D;
    /** Velocidad base al seguir al mercenario a pie ({@code modifier × MOVEMENT_SPEED}). */
    private static final double LOOSE_FOLLOW_SPEED = 1.10D;

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
     * Escala de pathfinding IA según especie.
     * <ul>
     *   <li>Equinos: {@code 1.0} — la velocidad real sale de {@code modifier × MOVEMENT_SPEED}
     *       (caballo rápido vs burro lento se nota).</li>
     *   <li>Camello: sube el modifier para compensar su atributo bajo (~0.09).</li>
     * </ul>
     */
    public static double getMountedNavigationScale(AbstractHorse mount) {
        if (!isCamel(mount)) {
            return 1.0D;
        }
        // Base value: evita que el sprint temporal reduzca la escala y frene al camello.
        double baseSpeed = mount.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        if (baseSpeed <= 0.0D) {
            return CAMEL_MOUNTED_NAV_FLOOR * CAMEL_PATHFIND_COMPENSATION;
        }
        double scale = EQUINE_REFERENCE_MOVEMENT_SPEED / baseSpeed;
        return Math.max(CAMEL_MOUNTED_NAV_FLOOR, scale) * CAMEL_PATHFIND_COMPENSATION;
    }

    /**
     * Modifier para {@code navigation.moveTo} / steer de la montura suelta o montada.
     * La velocidad en bloques/s sigue siendo {@code modifier × Attributes.MOVEMENT_SPEED}.
     */
    public static double resolveAiMoveSpeedModifier(AbstractHorse mount, double baseModifier) {
        return baseModifier * getMountedNavigationScale(mount);
    }

    /** Caballo/burro/mula/camello siguiendo a su mercenario a pie. */
    public static double resolveLooseFollowSpeedModifier(AbstractHorse mount) {
        return resolveAiMoveSpeedModifier(mount, LOOSE_FOLLOW_SPEED);
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
