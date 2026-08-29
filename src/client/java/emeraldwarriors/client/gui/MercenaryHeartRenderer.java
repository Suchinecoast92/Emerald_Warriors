package emeraldwarriors.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Vanilla-style heart row for the mercenary GUI: damage drain lag and heal/damage blink sprites.
 */
final class MercenaryHeartRenderer {

    private static final Identifier CONTAINER        = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier CONTAINER_BLINK  = Identifier.withDefaultNamespace("hud/heart/container_blinking");
    private static final Identifier FULL             = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HALF             = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier FULL_BLINK         = Identifier.withDefaultNamespace("hud/heart/full_blinking");
    private static final Identifier HALF_BLINK         = Identifier.withDefaultNamespace("hud/heart/half_blinking");
    private static final Identifier POISONED_FULL      = Identifier.withDefaultNamespace("hud/heart/poisoned_full");
    private static final Identifier POISONED_HALF      = Identifier.withDefaultNamespace("hud/heart/poisoned_half");
    private static final Identifier POISONED_FULL_BLINK = Identifier.withDefaultNamespace("hud/heart/poisoned_full_blinking");
    private static final Identifier POISONED_HALF_BLINK = Identifier.withDefaultNamespace("hud/heart/poisoned_half_blinking");
    private static final Identifier WITHERED_FULL      = Identifier.withDefaultNamespace("hud/heart/withered_full");
    private static final Identifier WITHERED_HALF      = Identifier.withDefaultNamespace("hud/heart/withered_half");
    private static final Identifier WITHERED_FULL_BLINK = Identifier.withDefaultNamespace("hud/heart/withered_full_blinking");
    private static final Identifier WITHERED_HALF_BLINK = Identifier.withDefaultNamespace("hud/heart/withered_half_blinking");

    private static final Map<UUID, HeartAnimState> STATES = new WeakHashMap<>();

    private MercenaryHeartRenderer() {
    }

    static void render(GuiGraphics graphics, LivingEntity entity, int x, int y, int maxHeartsToDraw) {
        if (entity == null || maxHeartsToDraw <= 0) {
            return;
        }

        HeartAnimState state = STATES.computeIfAbsent(entity.getUUID(), ignored -> new HeartAnimState());
        int ticks = entity.tickCount;
        int currentHealth = Mth.ceil(entity.getHealth());
        float maxHealth = Math.max(entity.getMaxHealth(), (float) currentHealth);
        int heartsToDraw = Math.min(Mth.ceil(maxHealth / 2.0F), maxHeartsToDraw);

        boolean blinking = state.heartJumpEndTick > ticks
                && (state.heartJumpEndTick - ticks) / 3L % 2L == 1L;
        long now = Util.getMillis();

        if (currentHealth < state.lastHealthValue) {
            state.lastHealthCheckTime = now;
            state.heartJumpEndTick = ticks + 20;
        } else if (currentHealth > state.lastHealthValue) {
            state.lastHealthCheckTime = now;
            state.heartJumpEndTick = ticks + 10;
        }

        if (now - state.lastHealthCheckTime > 1000L) {
            state.renderHealthValue = currentHealth;
            state.lastHealthCheckTime = now;
        }

        state.lastHealthValue = currentHealth;
        int laggedHealth = state.renderHealthValue;

        int regeneratingHeartIndex = -1;
        if (entity.hasEffect(MobEffects.REGENERATION)) {
            regeneratingHeartIndex = ticks % Mth.ceil(maxHealth + 5.0F);
        }

        HeartSprites sprites = heartSpritesFor(entity);
        boolean lowHealthShake = currentHealth + Mth.ceil(entity.getAbsorptionAmount()) <= 4;

        for (int i = 0; i < heartsToDraw; i++) {
            int heartX = x + i * 9;
            int heartY = y;
            if (lowHealthShake) {
                heartY += entity.getRandom().nextInt(2);
            }
            if (i == regeneratingHeartIndex) {
                heartY -= 1;
            }

            int healthPoints = i * 2;
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    blinking ? CONTAINER_BLINK : CONTAINER,
                    heartX, heartY, 9, 9);

            if (blinking && healthPoints < laggedHealth) {
                boolean half = healthPoints + 1 == laggedHealth;
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        half ? sprites.halfBlink() : sprites.fullBlink(),
                        heartX, heartY, 9, 9);
            }

            if (healthPoints < currentHealth) {
                boolean half = healthPoints + 1 == currentHealth;
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        half ? sprites.half() : sprites.full(),
                        heartX, heartY, 9, 9);
            }
        }
    }

    private static HeartSprites heartSpritesFor(LivingEntity entity) {
        if (entity.hasEffect(MobEffects.POISON)) {
            return new HeartSprites(POISONED_FULL, POISONED_HALF, POISONED_FULL_BLINK, POISONED_HALF_BLINK);
        }
        if (entity.hasEffect(MobEffects.WITHER)) {
            return new HeartSprites(WITHERED_FULL, WITHERED_HALF, WITHERED_FULL_BLINK, WITHERED_HALF_BLINK);
        }
        return new HeartSprites(FULL, HALF, FULL_BLINK, HALF_BLINK);
    }

    private record HeartSprites(Identifier full, Identifier half, Identifier fullBlink, Identifier halfBlink) {
    }

    private static final class HeartAnimState {
        private int lastHealthValue = -1;
        private int renderHealthValue;
        private long heartJumpEndTick = -1;
        private long lastHealthCheckTime;
    }
}
