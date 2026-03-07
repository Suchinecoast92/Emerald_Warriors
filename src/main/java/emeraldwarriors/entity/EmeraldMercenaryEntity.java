package emeraldwarriors.entity;

import emeraldwarriors.mercenary.MercenaryRole;
import emeraldwarriors.mercenary.MercenaryRank;
import emeraldwarriors.entity.ai.EmeraldFollowOwnerGoal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;

import java.util.UUID;

public class EmeraldMercenaryEntity extends PathfinderMob {

    private MercenaryRole currentRole = MercenaryRole.NONE;

    private MeleeAttackGoal meleeAttackGoal;

    // Dueño y apariencia básica (por ahora sin sync/NBT avanzado)
    private UUID ownerUuid;
    private String skinId;
    private MercenaryRank rank = MercenaryRank.RECRUIT;

    // Lista de skins disponibles, alineada con las carpetas reales de texturas en
    // assets/emerald_warriors/textures/entity/mercenary
    private static final String[] AVAILABLE_SKINS = new String[] {
            "m1", "m2", "m3", "m4", "m5",
            "m6", "m7f", "m8", "m9", "m10f",
            "m11", "m12", "m13", "m14", "m15",
            "m16f", "m17", "m18", "m19", "m20",
            "m21f", "m22", "m23", "m24", "m25",
            "m26", "m27f", "m28", "m29", "m30",
            "m31f", "m32f", "m33", "m34", "m35"
    };

    public EmeraldMercenaryEntity(EntityType<? extends EmeraldMercenaryEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new EmeraldFollowOwnerGoal(this, 1.0D, 5.0F, 2.0F));

        this.meleeAttackGoal = new MeleeAttackGoal(this, 1.2D, true);

        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));

        this.refreshCombatRoleAndGoals();
    }

    public MercenaryRole getCurrentRole() {
        return this.currentRole;
    }

    // === Owner handling sencillo ===

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public LivingEntity getOwner() {
        return this.ownerUuid != null ? this.level().getPlayerByUUID(this.ownerUuid) : null;
    }

    public void setOwner(Player player) {
        this.ownerUuid = player.getUUID();
    }

    private void updateCombatRoleFromEquipment() {
        ItemStack main = this.getMainHandItem();

        boolean hasBow = !main.isEmpty() && main.getItem() instanceof BowItem;
        // En 1.21.11 con mappings oficiales evitamos depender de clases concretas como SwordItem.
        // Consideramos "arma melee" cualquier cosa que no sea arco y no esté vacía.
        boolean hasMeleeWeapon = !main.isEmpty() && !hasBow;

        if (hasBow) {
            this.currentRole = MercenaryRole.ARCHER;
        } else if (hasMeleeWeapon) {
            this.currentRole = MercenaryRole.GUARDIAN;
        } else {
            this.currentRole = MercenaryRole.NONE;
        }
    }

    private void refreshCombatRoleAndGoals() {
        if (this.level().isClientSide()) {
            return;
        }

        this.updateCombatRoleFromEquipment();

        if (this.meleeAttackGoal != null) {
            this.goalSelector.removeGoal(this.meleeAttackGoal);
        }

        // Por ahora, tanto GUARDIAN como ARCHER usan la misma IA melee.
        // Más adelante podremos reintroducir un goal de ataque a distancia adaptado a 1.21.11.
        if (this.currentRole == MercenaryRole.ARCHER || this.currentRole == MercenaryRole.GUARDIAN) {
            this.goalSelector.addGoal(4, this.meleeAttackGoal);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount % 20 == 0) {
            this.refreshCombatRoleAndGoals();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.ownerUuid == null && stack.is(Items.EMERALD)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            if (!this.level().isClientSide()) {
                this.setOwner(player);
                this.getNavigation().stop();
                this.setTarget(null);
                this.level().broadcastEntityEvent(this, (byte)7);
            }

            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    // === Skin / rank para texturas (simplificado) ===

    public String getSkinId() {
        if (this.skinId == null || this.skinId.isEmpty()) {
            // Elegir una skin "aleatoria" pero determinista usando el id de entidad
            int base = Math.abs(this.getId());
            int index = base % AVAILABLE_SKINS.length; // 0..34
            this.skinId = AVAILABLE_SKINS[index];
        }
        return this.skinId;
    }

    public void setSkinId(String skinId) {
        this.skinId = skinId;
    }

    /**
     * Por ahora siempre devuelve "cobre" como rango visual.
     * Más adelante lo ligaremos al sistema real de rangos.
     */
    public String getRankTextureSuffix() {
        return this.rank.getTextureSuffix();
    }
}
