package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;

/**
 * Hace que el mercenario levante su escudo (offhand) cuando un Creeper cercano
 * está a punto de explotar. Prioridad muy alta para interrumpir el ataque.
 * - Se agacha (shift) para reducir perfil.
 * - Mira de FRENTE al creeper para que el escudo lo cubra bien.
 * - Retrocede a una distancia segura (~4 bloques) para evitar salir volando.
 */
public class ShieldAgainstCreeperGoal extends Goal {
    private final EmeraldMercenaryEntity mercenary;
    private final double detectionRange;
    private Creeper dangerousCreeper;

    // Umbral de hinchamiento del creeper para levantar el escudo (0.0 - 1.0)
    private static final float SWELL_THRESHOLD = 0.5F;

    public ShieldAgainstCreeperGoal(EmeraldMercenaryEntity mercenary, double detectionRange) {
        this.mercenary = mercenary;
        this.detectionRange = detectionRange;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ItemStack offhand = this.mercenary.getOffhandItem();
        if (!offhand.is(Items.SHIELD)) {
            return false;
        }

        List<Creeper> creepers = this.mercenary.level().getEntitiesOfClass(
                Creeper.class,
                this.mercenary.getBoundingBox().inflate(this.detectionRange),
                c -> c.isAlive() && c.getSwellDir() > 0 && c.getSwelling(1.0F) >= SWELL_THRESHOLD
        );

        if (creepers.isEmpty()) {
            return false;
        }

        this.dangerousCreeper = creepers.stream()
                .min((a, b) -> {
                    float swellA = a.getSwelling(1.0F);
                    float swellB = b.getSwelling(1.0F);
                    if (swellA != swellB) {
                        return Float.compare(swellB, swellA);
                    }
                    return Double.compare(
                            this.mercenary.distanceToSqr(a),
                            this.mercenary.distanceToSqr(b));
                })
                .orElse(null);

        return this.dangerousCreeper != null;
    }

    @Override
    public boolean canContinueToUse() {
        ItemStack offhand = this.mercenary.getOffhandItem();
        if (!offhand.is(Items.SHIELD)) {
            return false;
        }
        if (this.dangerousCreeper == null || !this.dangerousCreeper.isAlive()) {
            return false;
        }
        if (this.dangerousCreeper.getSwellDir() <= 0 && this.dangerousCreeper.getSwelling(1.0F) < SWELL_THRESHOLD) {
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        this.mercenary.getNavigation().stop();
        // Agacharse y levantar escudo
        this.mercenary.setShiftKeyDown(true);
        this.mercenary.startUsingItem(InteractionHand.OFF_HAND);
        // Girar inmediatamente de frente al creeper
        faceCreeper();
    }

    @Override
    public void tick() {
        if (this.dangerousCreeper == null || !this.dangerousCreeper.isAlive()) {
            return;
        }

        // === 1. Girar de frente al creeper cada tick ===
        faceCreeper();

        // === 2. Mantener escudo arriba ===
        if (!this.mercenary.isUsingItem()) {
            ItemStack offhand = this.mercenary.getOffhandItem();
            if (offhand.is(Items.SHIELD)) {
                this.mercenary.startUsingItem(InteractionHand.OFF_HAND);
            }
        }

        // === 3. Quedarse quieto mientras se protege ===
        this.mercenary.getNavigation().stop();

        // Mantener agachado
        this.mercenary.setShiftKeyDown(true);
    }

    @Override
    public void stop() {
        this.mercenary.stopUsingItem();
        this.mercenary.setShiftKeyDown(false);
        this.dangerousCreeper = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * Fuerza la rotación completa (yaw cuerpo + cabeza) para que el mercenario
     * mire directamente al creeper. Esto asegura que el escudo se renderice
     * de frente, como Alex en las imágenes de referencia.
     */
    private void faceCreeper() {
        if (this.dangerousCreeper == null) return;

        double dx = this.dangerousCreeper.getX() - this.mercenary.getX();
        double dz = this.dangerousCreeper.getZ() - this.mercenary.getZ();
        double dy = (this.dangerousCreeper.getEyeY()) - this.mercenary.getEyeY();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, horizontalDist) * Mth.RAD_TO_DEG);

        // Fijar rotación inmediata (cuerpo, cabeza y pitch)
        this.mercenary.setYRot(yaw);
        this.mercenary.yBodyRot = yaw;
        this.mercenary.yHeadRot = yaw;
        this.mercenary.setXRot(pitch);
    }
}
