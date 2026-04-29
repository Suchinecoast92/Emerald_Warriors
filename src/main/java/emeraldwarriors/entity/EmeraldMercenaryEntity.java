package emeraldwarriors.entity;

import emeraldwarriors.entity.ai.DefendVillagerGoal;
import emeraldwarriors.entity.ai.EmeraldCrossbowAttackGoal;
import emeraldwarriors.horn.HornGroupManager;
import emeraldwarriors.entity.ai.EmeraldBowAttackGoal;
import emeraldwarriors.entity.ai.EmeraldFollowOwnerGoal;
import emeraldwarriors.entity.ai.EmeraldHurtByTargetGoal;
import emeraldwarriors.entity.ai.EmeraldMeleeAttackGoal;
import emeraldwarriors.entity.ai.EmeraldProtectOwnerGoal;
import emeraldwarriors.entity.ai.GuardPositionGoal;
import emeraldwarriors.entity.ai.ContractRenewWarningGoal;
import emeraldwarriors.entity.ai.MercenaryIdleStrollGoal;
import emeraldwarriors.entity.ai.NeutralWanderGoal;
import emeraldwarriors.entity.ai.OwnerHurtTargetGoal;
import emeraldwarriors.entity.ai.PatrolAroundPointGoal;
import emeraldwarriors.entity.ai.RetreatLowHpGoal;
import emeraldwarriors.entity.ai.ShieldAgainstCreeperGoal;
import emeraldwarriors.entity.ai.UseHealingItemGoal;
import emeraldwarriors.inventory.MercenaryInventory;
import emeraldwarriors.inventory.MercenaryMenu;
import emeraldwarriors.mercenary.MercenaryOrder;
import emeraldwarriors.mercenary.MercenaryRank;
import emeraldwarriors.mercenary.MercenaryRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;

import java.util.UUID;

public class EmeraldMercenaryEntity extends PathfinderMob implements RangedAttackMob, CrossbowAttackMob {

    private static final int TICKS_PER_DAY = 24000;
    private static final int CONTRACT_OFFER_TIMEOUT_TICKS = 200; // ~10 segundos
    private static final int OWNER_DISCIPLINE_WINDOW_TICKS = 600;
    private static final int MAX_STORED_CONTRACT_DAYS = 12;
    private static final int CONTRACT_ADMIRE_TICKS = 60;
    private static final int CONTRACT_EXPIRE_APPROACH_TICKS = 80;
    private static final int CONTRACT_EXPIRE_RETREAT_DELAY_TICKS = 20;
    private static final double CONTRACT_EXPIRE_RETREAT_SPEED = 0.65D;

    private static final int CAMPFIRE_HEAL_INTERVAL_TICKS = 80;
    private static final float CAMPFIRE_HEAL_AMOUNT = 1.0F;
    private static final int CAMPFIRE_HEAL_RADIUS = 4;
    private static final int CAMPFIRE_HEAL_Y_RADIUS = 1;

    private static final EntityDataAccessor<Integer> DATA_RANK_ORDINAL = SynchedEntityData.defineId(EmeraldMercenaryEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORDER_ORDINAL = SynchedEntityData.defineId(EmeraldMercenaryEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(EmeraldMercenaryEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_CONTRACT_ADMIRING = SynchedEntityData.defineId(EmeraldMercenaryEntity.class, EntityDataSerializers.BOOLEAN);

    private MercenaryRole currentRole = MercenaryRole.NONE;

    private EmeraldMeleeAttackGoal meleeAttackGoal;
    private EmeraldBowAttackGoal bowAttackGoal;
    private EmeraldCrossbowAttackGoal crossbowAttackGoal;

    private EmeraldProtectOwnerGoal protectOwnerGoal;
    private OwnerHurtTargetGoal ownerHurtTargetGoal;
    private EmeraldHurtByTargetGoal hurtByTargetGoal;
    private DefendVillagerGoal defendVillagerGoal;
    private NearestAttackableTargetGoal<?> nearestAttackableGoal;

    private MercenaryRole lastAppliedRole = null;
    private Boolean lastAppliedHasArrows = null;
    private boolean lastAppliedIsCrossbow = false;

    // Dueño y apariencia básica
    private UUID ownerUuid;
    private String skinId;
    private MercenaryRank rank = MercenaryRank.RECRUIT;
    private int contractTicksRemaining;
    private int contractEmeraldsPerService;
    private int contractDaysPerPurchase;

    private UUID bundleDiscountPlayerUuid;
    private int bundleDiscountPercent;
    private int bundleDiscountUsesRemaining;
    private UUID currentContractBundlePayerUuid;

    private boolean pendingContractUsedBundle;
    private UUID pendingContractBundlePayerUuid;
    private int pendingContractBundleChangeEmeralds;
    private ItemStack pendingContractAdmireVisualMainHand = ItemStack.EMPTY;

    private net.minecraft.world.phys.Vec3 lastOwnerKnownPos;

    private UUID bannedOwnerUuid;
    private int bannedUntilDay;

    private int ownerDisciplineStrikes;
    private long ownerDisciplineWindowEndGameTime;
    private int ownerDisciplineLookTicks;
    private int ownerDisciplineSlapCountdown;
    private int disciplineAllowOwnerDamageTicks;
    private boolean ownerDisciplineCounterWithWeapon;

    // Sistema de órdenes
    private MercenaryOrder currentOrder = MercenaryOrder.FOLLOW;
    private BlockPos guardPos;
    private BlockPos patrolCenter;

    // Progreso de experiencia del mercenario (para futura progresión de rango)
    private int experience;
    private int maxExperience = 100;

    // Atención temporal al jugador tras la oferta de contrato
    private UUID attentionPlayer;
    private int attentionTicks;

    // Ticks restantes durante los que intentará mantener el escudo arriba
    private int reactiveShieldTicks;

    private DamageSource lastReactiveDamageSource;

    // ANCIENT_GUARD passive regeneration
    private int ticksSinceLastDamage = 0;
    private int regenerationTicks = 0;

    private int campfireHealCooldown = 0;
    
    // Pauses EmeraldFollowOwnerGoal when owner is offline or too far (FOLLOW order only)
    private boolean systemForcedNone = false;

    public boolean isSystemForcedNone() {
        return this.systemForcedNone;
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel sl) {
            UUID myId = this.getUUID();
            for (ServerPlayer sp : sl.getServer().getPlayerList().getPlayers()) {
                Inventory inv = sp.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack st = inv.getItem(i);
                    if (!HornGroupManager.isGoatHorn(st)) {
                        continue;
                    }
                    if (HornGroupManager.isMercenaryLinked(st, myId)) {
                        HornGroupManager.removeMercenary(st, myId);
                    }
                }
            }
        }
        super.die(source);
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    // Multi-target combat system for VETERAN and ANCIENT_GUARD
    private LivingEntity secondaryTarget = null;
    private int targetSwitchCooldown = 0;
    private int primaryTargetTicks = 0;

    // Inventario persistente del mercenario (equipo + mochila)
    private final MercenaryInventory mercenaryInventory = new MercenaryInventory(this);

    // Estado temporal para la "oferta" de contrato (primer click de esmeraldas)
    private UUID pendingContractPlayer;
    private int pendingContractTick;

    private enum PendingContractAction {
        NONE,
        START_CONTRACT,
        RENEW_CONTRACT
    }

    private PendingContractAction pendingContractAction = PendingContractAction.NONE;
    private UUID pendingContractOwnerAfterAdmire;
    private int pendingContractDaysAfterAdmire;
    private int pendingRenewDaysAfterAdmire;
    private UUID contractAdmirePlayerUuid;
    private int contractAdmireTicks;
    private ItemStack contractAdmireSavedMainHand = ItemStack.EMPTY;

    private UUID contractExpireNotifyPlayerUuid;
    private int contractExpireNotifyTicks;
    private int contractExpireRetreatDelayTicks;
    private boolean contractExpireNotified;
    private Vec3 contractExpireAwayFromPos;

    private static final String[] RECRUIT_PROPOSALS = new String[] {
            "Puedo trabajar.",
            "Disponible.",
            "Tarea simple.",
            "Empiezo si quieres.",
            "Poco costo."
    };
    private static final String[] RECRUIT_ACCEPTANCES = new String[] {
            "Recibido.",
            "Inicio ahora.",
            "Haré el intento.",
            "De acuerdo.",
            "Trabajo en curso."
    };

    private static final String[] SOLDIER_PROPOSALS = new String[] {
            "Trabajo fiable.",
            "Tengo práctica.",
            "Costo estándar.",
            "Puedo hacerlo.",
            "Servicio listo."
    };
    private static final String[] SOLDIER_ACCEPTANCES = new String[] {
            "Aceptado.",
            "Me encargo.",
            "En proceso.",
            "Todo claro.",
            "Procediendo."
    };

    private static final String[] SENTINEL_PROPOSALS = new String[] {
            "Vigilancia activa.",
            "Nada se escapa.",
            "Zona segura.",
            "Observo bien.",
            "Control constante."
    };
    private static final String[] SENTINEL_ACCEPTANCES = new String[] {
            "Área cubierta.",
            "Alerta activa.",
            "Sin novedades.",
            "Todo en orden.",
            "Bajo control."
    };

    private static final String[] VETERAN_PROPOSALS = new String[] {
            "Experiencia alta.",
            "Trabajo preciso.",
            "Sin errores.",
            "Hecho antes.",
            "Resultado seguro."
    };
    private static final String[] VETERAN_ACCEPTANCES = new String[] {
            "Confirmado.",
            "Resolviendo.",
            "Avance estable.",
            "Casi listo.",
            "Terminaré pronto."
    };

    private static final String[] ANCIENT_GUARD_PROPOSALS = new String[] {
            "Servicio limitado.",
            "Alta exigencia.",
            "Condiciones claras.",
            "No siempre disponible.",
            "Trabajo exacto."
    };
    private static final String[] ANCIENT_GUARD_ACCEPTANCES = new String[] {
            "En ejecución.",
            "Estado estable.",
            "Proceso completo.",
            "Nada falla.",
            "Finalizado."
    };

    private static final String[] BUNDLE_EASTER_EGG_ACCEPTANCES = new String[] {
            "Aprecio a quien paga como corresponde.",
            "Eso habla bien de ti.",
            "Tienes estilo para estas cosas.",
            "No muchos se toman esa molestia.",
            "Un detalle que no pasa desapercibido.",
            "Consideraré esto en el futuro.",
            "Recordaré este gesto.",
            "Un pago presentable.",
            "No olvidaré este trato."
    };

    private static final float BUNDLE_EASTER_EGG_CHANCE = 0.25F;
    private static final double BUNDLE_BAN_REDUCTION_MULTIPLIER = 0.75D;

    private static final String[] CONTRACT_END_MESSAGES = new String[] {
            "El trato termina aquí.",
            "Mi servicio concluye.",
            "Hasta la próxima.",
            "Ha sido un honor servirte.",
            "Fue buen combate mientras duró.",
            "Quizá nuestros caminos se crucen otra vez.",
            "Nuestro trato ha concluido.",
            "El contrato ha terminado.",
            "Mi deber contigo ha finalizado.",
            "Hemos cumplido lo pactado.",
            "El acuerdo llega a su fin."
    };

    private static final String[] CONTRACT_RENEW_WARN_MESSAGES = new String[] {
            "El trato está por concluir. ¿Deseas renovarlo?",
            "Nuestro contrato está por terminar. ¿Deseas continuar?",
            "Mi servicio está por concluir. ¿Deseas extender el trato?",
            "El acuerdo termina pronto. ¿Renovaremos?",
            "Mi tiempo a tu servicio casi concluye. ¿Continuamos?"
    };

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
        this.setCanPickUpLoot(true);
        this.setPathfindingMalus(PathType.RAIL, 0.0F);
        this.setPathfindingMalus(PathType.UNPASSABLE_RAIL, 0.0F);
    }

    private boolean contractRenewWarned = false;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RANK_ORDINAL, MercenaryRank.RECRUIT.ordinal());
        builder.define(DATA_ORDER_ORDINAL, MercenaryOrder.FOLLOW.ordinal());
        builder.define(DATA_IS_CHARGING_CROSSBOW, false);
        builder.define(DATA_IS_CONTRACT_ADMIRING, false);
    }

    public MercenaryRank getRank() {
        int ordinal = this.getEntityData().get(DATA_RANK_ORDINAL);
        MercenaryRank[] values = MercenaryRank.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return MercenaryRank.RECRUIT;
        }
        return values[ordinal];
    }

    public void setRank(MercenaryRank rank) {
        this.rank = rank;
        this.getEntityData().set(DATA_RANK_ORDINAL, rank.ordinal());
    }

    // === Sistema de órdenes ===

    public MercenaryOrder getCurrentOrder() {
        int ordinal = this.getEntityData().get(DATA_ORDER_ORDINAL);
        MercenaryOrder[] values = MercenaryOrder.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return MercenaryOrder.FOLLOW;
        }
        return values[ordinal];
    }

    /** Called by the horn system to apply an order from a player's goat horn. */
    public void setCurrentOrderFromHorn(MercenaryOrder order, Player commander) {
        if (this.ownerUuid == null || !this.ownerUuid.equals(commander.getUUID())) {
            return;
        }
        this.setCurrentOrder(order);
        // Force immediate AI recalculation
        this.setTarget(null);
        this.getNavigation().stop();
        // Visual acknowledgement
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    this.getX(), this.getY() + 1.0, this.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
        }
    }

    public void setCurrentOrder(MercenaryOrder order) {
        this.currentOrder = order;
        this.getEntityData().set(DATA_ORDER_ORDINAL, order.ordinal());

        // Al cambiar de orden, fijar posiciones de guardia/patrulla
        if (order == MercenaryOrder.GUARD) {
            this.guardPos = this.blockPosition();
        } else if (order == MercenaryOrder.PATROL || order == MercenaryOrder.NEUTRAL) {
            this.patrolCenter = this.blockPosition();
        }
        // Refresh proactive target goal based on new order (server-side only)
        if (!this.level().isClientSide()) {
            this.refreshTargetGoalsByOrder();
        }
    }

    public BlockPos getGuardPos() {
        return this.guardPos;
    }

    public BlockPos getPatrolCenter() {
        return this.patrolCenter;
    }

    public boolean isNeutralOrder() {
        // No neutral order exists in the 3-order system. All orders allow defensive combat.
        // Proactive vs defensive is controlled by refreshTargetGoalsByOrder() (NearestAttackableTargetGoal)
        return this.getCurrentOrder() == MercenaryOrder.NEUTRAL;
    }

    public boolean isOutOfCombatForHeal() {
        return this.getTarget() == null && this.ticksSinceLastDamage >= 200;
    }

    /**
     * Aplica los atributos del rango actual (vida, knockback resistance).
     * Se llama al spawnear y cuando sube de rango.
     */
    public void applyRankAttributes() {
        MercenaryRank r = this.getRank();

        // MaxHealth
        var healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(r.getMaxHealth());
            // Si la vida actual es mayor a la nueva máxima, ajustar
            if (this.getHealth() > this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
            // Si está a full vida, mantenerla a full
            if (this.getHealth() < this.getMaxHealth() && this.getHealth() == (float) this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
        }

        // Knockback resistance
        var kbAttr = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            kbAttr.setBaseValue(r.getKnockbackResistance());
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);

        // Asignación de rango al spawnear (para variar tarifas/texturas).
        // Distribución pensada para que los rangos altos sean raros.
        int roll = this.random.nextInt(100);
        if (roll < 55) {
            this.setRank(MercenaryRank.RECRUIT);
        } else if (roll < 80) {
            this.setRank(MercenaryRank.SOLDIER);
        } else if (roll < 93) {
            this.setRank(MercenaryRank.SENTINEL);
        } else if (roll < 99) {
            this.setRank(MercenaryRank.VETERAN);
        } else {
            this.setRank(MercenaryRank.ANCIENT_GUARD);
        }

        // Reset para que la tarifa se calcule dentro del rango del nuevo rank.
        this.contractEmeraldsPerService = 0;
        this.contractDaysPerPurchase = 0;
        this.getContractDaysPerPurchase();
        this.getContractEmeraldsPerService();

        // Aplicar stats del rango y curar a full
        this.applyRankAttributes();
        this.setHealth(this.getMaxHealth());

        // Orden por defecto: seguir al owner
        this.setCurrentOrder(MercenaryOrder.FOLLOW);

        return data;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)  // Base RECRUIT, se ajusta en applyRankAttributes
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);  // Base, se ajusta por rango
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        if (this.ownerUuid != null) {
            output.putString("Owner", this.ownerUuid.toString());
        }

        if (this.skinId != null && !this.skinId.isEmpty()) {
            output.putString("SkinId", this.skinId);
        }

        if (this.contractTicksRemaining > 0) {
            output.putInt("ContractTicks", this.contractTicksRemaining);
        }

        if (this.contractRenewWarned) {
            output.putInt("ContractRenewWarned", 1);
        }

        output.putString("MercenaryRank", this.getRank().name());
        if (this.contractEmeraldsPerService > 0) {
            output.putInt("ContractRate", this.contractEmeraldsPerService);
        }
        if (this.contractDaysPerPurchase > 0) {
            output.putInt("ContractDaysPerPurchase", this.contractDaysPerPurchase);
        }

        if (this.bannedOwnerUuid != null && this.bannedUntilDay > 0) {
            output.putString("BannedOwner", this.bannedOwnerUuid.toString());
            output.putInt("BannedUntilDay", this.bannedUntilDay);
        }

        if (this.bundleDiscountPlayerUuid != null && this.bundleDiscountPercent > 0 && this.bundleDiscountUsesRemaining > 0) {
            output.putString("BundleDiscountPlayer", this.bundleDiscountPlayerUuid.toString());
            output.putInt("BundleDiscountPercent", this.bundleDiscountPercent);
            output.putInt("BundleDiscountUses", this.bundleDiscountUsesRemaining);
        }

        if (this.currentContractBundlePayerUuid != null) {
            output.putString("CurrentContractBundlePayer", this.currentContractBundlePayerUuid.toString());
        }

        if (this.contractAdmireTicks > 0) {
            output.putInt("ContractAdmireTicks", this.contractAdmireTicks);
        }
        if (this.contractAdmirePlayerUuid != null) {
            output.putString("ContractAdmirePlayer", this.contractAdmirePlayerUuid.toString());
        }
        if (this.pendingContractAction != PendingContractAction.NONE) {
            output.putString("PendingContractAction", this.pendingContractAction.name());
        }
        if (this.pendingContractOwnerAfterAdmire != null) {
            output.putString("PendingContractOwner", this.pendingContractOwnerAfterAdmire.toString());
        }
        if (this.pendingContractDaysAfterAdmire > 0) {
            output.putInt("PendingContractDays", this.pendingContractDaysAfterAdmire);
        }
        if (this.pendingRenewDaysAfterAdmire > 0) {
            output.putInt("PendingRenewDays", this.pendingRenewDaysAfterAdmire);
        }
        if (!this.contractAdmireSavedMainHand.isEmpty()) {
            output.store("ContractAdmireSavedMainHand", ItemStack.CODEC, this.contractAdmireSavedMainHand);
        }

        if (this.pendingContractUsedBundle) {
            output.putInt("PendingContractUsedBundle", 1);
        }
        if (this.pendingContractBundlePayerUuid != null) {
            output.putString("PendingContractBundlePayer", this.pendingContractBundlePayerUuid.toString());
        }
        if (this.pendingContractBundleChangeEmeralds > 0) {
            output.putInt("PendingContractBundleChange", this.pendingContractBundleChangeEmeralds);
        }

        output.putInt("MercenaryXp", this.experience);
        output.putInt("MercenaryMaxXp", this.maxExperience);

        // Orden actual
        output.putString("MercenaryOrder", this.getCurrentOrder().name());
        if (this.guardPos != null) {
            output.putInt("GuardPosX", this.guardPos.getX());
            output.putInt("GuardPosY", this.guardPos.getY());
            output.putInt("GuardPosZ", this.guardPos.getZ());
        }
        if (this.patrolCenter != null) {
            output.putInt("PatrolCenterX", this.patrolCenter.getX());
            output.putInt("PatrolCenterY", this.patrolCenter.getY());
            output.putInt("PatrolCenterZ", this.patrolCenter.getZ());
        }

        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            ItemStack stack = this.mercenaryInventory.getItem(i);
            if (!stack.isEmpty()) {
                output.store("InvSlot" + i, ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        this.pendingContractAction = PendingContractAction.NONE;
        this.pendingContractOwnerAfterAdmire = null;
        this.pendingContractDaysAfterAdmire = 0;
        this.pendingRenewDaysAfterAdmire = 0;
        this.contractAdmirePlayerUuid = null;
        this.contractAdmireTicks = 0;
        this.contractAdmireSavedMainHand = ItemStack.EMPTY;

        this.bannedOwnerUuid = null;
        this.bannedUntilDay = 0;

        this.bundleDiscountPlayerUuid = null;
        this.bundleDiscountPercent = 0;
        this.bundleDiscountUsesRemaining = 0;
        this.currentContractBundlePayerUuid = null;

        this.pendingContractUsedBundle = false;
        this.pendingContractBundlePayerUuid = null;
        this.pendingContractBundleChangeEmeralds = 0;

        input.getString("Owner").ifPresent(value -> {
            try {
                this.ownerUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.ownerUuid = null;
            }
        });

        input.getString("SkinId").ifPresent(value -> this.skinId = value);

        input.getString("BannedOwner").ifPresent(value -> {
            try {
                this.bannedOwnerUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.bannedOwnerUuid = null;
            }
        });
        this.bannedUntilDay = input.getIntOr("BannedUntilDay", this.bannedUntilDay);

        input.getString("BundleDiscountPlayer").ifPresent(value -> {
            try {
                this.bundleDiscountPlayerUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.bundleDiscountPlayerUuid = null;
            }
        });
        this.bundleDiscountPercent = input.getIntOr("BundleDiscountPercent", this.bundleDiscountPercent);
        this.bundleDiscountUsesRemaining = input.getIntOr("BundleDiscountUses", this.bundleDiscountUsesRemaining);

        input.getString("CurrentContractBundlePayer").ifPresent(value -> {
            try {
                this.currentContractBundlePayerUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.currentContractBundlePayerUuid = null;
            }
        });

        this.contractTicksRemaining = input.getIntOr("ContractTicks", this.contractTicksRemaining);
        this.contractRenewWarned = input.getIntOr("ContractRenewWarned", this.contractRenewWarned ? 1 : 0) != 0;

        input.getString("MercenaryRank").ifPresent(value -> {
            try {
                this.setRank(MercenaryRank.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                this.setRank(MercenaryRank.RECRUIT);
            }
        });
        this.contractEmeraldsPerService = input.getIntOr("ContractRate", this.contractEmeraldsPerService);
        this.contractDaysPerPurchase = input.getIntOr("ContractDaysPerPurchase", this.contractDaysPerPurchase);

        this.getContractDaysPerPurchase();
        this.getContractEmeraldsPerService();

        this.experience = input.getIntOr("MercenaryXp", this.experience);
        this.maxExperience = input.getIntOr("MercenaryMaxXp", this.maxExperience);

        if (this.ownerUuid == null) {
            this.contractTicksRemaining = 0;
            this.contractRenewWarned = false;
        }

        this.contractAdmireTicks = input.getIntOr("ContractAdmireTicks", this.contractAdmireTicks);
        input.getString("ContractAdmirePlayer").ifPresent(value -> {
            try {
                this.contractAdmirePlayerUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.contractAdmirePlayerUuid = null;
            }
        });
        input.getString("PendingContractAction").ifPresent(value -> {
            try {
                this.pendingContractAction = PendingContractAction.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                this.pendingContractAction = PendingContractAction.NONE;
            }
        });
        input.getString("PendingContractOwner").ifPresent(value -> {
            try {
                this.pendingContractOwnerAfterAdmire = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.pendingContractOwnerAfterAdmire = null;
            }
        });
        this.pendingContractDaysAfterAdmire = input.getIntOr("PendingContractDays", this.pendingContractDaysAfterAdmire);
        this.pendingRenewDaysAfterAdmire = input.getIntOr("PendingRenewDays", this.pendingRenewDaysAfterAdmire);
        var savedMainOpt = input.read("ContractAdmireSavedMainHand", ItemStack.CODEC);
        if (savedMainOpt.isPresent()) {
            this.contractAdmireSavedMainHand = savedMainOpt.get();
        }

        this.pendingContractUsedBundle = input.getIntOr("PendingContractUsedBundle", 0) != 0;
        input.getString("PendingContractBundlePayer").ifPresent(value -> {
            try {
                this.pendingContractBundlePayerUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.pendingContractBundlePayerUuid = null;
            }
        });
        this.pendingContractBundleChangeEmeralds = input.getIntOr("PendingContractBundleChange", this.pendingContractBundleChangeEmeralds);

        if (this.pendingContractAction != PendingContractAction.NONE && this.contractAdmireTicks <= 0) {
            this.contractAdmireTicks = 1;
        }

        // Forzar pickup después de cargar NBT (Mob.readAdditionalSaveData lo sobreescribe a false por defecto)
        this.setCanPickUpLoot(true);

        // Orden
        input.getString("MercenaryOrder").ifPresent(value -> {
            try {
                MercenaryOrder loaded = MercenaryOrder.valueOf(value);
                this.currentOrder = loaded;
                this.getEntityData().set(DATA_ORDER_ORDINAL, loaded.ordinal());
            } catch (IllegalArgumentException ignored) {
                // Legacy values (NONE, STAY, NONE_STAY, NONE_PATROL) → map to nearest new order
                if (value.contains("STAY")) {
                    this.setCurrentOrder(MercenaryOrder.GUARD);
                } else if (value.contains("PATROL")) {
                    this.setCurrentOrder(MercenaryOrder.PATROL);
                } else {
                    this.setCurrentOrder(MercenaryOrder.FOLLOW);
                }
            }
        });

        // Posiciones de guardia/patrulla
        int gx = input.getIntOr("GuardPosX", Integer.MIN_VALUE);
        int gy = input.getIntOr("GuardPosY", Integer.MIN_VALUE);
        int gz = input.getIntOr("GuardPosZ", Integer.MIN_VALUE);
        if (gx != Integer.MIN_VALUE && gy != Integer.MIN_VALUE && gz != Integer.MIN_VALUE) {
            this.guardPos = new BlockPos(gx, gy, gz);
        }

        int px = input.getIntOr("PatrolCenterX", Integer.MIN_VALUE);
        int py = input.getIntOr("PatrolCenterY", Integer.MIN_VALUE);
        int pz = input.getIntOr("PatrolCenterZ", Integer.MIN_VALUE);
        if (px != Integer.MIN_VALUE && py != Integer.MIN_VALUE && pz != Integer.MIN_VALUE) {
            this.patrolCenter = new BlockPos(px, py, pz);
        }

        // Aplicar stats del rango después de cargar
        this.applyRankAttributes();

        this.mercenaryInventory.setItem(MercenaryInventory.SLOT_MAIN_HAND, this.getMainHandItem());
        this.mercenaryInventory.setItem(MercenaryInventory.SLOT_OFF_HAND, this.getOffhandItem());
        this.mercenaryInventory.setItem(MercenaryInventory.SLOT_HELMET, this.getItemBySlot(EquipmentSlot.HEAD));
        this.mercenaryInventory.setItem(MercenaryInventory.SLOT_CHESTPLATE, this.getItemBySlot(EquipmentSlot.CHEST));
        this.mercenaryInventory.setItem(MercenaryInventory.SLOT_LEGGINGS, this.getItemBySlot(EquipmentSlot.LEGS));
        this.mercenaryInventory.setItem(MercenaryInventory.SLOT_BOOTS, this.getItemBySlot(EquipmentSlot.FEET));

        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            var opt = input.read("InvSlot" + i, ItemStack.CODEC);
            if (opt.isPresent()) {
                this.mercenaryInventory.setItem(i, opt.get());
            }
        }
    }

    private static int minContractRate(MercenaryRank rank) {
        return switch (rank) {
            case RECRUIT -> 1;
            case SOLDIER -> 5;
            case SENTINEL -> 9;
            case VETERAN -> 17;
            case ANCIENT_GUARD -> 25;
        };
    }

    private static int maxContractRate(MercenaryRank rank) {
        return switch (rank) {
            case RECRUIT -> 4;
            case SOLDIER -> 8;
            case SENTINEL -> 16;
            case VETERAN -> 24;
            case ANCIENT_GUARD -> 32;
        };
    }

    private static int minContractDays(MercenaryRank rank) {
        return switch (rank) {
            case RECRUIT -> 1;
            case SOLDIER -> 2;
            case SENTINEL -> 3;
            case VETERAN -> 5;
            case ANCIENT_GUARD -> 7;
        };
    }

    private static int maxContractDays(MercenaryRank rank) {
        return switch (rank) {
            case RECRUIT -> 3;
            case SOLDIER -> 4;
            case SENTINEL -> 6;
            case VETERAN -> 8;
            case ANCIENT_GUARD -> 10;
        };
    }

    private int getContractEmeraldsPerService() {
        MercenaryRank rank = this.getRank();
        int min = minContractRate(rank);
        int max = maxContractRate(rank);

        int days = this.getContractDaysPerPurchase();
        if (days <= 0) {
            days = 1;
        }

        if (this.contractEmeraldsPerService <= 0
                || this.contractEmeraldsPerService < min
                || this.contractEmeraldsPerService > max
                || (days > 0 && (this.contractEmeraldsPerService % days) != 0)) {
            int minPerDay = Math.max(1, (min + days - 1) / days);
            int maxPerDay = Math.max(minPerDay, max / days);
            int span = Math.max(1, (maxPerDay - minPerDay) + 1);
            long least = this.getUUID().getLeastSignificantBits();
            int base = (int) (least ^ (least >>> 32));
            if (base == Integer.MIN_VALUE) {
                base = 0;
            }
            base = Math.abs(base);
            int perDay = minPerDay + (base % span);
            this.contractEmeraldsPerService = perDay * days;
        }

        return this.contractEmeraldsPerService;
    }

    private int getContractDaysPerPurchase() {
        MercenaryRank rank = this.getRank();
        int min = minContractDays(rank);
        int max = maxContractDays(rank);

        if (this.contractDaysPerPurchase <= 0
                || this.contractDaysPerPurchase < min
                || this.contractDaysPerPurchase > max) {
            int span = Math.max(1, (max - min) + 1);
            long most = this.getUUID().getMostSignificantBits();
            int base = (int) (most ^ (most >>> 32));
            if (base == Integer.MIN_VALUE) {
                base = 0;
            }
            base = Math.abs(base);
            this.contractDaysPerPurchase = min + (base % span);
        }

        return this.contractDaysPerPurchase;
    }

    private String randomProposal() {
        String[] options = switch (this.getRank()) {
            case RECRUIT -> RECRUIT_PROPOSALS;
            case SOLDIER -> SOLDIER_PROPOSALS;
            case SENTINEL -> SENTINEL_PROPOSALS;
            case VETERAN -> VETERAN_PROPOSALS;
            case ANCIENT_GUARD -> ANCIENT_GUARD_PROPOSALS;
        };
        return options[this.random.nextInt(options.length)];
    }

    private String randomAcceptance() {
        String[] options = switch (this.getRank()) {
            case RECRUIT -> RECRUIT_ACCEPTANCES;
            case SOLDIER -> SOLDIER_ACCEPTANCES;
            case SENTINEL -> SENTINEL_ACCEPTANCES;
            case VETERAN -> VETERAN_ACCEPTANCES;
            case ANCIENT_GUARD -> ANCIENT_GUARD_ACCEPTANCES;
        };
        return options[this.random.nextInt(options.length)];
    }

    private String randomContractEndMessage() {
        return CONTRACT_END_MESSAGES[this.random.nextInt(CONTRACT_END_MESSAGES.length)];
    }

    public String randomContractRenewWarnMessage() {
        return CONTRACT_RENEW_WARN_MESSAGES[this.random.nextInt(CONTRACT_RENEW_WARN_MESSAGES.length)];
    }

    @Override
    protected void registerGoals() {
        // Prioridad 0: Supervivencia básica
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Prioridad 0: Levantar escudo contra creepers a punto de explotar
        this.goalSelector.addGoal(0, new ShieldAgainstCreeperGoal(this, 6.0D));

        // Prioridad 1: Retirarse con poca vida (todos los rangos)
        this.goalSelector.addGoal(1, new RetreatLowHpGoal(this, 1.2D));

        // Prioridad 1: Usar item de curación cuando tiene poca vida
        this.goalSelector.addGoal(1, new UseHealingItemGoal(this));

        // Prioridad 2: Aviso preventivo de renovación (solo fuera de combate)
        this.goalSelector.addGoal(2, new ContractRenewWarningGoal(this, 1.0D));

        // Prioridad 2: Ataque cuerpo a cuerpo (con animación de swing)
        this.meleeAttackGoal    = new EmeraldMeleeAttackGoal(this, 1.1D, true);
        this.bowAttackGoal      = new EmeraldBowAttackGoal(this, 0.9D, 20, 15.0F);
        this.crossbowAttackGoal = new EmeraldCrossbowAttackGoal(this, 0.9D, 30, 15.0F);

        // Prioridad 3: Movimiento según orden
        this.goalSelector.addGoal(3, new EmeraldFollowOwnerGoal(this, 1.0D, 5.0F, 2.0F));
        this.goalSelector.addGoal(3, new GuardPositionGoal(this, 1.0D, 8.0D));

        // Prioridad 4: Patrullar zona (solo en orden PATROL)
        this.goalSelector.addGoal(4, new PatrolAroundPointGoal(this, 0.9D));

        this.goalSelector.addGoal(4, new NeutralWanderGoal(this, 0.65D));

        // Prioridad 5: Abrir puertas y puertas de valla
        this.goalSelector.addGoal(5, new OpenDoorGoal(this, true));

        // Prioridad 8-10: Comportamiento idle
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new MercenaryIdleStrollGoal(this, 1.0D));

        // Target goals (prioridad de objetivos según diseño)
        // 0: Mob que está pegando al dueño ahora mismo
        this.protectOwnerGoal = new EmeraldProtectOwnerGoal(this);
        // 1: Mob/jugador que el dueño haya golpeado recientemente
        this.ownerHurtTargetGoal = new OwnerHurtTargetGoal(this);
        // 2: Mob que está pegando al mercenario (autodefensa)
        this.hurtByTargetGoal = new EmeraldHurtByTargetGoal(this);
        // 2: Defender aldeanos, mercaderes errantes e iron golems atacados
        this.defendVillagerGoal = new DefendVillagerGoal(this, 32.0);
        // 3: Hostil más cercano (solo en GUARD/PATROL; se añade/quita dinámicamente)
        this.nearestAttackableGoal = new NearestAttackableTargetGoal<>(this, Monster.class, true);
        // Initialized in refreshTargetGoalsByOrder() after goals are registered

        if (this.meleeAttackGoal != null) {
            this.goalSelector.addGoal(2, this.meleeAttackGoal);
        }

        // Initialize proactive target goal based on default order
        this.refreshTargetGoalsByOrder();

        this.refreshCombatRoleAndGoals();
    }

    private void refreshTargetGoalsByOrder() {
        if (this.level().isClientSide()) {
            return;
        }
        if (this.protectOwnerGoal == null
                || this.ownerHurtTargetGoal == null
                || this.hurtByTargetGoal == null
                || this.defendVillagerGoal == null
                || this.nearestAttackableGoal == null) {
            return;
        }

        this.targetSelector.removeGoal(this.nearestAttackableGoal);
        this.targetSelector.removeGoal(this.protectOwnerGoal);
        this.targetSelector.removeGoal(this.ownerHurtTargetGoal);
        this.targetSelector.removeGoal(this.hurtByTargetGoal);
        this.targetSelector.removeGoal(this.defendVillagerGoal);

        MercenaryOrder order = this.getCurrentOrder();
        if (order == MercenaryOrder.GUARD || order == MercenaryOrder.PATROL) {
            this.targetSelector.addGoal(0, this.nearestAttackableGoal);
            this.targetSelector.addGoal(1, this.hurtByTargetGoal);
            this.targetSelector.addGoal(2, this.defendVillagerGoal);
            this.targetSelector.addGoal(3, this.protectOwnerGoal);
            this.targetSelector.addGoal(4, this.ownerHurtTargetGoal);
        } else {
            this.targetSelector.addGoal(0, this.protectOwnerGoal);
            this.targetSelector.addGoal(1, this.ownerHurtTargetGoal);
            this.targetSelector.addGoal(2, this.hurtByTargetGoal);
            this.targetSelector.addGoal(2, this.defendVillagerGoal);
        }
    }

    public MercenaryRole getCurrentRole() {
        return this.currentRole;
    }

    // === Owner handling sencillo ===

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public int getContractTicksRemaining() {
        return this.contractTicksRemaining;
    }

    public Player getContractOwnerPlayer() {
        UUID owner = this.ownerUuid;
        if (owner == null) {
            return null;
        }
        Player p = this.level().getPlayerByUUID(owner);
        return (p != null && p.isAlive()) ? p : null;
    }

    public boolean hasSentContractRenewWarning() {
        return this.contractRenewWarned;
    }

    public void markSentContractRenewWarning() {
        this.contractRenewWarned = true;
    }

    public LivingEntity getOwner() {
        return this.ownerUuid != null ? this.level().getPlayerByUUID(this.ownerUuid) : null;
    }

    public void setOwner(Player player) {
        this.ownerUuid = player.getUUID();
        this.lastOwnerKnownPos = player.position();
    }

    public boolean isContractAdmiring() {
        return this.contractAdmireTicks > 0 || this.pendingContractAction != PendingContractAction.NONE;
    }

    public boolean isContractAdmiringForRender() {
        return this.getEntityData().get(DATA_IS_CONTRACT_ADMIRING);
    }

    public boolean isDisciplineSlapDamageAllowed() {
        return this.disciplineAllowOwnerDamageTicks > 0;
    }

    public void onOwnerMeleeHit(Player owner, boolean ownerUsedWeapon) {
        if (!(this.level() instanceof ServerLevel sl)) {
            return;
        }
        if (this.ownerUuid == null || !this.ownerUuid.equals(owner.getUUID())) {
            return;
        }

        long now = this.level().getGameTime();
        if (now > this.ownerDisciplineWindowEndGameTime) {
            this.ownerDisciplineStrikes = 0;
        }
        this.ownerDisciplineWindowEndGameTime = now + OWNER_DISCIPLINE_WINDOW_TICKS;

        this.ownerDisciplineStrikes = Math.min(3, this.ownerDisciplineStrikes + 1);

        this.setTarget(null);
        this.setAggressive(false);
        this.getNavigation().stop();
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }

        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                this.getX(), this.getY() + 1.0, this.getZ(),
                8, 0.4, 0.5, 0.4, 0.0);

        if (this.ownerDisciplineStrikes == 1) {
            this.ownerDisciplineLookTicks = 10 + this.random.nextInt(11);
            this.ownerDisciplineSlapCountdown = 0;
        } else if (this.ownerDisciplineStrikes == 2) {
            this.ownerDisciplineCounterWithWeapon = ownerUsedWeapon;
            int lookTicks = 20 + this.random.nextInt(11);
            int slapTicks = 60 + this.random.nextInt(21);
            this.ownerDisciplineLookTicks = lookTicks;
            this.ownerDisciplineSlapCountdown = slapTicks;

            ItemStack offhand = this.getOffhandItem();
            ItemStack main = this.getMainHandItem();
            if (offhand.is(Items.SHIELD)) {
                this.startUsingItem(InteractionHand.OFF_HAND);
                this.reactiveShieldTicks = Math.max(this.reactiveShieldTicks, slapTicks);
            } else if (main.is(Items.SHIELD)) {
                this.startUsingItem(InteractionHand.MAIN_HAND);
                this.reactiveShieldTicks = Math.max(this.reactiveShieldTicks, slapTicks);
            }
        } else {
            this.breakContractFromDiscipline(owner);
        }
    }

    private void performOwnerDisciplineSlap() {
        if (!(this.level() instanceof ServerLevel sl)) {
            return;
        }
        if (this.ownerUuid == null) {
            return;
        }
        Player owner = sl.getPlayerByUUID(this.ownerUuid);
        if (owner == null || !owner.isAlive()) {
            return;
        }
        if (this.distanceToSqr(owner) > 4.0D) {
            return;
        }
        if (!this.hasLineOfSight(owner)) {
            return;
        }

        this.swing(InteractionHand.MAIN_HAND);
        this.disciplineAllowOwnerDamageTicks = 2;
        if (this.ownerDisciplineCounterWithWeapon) {
            this.doHurtTarget(sl, owner);
        } else {
            owner.hurtServer(sl, this.damageSources().mobAttack(this), 1.0F);
        }
    }

    private void breakContractFromDiscipline(Player owner) {
        if (!(this.level() instanceof ServerLevel sl)) {
            return;
        }

        this.bannedOwnerUuid = owner.getUUID();
        int currentDay = (int) (this.level().getDayTime() / (long) TICKS_PER_DAY);
        int banDays = banDaysByRank(this.getRank());
        if (this.currentContractBundlePayerUuid != null && this.currentContractBundlePayerUuid.equals(owner.getUUID())) {
            banDays = Math.max(1, (int) Math.ceil(banDays * BUNDLE_BAN_REDUCTION_MULTIPLIER));
        }
        this.bannedUntilDay = currentDay + banDays;

        this.ownerUuid = null;
        this.contractTicksRemaining = 0;
        this.currentContractBundlePayerUuid = null;
        this.pendingContractPlayer = null;
        this.attentionPlayer = null;
        this.attentionTicks = 0;

        this.pendingContractAction = PendingContractAction.NONE;
        this.pendingContractOwnerAfterAdmire = null;
        this.pendingContractDaysAfterAdmire = 0;
        this.pendingRenewDaysAfterAdmire = 0;
        this.contractAdmirePlayerUuid = null;
        this.contractAdmireTicks = 0;
        this.contractAdmireSavedMainHand = ItemStack.EMPTY;

        this.setTarget(null);
        this.setAggressive(false);
        this.getNavigation().stop();
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }

        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                this.getX(), this.getY() + 1.0, this.getZ(),
                12, 0.5, 0.6, 0.5, 0.0);

        this.sendContractInfo(owner, "Disponible nuevamente en " + banDays + " día" + (banDays == 1 ? "" : "s") + ".");
        this.setCurrentOrder(MercenaryOrder.NEUTRAL);

        this.retreatFromExOwner(owner);
    }

    private void retreatFromExOwner(Player owner) {
        this.lastOwnerKnownPos = owner.position();

        double dx = this.getX() - owner.getX();
        double dz = this.getZ() - owner.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001D) {
            dx = (this.random.nextDouble() - 0.5D);
            dz = (this.random.nextDouble() - 0.5D);
            len = Math.sqrt(dx * dx + dz * dz);
        }
        dx /= len;
        dz /= len;

        double[] dists = new double[] { 32.0D, 44.0D, 56.0D, 72.0D };
        for (double dist : dists) {
            for (int attempts = 0; attempts < 16; attempts++) {
                double tx = this.getX() + dx * dist + (this.random.nextDouble() - 0.5D) * 16.0D;
                double tz = this.getZ() + dz * dist + (this.random.nextDouble() - 0.5D) * 16.0D;
                BlockPos target = new BlockPos((int) Math.floor(tx), (int) Math.floor(this.getY()), (int) Math.floor(tz));

                BlockPos ground = target;
                for (int dy = 8; dy >= -12; dy--) {
                    BlockPos check = target.offset(0, dy, 0);
                    if (this.level().getBlockState(check.below()).isSolid()
                            && !this.level().getBlockState(check).isSolid()) {
                        ground = check;
                        break;
                    }
                }

                if (this.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 1.25D)) {
                    if (this.getCurrentOrder() == MercenaryOrder.NEUTRAL) {
                        this.patrolCenter = ground;
                    }
                    return;
                }
            }
        }

        double tx = this.getX() + dx * 32.0D;
        double tz = this.getZ() + dz * 32.0D;
        this.getNavigation().moveTo(tx, this.getY(), tz, 1.25D);
        if (this.getCurrentOrder() == MercenaryOrder.NEUTRAL) {
            this.patrolCenter = new BlockPos((int) Math.floor(tx), (int) Math.floor(this.getY()), (int) Math.floor(tz));
        }
    }

    private static int banDaysByRank(MercenaryRank rank) {
        return switch (rank) {
            case RECRUIT -> 5;
            case SOLDIER -> 6;
            case SENTINEL -> 8;
            case VETERAN -> 10;
            case ANCIENT_GUARD -> 12;
        };
    }

    private static int discountPercentByRank(MercenaryRank rank) {
        return switch (rank) {
            case RECRUIT -> 10;
            case SOLDIER -> 12;
            case SENTINEL -> 14;
            case VETERAN -> 16;
            case ANCIENT_GUARD -> 20;
        };
    }

    private String randomBundleEasterEggAcceptance() {
        return BUNDLE_EASTER_EGG_ACCEPTANCES[this.random.nextInt(BUNDLE_EASTER_EGG_ACCEPTANCES.length)];
    }

    private boolean isBundleDiscountAvailableFor(Player player) {
        return this.bundleDiscountPlayerUuid != null
                && this.bundleDiscountPlayerUuid.equals(player.getUUID())
                && this.bundleDiscountPercent > 0
                && this.bundleDiscountUsesRemaining > 0;
    }

    private int getDiscountedContractRateFor(Player player, int baseRate, int daysPerPurchase) {
        if (!this.isBundleDiscountAvailableFor(player)) {
            return baseRate;
        }
        if (daysPerPurchase <= 0) {
            daysPerPurchase = 1;
        }
        int perDay = Math.max(1, baseRate / daysPerPurchase);
        int discountedPerDay = Math.max(1, (perDay * (100 - this.bundleDiscountPercent)) / 100);
        return Math.max(1, discountedPerDay * daysPerPurchase);
    }

    private void consumeBundleDiscount(Player player) {
        if (!this.isBundleDiscountAvailableFor(player)) {
            return;
        }
        this.bundleDiscountUsesRemaining = Math.max(0, this.bundleDiscountUsesRemaining - 1);
        if (this.bundleDiscountUsesRemaining <= 0) {
            this.bundleDiscountPlayerUuid = null;
            this.bundleDiscountPercent = 0;
        }
    }

    private void grantBundleDiscount(Player player) {
        this.bundleDiscountPlayerUuid = player.getUUID();
        this.bundleDiscountPercent = discountPercentByRank(this.getRank());
        this.bundleDiscountUsesRemaining = 1;
    }

    private static boolean isBundleLikeStack(ItemStack stack) {
        if (stack.is(ItemTags.BUNDLES)) {
            return true;
        }
        return stack.get(DataComponents.BUNDLE_CONTENTS) != null;
    }

    private int countEmeraldsInBundle(ItemStack bundle) {
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) {
            return 0;
        }
        int emeralds = 0;
        for (ItemStack s : contents.items()) {
            if (s.isEmpty()) continue;
            if (!s.is(Items.EMERALD)) {
                return -1;
            }
            emeralds += s.getCount();
        }
        return emeralds;
    }

    private void dropEmeraldChangeTowards(Player player, int emeraldCount) {
        if (!(this.level() instanceof ServerLevel sl)) {
            return;
        }
        if (emeraldCount <= 0) {
            return;
        }

        Vec3 target = player.position().add(0.0D, 0.5D, 0.0D);
        Vec3 from = this.position().add(0.0D, 0.9D, 0.0D);
        Vec3 dir = target.subtract(from);
        Vec3 vel = dir.lengthSqr() > 0.0001D ? dir.normalize().scale(0.35D) : new Vec3(0.0D, 0.0D, 0.0D);

        int remaining = emeraldCount;
        while (remaining > 0) {
            int drop = Math.min(64, remaining);
            ItemEntity item = new ItemEntity(sl, from.x, from.y, from.z, new ItemStack(Items.EMERALD, drop));
            item.setPickUpDelay(10);
            item.setDeltaMovement(vel.x, 0.2D, vel.z);
            sl.addFreshEntity(item);
            remaining -= drop;
        }
    }

    private boolean isBannedFromContract(Player player) {
        if (this.bannedOwnerUuid == null || this.bannedUntilDay <= 0) {
            return false;
        }
        if (!this.bannedOwnerUuid.equals(player.getUUID())) {
            return false;
        }
        int currentDay = (int) (this.level().getDayTime() / (long) TICKS_PER_DAY);
        return currentDay < this.bannedUntilDay;
    }

    public MercenaryInventory getMercenaryInventory() {
        return this.mercenaryInventory;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        if (!this.level().isClientSide()) {
            int invSlot = switch (slot) {
                case MAINHAND -> MercenaryInventory.SLOT_MAIN_HAND;
                case OFFHAND  -> MercenaryInventory.SLOT_OFF_HAND;
                case HEAD     -> MercenaryInventory.SLOT_HELMET;
                case CHEST    -> MercenaryInventory.SLOT_CHESTPLATE;
                case LEGS     -> MercenaryInventory.SLOT_LEGGINGS;
                case FEET     -> MercenaryInventory.SLOT_BOOTS;
                default       -> -1;
            };
            if (invSlot >= 0) {
                this.mercenaryInventory.setItemDirect(invSlot, stack);
            }
        }
    }

    @Override
    public void hurtArmor(DamageSource source, float amount) {
        super.hurtArmor(source, amount);
        if (amount > 0.0F && !this.level().isClientSide()) {
            // Reset ANCIENT_GUARD regeneration timer when taking damage
            if (this.getRank() == MercenaryRank.ANCIENT_GUARD) {
                this.ticksSinceLastDamage = 0;
                this.regenerationTicks = 0;
            }
            
            int armorDmg = Math.max(1, (int) (amount / 4.0F));
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = this.getItemBySlot(slot);
                if (!armor.isEmpty() && armor.isDamageableItem()) {
                    armor.hurtAndBreak(armorDmg, this, slot);
                }
            }
            
        }
    }

    @Override
    public float applyItemBlocking(ServerLevel level, DamageSource source, float amount) {
        float result = super.applyItemBlocking(level, source, amount);
        // Damage the shield proportionally to what was blocked
        float blocked = amount - result;
        if (blocked <= 0f) {
            blocked = result;
        }
        if (blocked > 0f) {
            ItemStack shieldStack = this.getItemBlockingWith();
            if (shieldStack != null && !shieldStack.isEmpty() && shieldStack.isDamageableItem()) {
                InteractionHand hand = this.getUsedItemHand();
                EquipmentSlot slot;
                if (hand == InteractionHand.MAIN_HAND) {
                    slot = EquipmentSlot.MAINHAND;
                } else if (hand == InteractionHand.OFF_HAND) {
                    slot = EquipmentSlot.OFFHAND;
                } else {
                    // Fallback (should be rare): damage the equipped shield
                    slot = this.getOffhandItem().is(Items.SHIELD) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
                }
                shieldStack.hurtAndBreak(Math.max(1, 1 + (int) blocked), this, slot);
            }
        }
        return result;
    }

    @Override
    public void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        // VETERAN attack anticipation - 25% chance to reduce damage with shield
        if (this.getRank() == MercenaryRank.VETERAN && this.random.nextFloat() < 0.25f) {
            ItemStack mainHand = this.getMainHandItem();
            ItemStack offHand = this.getOffhandItem();
            
            // Check if has shield equipped
            if (mainHand.is(Items.SHIELD) || offHand.is(Items.SHIELD)) {
                // Anticipate attack - reduce damage by 20%
                amount *= 0.8f;
                
                // Play shield block sound
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8f, 1.0f);
                
                // Brief shield raise animation (if not already using item)
                if (!this.isUsingItem()) {
                    if (offHand.is(Items.SHIELD)) {
                        this.startUsingItem(InteractionHand.OFF_HAND);
                        // Stop using shield after a brief moment (will be handled by existing reactive shield code)
                        this.reactiveShieldTicks = Math.max(this.reactiveShieldTicks, 10);
                    } else if (mainHand.is(Items.SHIELD)) {
                        this.startUsingItem(InteractionHand.MAIN_HAND);
                        this.reactiveShieldTicks = Math.max(this.reactiveShieldTicks, 10);
                    }
                }
            }
        }

        if (amount > 0.0F) {
            this.ticksSinceLastDamage = 0;
            if (this.getRank() == MercenaryRank.ANCIENT_GUARD) {
                this.regenerationTicks = 0;
            }
        }
        
        super.actuallyHurt(level, source, amount);
    }

    public void addContractDays(int days) {
        if (days <= 0) {
            return;
        }
        this.contractTicksRemaining += days * TICKS_PER_DAY;
        this.contractRenewWarned = false;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos) {
        // Evitar polvo de nieve - es mortal para los mercenarios
        if (this.level().getBlockState(pos).is(Blocks.POWDER_SNOW) ||
            this.level().getBlockState(pos.below()).is(Blocks.POWDER_SNOW) ||
            this.level().getBlockState(pos.above()).is(Blocks.POWDER_SNOW)) {
            return -1.0f; // Valor muy negativo para evitarlo completamente
        }
        
        // Tambien evitar cauldron de polvo de nieve
        if (this.level().getBlockState(pos).is(Blocks.POWDER_SNOW_CAULDRON) ||
            this.level().getBlockState(pos.below()).is(Blocks.POWDER_SNOW_CAULDRON)) {
            return -0.5f;
        }
        
        return super.getWalkTargetValue(pos);
    }
    
    @Override
    public void setTarget(LivingEntity target) {
        if (target != null) {
            if (target instanceof AbstractVillager || target instanceof IronGolem) {
                return;
            }

            if (target instanceof Player p && this.ownerUuid != null && this.ownerUuid.equals(p.getUUID())) {
                return;
            }

            // Friendly fire prevention: never target another mercenary owned by the same player
            if (target instanceof EmeraldMercenaryEntity otherMerc) {
                UUID otherOwner = otherMerc.getOwnerUuid();
                if (otherOwner != null && otherOwner.equals(this.ownerUuid)) {
                    return;
                }
            }

            MercenaryOrder order = this.getCurrentOrder();
            // FOLLOW: NearestAttackableTargetGoal already disabled via refreshTargetGoalsByOrder().
            // Defensive goals (EmeraldProtectOwnerGoal, OwnerHurtTargetGoal, HurtByTargetGoal)
            // must pass freely so mercenary defends owner and itself.
            // During raids, triple the effective range for GUARD and PATROL
            double raidMultiplier = this.isRaidActive() ? 3.0 : 1.0;

            boolean recentAttacker = (target == this.getLastHurtByMob())
                    && (this.tickCount - this.getLastHurtByMobTimestamp() < 100);
            if (recentAttacker) {
                super.setTarget(target);
                return;
            }
            
            if (order == MercenaryOrder.GUARD && this.guardPos != null) {
                // GUARD: only accept targets within guardRadius+4 of the guard post
                double limit = (this.getRank().getGuardRadius() + 4.0) * raidMultiplier;
                if (target.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.guardPos)) > limit * limit) {
                    return;
                }
            } else if (order == MercenaryOrder.PATROL && this.patrolCenter != null) {
                // PATROL: only accept targets within patrolRadius+4 of the patrol center
                double limit = (this.getRank().getPatrolRadius() + 4.0) * raidMultiplier;
                if (target.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.patrolCenter)) > limit * limit) {
                    return;
                }
            }
        }
        super.setTarget(target);
    }

    public int getExperience() {
        return this.experience;
    }

    public int getMaxExperience() {
        return this.maxExperience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public void setMaxExperience(int maxExperience) {
        this.maxExperience = maxExperience;
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(net.minecraft.world.entity.Entity vehicle) {
        // Ajustar el punto de anclaje al vehículo para que el cuerpo quede más bajo
        // (butt apoyado en el asiento del bote/minecart en vez de flotar ligeramente).
        Vec3 base = super.getVehicleAttachmentPoint(vehicle);
        // Subimos el attachment ~0.55 bloques, lo que baja el modelo respecto al asiento.
        return base.add(0.0D, 0.55D, 0.0D);
    }

    // ── CrossbowAttackMob interface ──────────────────────────────────────────

    @Override
    public void setChargingCrossbow(boolean charging) {
        this.getEntityData().set(DATA_IS_CHARGING_CROSSBOW, charging);
    }

    public boolean isChargingCrossbow() {
        return this.getEntityData().get(DATA_IS_CHARGING_CROSSBOW);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        // Called after CrossbowItem.performShooting() completes one shot cycle.
    }

    /** Used by CrossbowItem.tryLoadProjectiles() during stopUsingItem() to consume ammo. */
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        int slot = this.findArrowSlotInBag();
        if (slot != -1) {
            return this.mercenaryInventory.getItem(slot);
        }
        return ItemStack.EMPTY;
    }

    // ── Door navigation ───────────────────────────────────────────────────────

    @Override
    protected PathNavigation createNavigation(Level level) {
        PathNavigation nav = super.createNavigation(level);
        nav.setCanOpenDoors(true);
        return nav;
    }

    private int findArrowSlotInBag() {
        int normalIndex = -1;
        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            ItemStack stack = this.mercenaryInventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof ArrowItem)) {
                continue;
            }
            if (stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW)) {
                return i;
            }
            if (normalIndex == -1) {
                normalIndex = i;
            }
        }
        return normalIndex;
    }

    private boolean hasAnyArrowsInBag() {
        return this.findArrowSlotInBag() != -1;
    }

    private ItemStack removeOneArrowFromBag() {
        int slot = this.findArrowSlotInBag();
        if (slot == -1) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.mercenaryInventory.getItem(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack single = stack.copy();
        single.setCount(1);
        stack.shrink(1);
        if (stack.isEmpty()) {
            this.mercenaryInventory.setItem(slot, ItemStack.EMPTY);
        }
        this.mercenaryInventory.setChanged();
        return single;
    }

    private void updateCombatRoleFromEquipment() {
        ItemStack main = this.getMainHandItem();
        // Support vanilla bows, crossbows, and mod ranged weapons (any ProjectileWeaponItem)
        boolean hasBow = !main.isEmpty() && main.getItem() instanceof ProjectileWeaponItem;
        // Ranged combat is always available regardless of off-hand (shield + bow/crossbow is vanilla)
        if (hasBow) {
            this.currentRole = MercenaryRole.ARCHER;
        } else {
            this.currentRole = MercenaryRole.GUARDIAN;
        }
    }

    private void refreshCombatRoleAndGoals() {
        if (this.level().isClientSide()) {
            return;
        }

        this.updateCombatRoleFromEquipment();

        boolean isArcher   = this.currentRole == MercenaryRole.ARCHER;
        boolean hasArrows  = isArcher && this.hasAnyArrowsInBag();
        boolean isCrossbow = isArcher && this.getMainHandItem().getItem() instanceof CrossbowItem;

        if (this.currentRole == this.lastAppliedRole
                && Boolean.valueOf(hasArrows).equals(this.lastAppliedHasArrows)
                && isCrossbow == this.lastAppliedIsCrossbow) {
            return;
        }

        if (this.meleeAttackGoal != null)    this.goalSelector.removeGoal(this.meleeAttackGoal);
        if (this.bowAttackGoal != null)      this.goalSelector.removeGoal(this.bowAttackGoal);
        if (this.crossbowAttackGoal != null) this.goalSelector.removeGoal(this.crossbowAttackGoal);

        if (isArcher && hasArrows) {
            if (isCrossbow && this.crossbowAttackGoal != null) {
                this.goalSelector.addGoal(2, this.crossbowAttackGoal);
            } else if (this.bowAttackGoal != null) {
                this.goalSelector.addGoal(2, this.bowAttackGoal);
            }
        } else if (this.meleeAttackGoal != null) {
            this.goalSelector.addGoal(2, this.meleeAttackGoal);
        }

        this.lastAppliedRole       = this.currentRole;
        this.lastAppliedHasArrows  = hasArrows;
        this.lastAppliedIsCrossbow = isCrossbow;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (target instanceof AbstractVillager || target instanceof IronGolem) {
            return;
        }

        if (this.isFriendlyInLineOfFire(target)) {
            return;
        }

        ItemStack bowStack = this.getMainHandItem();
        // Support vanilla bows, crossbows, and mod ranged weapons
        if (!(bowStack.getItem() instanceof ProjectileWeaponItem)) {
            return;
        }

        ItemStack arrowStack = this.removeOneArrowFromBag();
        if (arrowStack.isEmpty()) {
            return;
        }

        var arrow = ProjectileUtil.getMobArrow(this, arrowStack, distanceFactor, bowStack);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.333333333333D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        
        // Apply bow enchantment effects (Flame, Power, Punch)
        this.applyBowEnchantments(serverLevel, bowStack, arrow, distanceFactor);
        
        // Rank-based accuracy scaling - SOLDIER+ get improved precision
        float inaccuracy = this.getRangedInaccuracyByRank(serverLevel);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, inaccuracy);
        serverLevel.addFreshEntity(arrow);
        
        // Consume bow durability (1 per shot, vanilla behavior)
        bowStack.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
    }

    public boolean isFriendlyInLineOfFire(LivingEntity target) {
        if (target == null) {
            return false;
        }

        Vec3 start = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        Vec3 end = new Vec3(target.getX(), target.getEyeY(), target.getZ());

        double minX = Math.min(start.x, end.x);
        double minY = Math.min(start.y, end.y);
        double minZ = Math.min(start.z, end.z);
        double maxX = Math.max(start.x, end.x);
        double maxY = Math.max(start.y, end.y);
        double maxZ = Math.max(start.z, end.z);

        AABB search = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.6D);
        double maxDistSqr = start.distanceToSqr(end);

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.isAlive()) {
            Optional<Vec3> hit = owner.getBoundingBox().inflate(0.25D).clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < maxDistSqr) {
                return true;
            }
        }

        UUID ownerId = this.getOwnerUuid();
        if (ownerId == null) {
            return false;
        }

        for (EmeraldMercenaryEntity other : this.level().getEntitiesOfClass(
                EmeraldMercenaryEntity.class,
                search,
                e -> e != this && ownerId.equals(e.getOwnerUuid())
        )) {
            if (!other.isAlive()) {
                continue;
            }
            Optional<Vec3> hit = other.getBoundingBox().inflate(0.25D).clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < maxDistSqr) {
                return true;
            }
        }

        return false;
    }

    private void applyBowEnchantments(ServerLevel level, ItemStack bowStack, AbstractArrow arrow, float distanceFactor) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        // Flame - set arrow on fire (100 ticks = 5 seconds, vanilla behavior)
        registry.get(Enchantments.FLAME).ifPresent(flame -> {
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(flame, bowStack);
            if (lvl > 0) {
                arrow.setRemainingFireTicks(100);
            }
        });

        // Power - extra projectile damage (+50% per level, vanilla formula)
        // baseDamage from getMobArrow = 2.0 * distanceFactor^2
        registry.get(Enchantments.POWER).ifPresent(power -> {
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(power, bowStack);
            if (lvl > 0) {
                double baseDamage = 2.0 * distanceFactor * distanceFactor;
                arrow.setBaseDamage(baseDamage * (1.0 + lvl * 0.5));
            }
        });
    }
    
    private float getRangedInaccuracyByRank(ServerLevel level) {
        // Base inaccuracy calculation (vanilla mob formula)
        float baseInaccuracy = 14 - level.getDifficulty().getId() * 4;

        // Rank-based precision scaling
        return switch (this.getRank()) {
            case RECRUIT -> baseInaccuracy; // 100% base inaccuracy (60% accuracy)
            case SOLDIER -> baseInaccuracy * 0.85f; // 15% better precision (70% accuracy)
            case SENTINEL -> baseInaccuracy * 0.70f; // 30% better precision (80% accuracy)
            case VETERAN -> baseInaccuracy * 0.55f; // 45% better precision (87% accuracy)
            case ANCIENT_GUARD -> baseInaccuracy * 0.40f; // 60% better precision (93% accuracy)
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            boolean shouldAdmireFlag = this.contractAdmireTicks > 0 || this.pendingContractAction != PendingContractAction.NONE;
            if (this.getEntityData().get(DATA_IS_CONTRACT_ADMIRING) != shouldAdmireFlag) {
                this.getEntityData().set(DATA_IS_CONTRACT_ADMIRING, shouldAdmireFlag);
            }

            if (this.pendingContractAction != PendingContractAction.NONE) {
                if (this.getLastHurtByMob() != null && (this.tickCount - this.getLastHurtByMobTimestamp() < 10)) {
                    this.contractAdmireTicks = 0;
                    this.finishContractAdmire();
                }
            }

            if (this.contractAdmireTicks > 0) {
                this.contractAdmireTicks--;
                this.getNavigation().stop();
                this.setTarget(null);
                this.setAggressive(false);
                if (this.isUsingItem()) {
                    this.stopUsingItem();
                }
                if (this.contractAdmirePlayerUuid != null) {
                    Player p = this.level().getPlayerByUUID(this.contractAdmirePlayerUuid);
                    if (p != null) {
                        this.getLookControl().setLookAt(p, 30.0F, this.getMaxHeadXRot());
                    }
                }
                if (this.contractAdmireTicks == 0 && this.pendingContractAction != PendingContractAction.NONE) {
                    this.finishContractAdmire();
                }
            }

            if (this.pendingContractAction != PendingContractAction.NONE && this.contractAdmireTicks <= 0) {
                this.finishContractAdmire();
            }

            if (this.contractExpireNotifyTicks > 0 || this.contractExpireRetreatDelayTicks > 0) {
                this.tickContractExpireNotify();
                return;
            }

            LivingEntity currentTarget = this.getTarget();
            if (currentTarget instanceof AbstractVillager || currentTarget instanceof IronGolem) {
                this.setTarget(null);
                this.getNavigation().stop();
            }

            if (this.disciplineAllowOwnerDamageTicks > 0) {
                this.disciplineAllowOwnerDamageTicks--;
            }

            if (this.ownerDisciplineLookTicks > 0 && this.ownerUuid != null) {
                this.setTarget(null);
                this.setAggressive(false);
                if (this.ownerDisciplineSlapCountdown <= 0) {
                    this.getNavigation().stop();
                }
                Player p = this.level().getPlayerByUUID(this.ownerUuid);
                if (p != null) {
                    this.getLookControl().setLookAt(p, 30.0F, this.getMaxHeadXRot());
                }
                this.ownerDisciplineLookTicks--;
            }

            if (this.ownerDisciplineSlapCountdown > 0 && this.ownerUuid != null) {
                this.setTarget(null);
                this.setAggressive(false);
                Player p = this.level().getPlayerByUUID(this.ownerUuid);
                if (p == null || !p.isAlive()) {
                    this.ownerDisciplineSlapCountdown = 0;
                } else {
                    this.getLookControl().setLookAt(p, 30.0F, this.getMaxHeadXRot());
                    if (this.distanceToSqr(p) > 4.0D) {
                        this.getNavigation().moveTo(p, 1.2D);
                    }

                    if (this.distanceToSqr(p) <= 4.0D && this.hasLineOfSight(p)) {
                        this.ownerDisciplineSlapCountdown = 0;
                        this.ownerDisciplineLookTicks = 0;
                        this.getNavigation().stop();
                        this.performOwnerDisciplineSlap();
                    } else {
                        this.ownerDisciplineSlapCountdown--;
                        if (this.ownerDisciplineSlapCountdown == 0) {
                            if (this.distanceToSqr(p) <= 4.0D && this.hasLineOfSight(p)) {
                                this.ownerDisciplineLookTicks = 0;
                                this.getNavigation().stop();
                                this.performOwnerDisciplineSlap();
                            } else {
                                // Keep trying a little longer to reach the owner before slapping
                                this.ownerDisciplineSlapCountdown = 10;
                            }
                        }
                    }
                }
            }

            if (this.ownerUuid == null && this.contractTicksRemaining > 0) {
                this.contractTicksRemaining = 0;
            }
            if (!this.isContractAdmiring() && this.contractTicksRemaining > 0 && this.ownerUuid != null) {
                this.contractTicksRemaining--;
                if (this.contractTicksRemaining == 0) {
                    this.onContractExpired();
                }
            }

            // --- Owner presence and distance checks (every 20 ticks for performance) ---
            if (this.tickCount % 20 == 0 && this.ownerUuid != null && this.level() instanceof ServerLevel serverLvl) {
                Player owner = this.level().getPlayerByUUID(this.ownerUuid);
                boolean ownerOnlineHere = (owner != null);

                if (owner != null) {
                    this.lastOwnerKnownPos = owner.position();
                }

                // Check if owner is in a different dimension (dimensional follow)
                if (!ownerOnlineHere) {
                    for (ServerLevel otherLevel : serverLvl.getServer().getAllLevels()) {
                        Player ownerElsewhere = otherLevel.getPlayerByUUID(this.ownerUuid);
                        if (otherLevel != serverLvl && ownerElsewhere != null) {
                            if (this.getCurrentOrder() == MercenaryOrder.FOLLOW && !this.systemForcedNone) {
                                this.teleportTo(otherLevel,
                                        ownerElsewhere.getX(), ownerElsewhere.getY(), ownerElsewhere.getZ(),
                                        java.util.Set.of(), ownerElsewhere.getYRot(), 0f, false);
                            }
                            ownerOnlineHere = true;
                            break;
                        }
                    }
                }

                // systemForcedNone only applies to FOLLOW order
                if (this.getCurrentOrder() == MercenaryOrder.FOLLOW) {
                    if (!ownerOnlineHere) {
                        // Owner offline: pause following
                        if (!this.systemForcedNone) {
                            this.systemForcedNone = true;
                            this.setTarget(null);
                            this.getNavigation().stop();
                        }
                    } else if (owner != null) {
                        double maxFollowDist = this.getRank().getMaxChaseFromAnchor() * 3.0;
                        double resumeDist = this.getRank().getMaxChaseFromAnchor() * 1.5;
                        if (!this.systemForcedNone && this.distanceToSqr(owner) > maxFollowDist * maxFollowDist) {
                            // Too far: pause following, stay put
                            this.systemForcedNone = true;
                            this.setTarget(null);
                            this.getNavigation().stop();
                        } else if (this.systemForcedNone && this.distanceToSqr(owner) < resumeDist * resumeDist) {
                            // Close enough again: resume following
                            this.systemForcedNone = false;
                        }
                    } else {
                        // Owner online in another dimension (teleport handled above)
                        this.systemForcedNone = false;
                    }
                } else {
                    // Not FOLLOW: clear flag (GUARD/PATROL manage themselves)
                    this.systemForcedNone = false;
                }
            }

            // Mantener la atención y detener el movimiento mientras dura la oferta de contrato
            if (this.attentionTicks > 0 && this.attentionPlayer != null) {
                // Forzar parada cada tick para que ningún goal nos mueva
                this.getNavigation().stop();
                Player attention = this.level().getPlayerByUUID(this.attentionPlayer);
                if (attention != null) {
                    this.getLookControl().setLookAt(attention, 30.0F, this.getMaxHeadXRot());
                }
                this.attentionTicks--;
                if (this.attentionTicks <= 0) {
                    this.attentionPlayer = null;
                }
            }

            DamageSource lastDamage = this.getLastDamageSource();
            if (lastDamage != null && lastDamage != this.lastReactiveDamageSource) {
                if (this.shouldRaiseShieldFromDamage(lastDamage)) {
                    // Mantener el escudo arriba al menos ~2.5s tras recibir daño
                    this.reactiveShieldTicks = 140;
                }
                this.lastReactiveDamageSource = lastDamage;
            }

            // Uso reactivo del escudo tras recibir daño
            if (this.reactiveShieldTicks > 0) {
                this.reactiveShieldTicks--;

                if (!this.isUsingItem()) {
                    ItemStack offhand = this.getOffhandItem();
                    ItemStack main = this.getMainHandItem();

                    if (offhand.is(Items.SHIELD)) {
                        this.startUsingItem(InteractionHand.OFF_HAND);
                    } else if (main.is(Items.SHIELD)) {
                        this.startUsingItem(InteractionHand.MAIN_HAND);
                    }
                } else if (this.reactiveShieldTicks == 0 && this.getUseItem().is(Items.SHIELD)) {
                    this.stopUsingItem();
                }
            }

            // Track damage timer for all ranks (needed for auto-healing and ANCIENT_GUARD regen)
            this.ticksSinceLastDamage++;

            if (this.campfireHealCooldown > 0) {
                this.campfireHealCooldown--;
            }
            
            // ANCIENT_GUARD passive regeneration
            if (this.getRank() == MercenaryRank.ANCIENT_GUARD) {
                if (this.isOutOfCombatForHeal()) {
                    this.regenerationTicks++;
                    if (this.regenerationTicks >= 100) {
                        this.regenerationTicks = 0;
                        if (this.getHealth() < this.getMaxHealth()) {
                            this.heal(1.0F);
                            if (this.level() instanceof ServerLevel serverLevel) {
                                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                        this.getX(), this.getY() + 1.0, this.getZ(),
                                        3, 0.3, 0.3, 0.3, 0.0);
                            }
                        }
                    }
                }
                
                // Motivation aura for ANCIENT_GUARD - only during active raids
                if (this.isRaidActive() && this.tickCount % 20 == 0) {
                    double radius = 12.0;
                    List<LivingEntity> nearbyAllies = this.level().getEntitiesOfClass(LivingEntity.class,
                                this.getBoundingBox().inflate(radius))
                                .stream()
                                .filter(entity -> entity != this && 
                                        (entity instanceof Player || entity instanceof EmeraldMercenaryEntity))
                                .toList();

                    for (LivingEntity ally : nearbyAllies) {
                        ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, false, true));
                        ally.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, false, true));
                    }
                    
                    // Aura particles
                    if (this.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                this.getX(), this.getY() + 1.0, this.getZ(),
                                8, 2.0, 2.0, 2.0, 0.1);
                    }
                }
            }

            if (this.getCurrentOrder() == MercenaryOrder.NEUTRAL
                    && this.isOutOfCombatForHeal()
                    && this.getHealth() < this.getMaxHealth()
                    && this.isNearLitCampfire()) {
                if (this.campfireHealCooldown <= 0) {
                    this.heal(CAMPFIRE_HEAL_AMOUNT);
                    this.campfireHealCooldown = CAMPFIRE_HEAL_INTERVAL_TICKS;
                }
            }

            // Note: Out-of-combat healing with inventory items is handled by UseHealingItemGoal

            if ((this.getRank() == MercenaryRank.VETERAN || this.getRank() == MercenaryRank.ANCIENT_GUARD)) {
                this.updateMultiTargetCombat();
            }

            if (this.tickCount % 20 == 0) {
                this.refreshCombatRoleAndGoals();
            }
        }
    }

    private boolean isNearLitCampfire() {
        BlockPos base = this.blockPosition();
        int r = CAMPFIRE_HEAL_RADIUS;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -CAMPFIRE_HEAL_Y_RADIUS; dy <= CAMPFIRE_HEAL_Y_RADIUS; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    mut.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    BlockState state = this.level().getBlockState(mut);
                    if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
                            && state.hasProperty(CampfireBlock.LIT)
                            && Boolean.TRUE.equals(state.getValue(CampfireBlock.LIT))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean shouldRaiseShieldFromDamage(DamageSource source) {
        ItemStack main = this.getMainHandItem();
        ItemStack offhand = this.getOffhandItem();

        boolean hasShield = main.is(Items.SHIELD) || offhand.is(Items.SHIELD);
        if (!hasShield) {
            return false;
        }

        net.minecraft.world.entity.Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) {
            return true;
        }

        net.minecraft.world.entity.Entity attacker = source.getEntity();
        if (attacker instanceof Monster) {
            return true;
        }

        if (attacker instanceof Player) {
            return true;
        }

        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.updateSwingTime();

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.tickCount % 5 != 0) {
            return;
        }

        if (!this.isAlive()) {
            return;
        }

        Vec3i reach = this.getPickupReach();
        for (ItemEntity itemEntity : this.level().getEntitiesOfClass(
                ItemEntity.class,
                this.getBoundingBox().inflate(reach.getX(), reach.getY(), reach.getZ())
        )) {
            if (!itemEntity.isAlive() || itemEntity.hasPickUpDelay()) {
                continue;
            }
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (!this.wantsToPickUp(serverLevel, stack)) {
                continue;
            }
            this.pickUpItem(serverLevel, itemEntity);
        }
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return true;
    }

    private ItemStack addToBag(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }

        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            ItemStack existing = this.mercenaryInventory.getItem(i);
            if (existing.isEmpty()) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }
            int space = existing.getMaxStackSize() - existing.getCount();
            if (space <= 0) {
                continue;
            }
            int toMove = Math.min(space, stack.getCount());
            if (toMove > 0) {
                existing.grow(toMove);
                stack.shrink(toMove);
                this.mercenaryInventory.setChanged();
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        for (int i = MercenaryInventory.SLOT_BAG_START; i < MercenaryInventory.SIZE; i++) {
            ItemStack existing = this.mercenaryInventory.getItem(i);
            if (!existing.isEmpty()) {
                continue;
            }
            this.mercenaryInventory.setItem(i, stack);
            this.mercenaryInventory.setChanged();
            return ItemStack.EMPTY;
        }

        return stack;
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem().copy();
        if (stack.isEmpty()) {
            return;
        }

        int before = stack.getCount();
        ItemStack remaining = this.addToBag(stack);

        int picked = before - remaining.getCount();
        if (picked <= 0) {
            return;
        }

        this.take(itemEntity, picked);

        if (remaining.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remaining);
        }
    }

    // === Ganar EXP al matar sin modificar el daño base ===

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        if (target instanceof AbstractVillager || target instanceof IronGolem) {
            return false;
        }

        boolean result = super.doHurtTarget(level, target);

        if (result) {
            // SENTINEL critical hits - 15% chance for extra damage
            if (this.getRank() == MercenaryRank.SENTINEL && target instanceof LivingEntity living && this.random.nextFloat() < 0.15f) {
                // Deal additional 3-5 damage for critical hit (simulates 50% bonus)
                float criticalBonus = 3.0f + this.random.nextFloat() * 2.0f;
                living.hurtServer(level, this.damageSources().mobAttack(this), criticalBonus);
                
                // Critical hit effects
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0f, 1.0f);
                
                // Critical particles at target location
                level.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY(0.5), target.getZ(),
                        8, 0.4, 0.4, 0.4, 0.1);
            }
            
            // Raid damage bonuses for specific ranks and targets
            if (target instanceof LivingEntity living) {
                float raidBonus = this.calculateRaidDamageBonus(living);
                if (raidBonus > 0) {
                    living.hurtServer(level, this.damageSources().mobAttack(this), raidBonus);
                }
            }
            
            // Desgastar el arma principal al golpear
            ItemStack mainHand = this.getMainHandItem();
            if (!mainHand.isEmpty() && mainHand.isDamageableItem()) {
                mainHand.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
            }

            // Ganar EXP si el objetivo murió
            if (target instanceof LivingEntity living && !living.isAlive()) {
                this.addExperience(getExpForKill(living));
            }
        }

        return result;
    }

    private static int getExpForKill(LivingEntity target) {
        if (target instanceof Monster) {
            return 5;
        }
        if (target instanceof Player) {
            return 15;
        }
        // Neutrales, etc.
        return 3;
    }

    // === Sistema de EXP y subida de rango ===

    public void addExperience(int amount) {
        if (amount <= 0) {
            return;
        }
        // No subir de rango si ya es ANCIENT_GUARD
        if (this.getRank() == MercenaryRank.ANCIENT_GUARD) {
            return;
        }

        this.experience += amount;

        while (this.experience >= this.maxExperience && this.getRank() != MercenaryRank.ANCIENT_GUARD) {
            this.experience -= this.maxExperience;
            this.rankUp();
        }

        // Tope de exp si ya es rango máximo
        if (this.getRank() == MercenaryRank.ANCIENT_GUARD) {
            this.experience = Math.min(this.experience, this.maxExperience);
        }
    }
    
    // Cached raid state to avoid expensive checks every tick
    private boolean cachedRaidActive = false;
    private int raidCheckCooldown = 0;
    
    public boolean isRaidActive() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        
        // Only recheck every 2 seconds (40 ticks)
        if (this.raidCheckCooldown > 0) {
            this.raidCheckCooldown--;
            return this.cachedRaidActive;
        }
        this.raidCheckCooldown = 40;
        
        // Primary check: Raids API
        var raids = level.getRaids();
        var raid = raids.getNearbyRaid(this.blockPosition(), 64);
        if (raid != null && raid.isActive()) {
            this.cachedRaidActive = true;
            return true;
        }
        
        // Fallback: Check for nearby Raider entities (in case API doesn't detect)
        boolean hasRaiders = !this.level().getEntitiesOfClass(Raider.class,
                this.getBoundingBox().inflate(48.0), Raider::isAlive).isEmpty();
        
        this.cachedRaidActive = hasRaiders;
        return hasRaiders;
    }
    
    private void updateMultiTargetCombat() {
        // Decrease cooldowns
        if (this.targetSwitchCooldown > 0) {
            this.targetSwitchCooldown--;
        }
        
        LivingEntity currentPrimary = this.getTarget();
        
        // If no primary target, clear secondary and return
        if (currentPrimary == null || !currentPrimary.isAlive()) {
            this.secondaryTarget = null;
            this.primaryTargetTicks = 0;
            return;
        }
        
        // Count time with current primary target
        this.primaryTargetTicks++;
        
        // Look for secondary target if we don't have one or if it's dead/gone
        if (this.secondaryTarget == null || !this.secondaryTarget.isAlive() || 
            this.distanceToSqr(this.secondaryTarget) > 256) { // 16 blocks max
            this.findSecondaryTarget(currentPrimary);
        }
        
        // Switch targets intelligently every 3-4 seconds (60-80 ticks)
        if (this.targetSwitchCooldown <= 0 && this.secondaryTarget != null && 
            this.primaryTargetTicks >= 60 && this.random.nextInt(100) < 30) {
            
            // Switch primary and secondary targets
            LivingEntity temp = currentPrimary;
            this.setTarget(this.secondaryTarget);
            this.secondaryTarget = temp;
            
            // Reset counters
            this.primaryTargetTicks = 0;
            this.targetSwitchCooldown = 80; // 4 second cooldown before next switch
        }
    }
    
    private void findSecondaryTarget(LivingEntity currentPrimary) {
        double maxDistance = 16.0; // Maximum engagement distance
        LivingEntity bestSecondary = null;
        double closestDistance = maxDistance * maxDistance;
        
        // Search for nearby hostile entities
        var nearbyEntities = this.level().getEntitiesOfClass(LivingEntity.class, 
            this.getBoundingBox().inflate(maxDistance), 
            entity -> entity != this && entity != currentPrimary && 
                     entity.isAlive() && this.isValidMultiTarget(entity));
        
        for (LivingEntity entity : nearbyEntities) {
            double distanceSq = this.distanceToSqr(entity);
            
            // Prioritize entities that are attacking us or our owner
            boolean isAttackingUs = false;
            boolean isAttackingOwner = false;
            
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                LivingEntity target = mob.getTarget();
                isAttackingUs = target == this;
                isAttackingOwner = this.ownerUuid != null && target != null && 
                                 target.getUUID().equals(this.ownerUuid);
            }
            
            // Calculate priority score (lower = higher priority)
            double priorityScore = distanceSq;
            if (isAttackingUs) priorityScore *= 0.3; // High priority
            if (isAttackingOwner) priorityScore *= 0.5; // Medium-high priority
            
            if (priorityScore < closestDistance) {
                closestDistance = priorityScore;
                bestSecondary = entity;
            }
        }
        
        this.secondaryTarget = bestSecondary;
    }
    
    private boolean isValidMultiTarget(LivingEntity entity) {
        // Use existing target validation logic
        if (entity instanceof Player player) {
            // Don't attack owner
            if (this.ownerUuid != null && player.getUUID().equals(this.ownerUuid)) {
                return false;
            }
            // Don't attack peaceful players unless they attack us first
            boolean playerTargetingUs = false;
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                playerTargetingUs = mob.getTarget() == this;
            }
            return playerTargetingUs || entity.getLastHurtByMob() == this;
        }
        
        // Attack hostile mobs, illagers, etc.
        return entity instanceof net.minecraft.world.entity.monster.Monster ||
               entity instanceof net.minecraft.world.entity.monster.Enemy ||
               entity.getType().toString().contains("illager") ||
               entity.getType().toString().contains("pillager") ||
               entity.getType().toString().contains("ravager");
    }
    
    private float calculateRaidDamageBonus(LivingEntity target) {
        float baseDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        String targetType = target.getType().toString();
        
        return switch (this.getRank()) {
            case SENTINEL -> {
                // +10% damage vs Illagers (Pillager, Vindicator, Evoker)
                if (targetType.contains("pillager") || targetType.contains("vindicator") || targetType.contains("evoker")) {
                    yield baseDamage * 0.10f;
                }
                yield 0.0f;
            }
            case VETERAN -> {
                // +20% damage vs Ravagers
                if (targetType.contains("ravager")) {
                    yield baseDamage * 0.20f;
                }
                yield 0.0f;
            }
            case ANCIENT_GUARD -> {
                // +20% damage vs all Illagers
                if (targetType.contains("pillager") || targetType.contains("vindicator") || 
                    targetType.contains("evoker") || targetType.contains("ravager")) {
                    yield baseDamage * 0.20f;
                }
                yield 0.0f;
            }
            default -> 0.0f;
        };
    }
    
    private void rankUp() {
        MercenaryRank current = this.getRank();
        MercenaryRank[] ranks = MercenaryRank.values();
        int nextOrdinal = current.ordinal() + 1;

        if (nextOrdinal >= ranks.length) {
            return;
        }

        MercenaryRank newRank = ranks[nextOrdinal];
        this.setRank(newRank);

        // Escalar maxExperience para el siguiente rango
        this.maxExperience = getMaxExpForRank(newRank);

        // Aplicar nuevos stats y curar a full
        this.applyRankAttributes();
        this.setHealth(this.getMaxHealth());

        // Efecto visual de corazones (como al domesticar)
        this.level().broadcastEntityEvent(this, (byte) 7);
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.75f, 1.0f);
        }

        // Notificar al dueño
        LivingEntity owner = this.getOwner();
        if (owner instanceof Player player) {
            String rankName = switch (newRank) {
                case RECRUIT -> "Recluta";
                case SOLDIER -> "Soldado";
                case SENTINEL -> "Centinela";
                case VETERAN -> "Veterano";
                case ANCIENT_GUARD -> "Guardia Ancestral";
            };
            this.sendContractInfo(player, "¡Ascenso a " + rankName + "!");
        }
    }

    private static int getMaxExpForRank(MercenaryRank rank) {
        return switch (rank) {
            case RECRUIT -> 100;
            case SOLDIER -> 200;
            case SENTINEL -> 350;
            case VETERAN -> 550;
            case ANCIENT_GUARD -> 800;
        };
    }

    private void onContractExpired() {
        if (this.contractExpireNotifyTicks > 0 || this.contractExpireRetreatDelayTicks > 0) {
            return;
        }

        UUID exOwner = this.ownerUuid;
        Vec3 awayFrom = this.lastOwnerKnownPos;
        if (exOwner != null) {
            Player owner = this.level().getPlayerByUUID(exOwner);
            if (owner != null) {
                awayFrom = owner.position();
            }
        }

        this.ownerUuid = null;
        this.currentContractBundlePayerUuid = null;
        this.getNavigation().stop();
        this.setTarget(null);
        this.setAggressive(false);
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }

        this.setCurrentOrder(MercenaryOrder.NEUTRAL);

        this.contractExpireNotifyPlayerUuid = exOwner;
        this.contractExpireAwayFromPos = awayFrom;
        this.contractExpireNotifyTicks = CONTRACT_EXPIRE_APPROACH_TICKS;
        this.contractExpireRetreatDelayTicks = 0;
        this.contractExpireNotified = false;
    }

    private void tickContractExpireNotify() {
        if (!(this.level() instanceof ServerLevel)) {
            this.contractExpireNotifyTicks = 0;
            this.contractExpireRetreatDelayTicks = 0;
            return;
        }

        if (this.getLastHurtByMob() != null && (this.tickCount - this.getLastHurtByMobTimestamp()) < 10) {
            this.finishContractExpireNotifyAndRetreat(null);
            return;
        }

        Player p = null;
        if (this.contractExpireNotifyPlayerUuid != null) {
            p = this.level().getPlayerByUUID(this.contractExpireNotifyPlayerUuid);
        }

        this.setTarget(null);
        this.setAggressive(false);
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }

        if (this.contractExpireRetreatDelayTicks > 0) {
            this.contractExpireRetreatDelayTicks--;
            this.getNavigation().stop();
            if (p != null && p.isAlive()) {
                this.getLookControl().setLookAt(p, 30.0F, this.getMaxHeadXRot());
            }
            if (this.contractExpireRetreatDelayTicks <= 0) {
                Vec3 awayFrom = (p != null && p.isAlive()) ? p.position() : this.contractExpireAwayFromPos;
                this.finishContractExpireNotifyAndRetreat(awayFrom);
            }
            return;
        }

        if (this.contractExpireNotifyTicks > 0) {
            this.contractExpireNotifyTicks--;
        }

        if (p != null && p.isAlive()) {
            this.getLookControl().setLookAt(p, 30.0F, this.getMaxHeadXRot());
            if (this.distanceToSqr(p) > 16.0D) {
                this.getNavigation().moveTo(p, 1.0D);
            } else {
                this.getNavigation().stop();
            }
        } else {
            this.getNavigation().stop();
        }

        boolean closeEnoughToNotify = p != null && p.isAlive() && this.distanceToSqr(p) <= 16.0D;
        if (!this.contractExpireNotified && (closeEnoughToNotify || this.contractExpireNotifyTicks <= 0)) {
            if (p == null || !p.isAlive()) {
                this.finishContractExpireNotifyAndRetreat(this.contractExpireAwayFromPos);
                return;
            }

            this.sendMercenaryMessage(p, this.randomContractEndMessage());
            this.contractExpireNotified = true;
            this.contractExpireRetreatDelayTicks = CONTRACT_EXPIRE_RETREAT_DELAY_TICKS;
            this.contractExpireNotifyTicks = 0;
            this.getNavigation().stop();
            return;
        }

        if (this.contractExpireNotifyTicks <= 0 && !this.contractExpireNotified) {
            this.finishContractExpireNotifyAndRetreat(this.contractExpireAwayFromPos);
        }
    }

    private void finishContractExpireNotifyAndRetreat(Vec3 awayFrom) {
        this.contractExpireNotifyTicks = 0;
        this.contractExpireRetreatDelayTicks = 0;
        this.contractExpireNotified = false;
        this.contractExpireNotifyPlayerUuid = null;

        if (awayFrom != null) {
            this.retreatFromExOwnerPosition(awayFrom);
        } else if (this.contractExpireAwayFromPos != null) {
            this.retreatFromExOwnerPosition(this.contractExpireAwayFromPos);
        } else {
            this.retreatFromRandomDirection();
        }
        this.contractExpireAwayFromPos = null;
    }

    private void retreatFromExOwnerPosition(net.minecraft.world.phys.Vec3 awayFrom) {
        double dx = this.getX() - awayFrom.x;
        double dz = this.getZ() - awayFrom.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001D) {
            dx = (this.random.nextDouble() - 0.5D);
            dz = (this.random.nextDouble() - 0.5D);
            len = Math.sqrt(dx * dx + dz * dz);
        }
        dx /= len;
        dz /= len;

        double[] dists = new double[] { 32.0D, 44.0D, 56.0D, 72.0D };
        for (double dist : dists) {
            for (int attempts = 0; attempts < 16; attempts++) {
                double tx = this.getX() + dx * dist + (this.random.nextDouble() - 0.5D) * 16.0D;
                double tz = this.getZ() + dz * dist + (this.random.nextDouble() - 0.5D) * 16.0D;
                BlockPos target = new BlockPos((int) Math.floor(tx), (int) Math.floor(this.getY()), (int) Math.floor(tz));

                BlockPos ground = target;
                for (int dy = 8; dy >= -12; dy--) {
                    BlockPos check = target.offset(0, dy, 0);
                    if (this.level().getBlockState(check.below()).isSolid()
                            && !this.level().getBlockState(check).isSolid()) {
                        ground = check;
                        break;
                    }
                }

                if (this.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, CONTRACT_EXPIRE_RETREAT_SPEED)) {
                    if (this.getCurrentOrder() == MercenaryOrder.NEUTRAL) {
                        this.patrolCenter = ground;
                    }
                    return;
                }
            }
        }

        double tx = this.getX() + dx * 32.0D;
        double tz = this.getZ() + dz * 32.0D;
        this.getNavigation().moveTo(tx, this.getY(), tz, CONTRACT_EXPIRE_RETREAT_SPEED);
        if (this.getCurrentOrder() == MercenaryOrder.NEUTRAL) {
            this.patrolCenter = new BlockPos((int) Math.floor(tx), (int) Math.floor(this.getY()), (int) Math.floor(tz));
        }
    }

    private void retreatFromRandomDirection() {
        double dx = (this.random.nextDouble() - 0.5D);
        double dz = (this.random.nextDouble() - 0.5D);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001D) {
            dx = 1.0D;
            dz = 0.0D;
            len = 1.0D;
        }
        dx /= len;
        dz /= len;
        double tx = this.getX() + dx * 48.0D;
        double tz = this.getZ() + dz * 48.0D;
        this.getNavigation().moveTo(tx, this.getY(), tz, CONTRACT_EXPIRE_RETREAT_SPEED);
        if (this.getCurrentOrder() == MercenaryOrder.NEUTRAL) {
            this.patrolCenter = new BlockPos((int) Math.floor(tx), (int) Math.floor(this.getY()), (int) Math.floor(tz));
        }
    }

    private void startContractAdmire(Player player, PendingContractAction action, int days) {
        if (!(this.level() instanceof ServerLevel sl)) {
            return;
        }
        if (this.isContractAdmiring()) {
            return;
        }

        this.pendingContractAction = action;
        this.contractAdmirePlayerUuid = player.getUUID();
        this.contractAdmireTicks = CONTRACT_ADMIRE_TICKS;

        if (action == PendingContractAction.START_CONTRACT) {
            this.pendingContractOwnerAfterAdmire = player.getUUID();
            this.pendingContractDaysAfterAdmire = days;
        } else if (action == PendingContractAction.RENEW_CONTRACT) {
            this.pendingRenewDaysAfterAdmire = days;
        }

        this.getNavigation().stop();
        this.setTarget(null);
        this.setAggressive(false);
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }

        this.contractAdmireSavedMainHand = this.getMainHandItem().copy();
        ItemStack visual = this.pendingContractAdmireVisualMainHand.isEmpty()
                ? new ItemStack(Items.EMERALD)
                : this.pendingContractAdmireVisualMainHand;
        this.pendingContractAdmireVisualMainHand = ItemStack.EMPTY;
        this.setItemSlot(EquipmentSlot.MAINHAND, visual.copyWithCount(1));
        this.attentionPlayer = player.getUUID();
        this.attentionTicks = Math.max(this.attentionTicks, CONTRACT_ADMIRE_TICKS);

        sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                this.getX(), this.getY() + 1.0, this.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
    }

    private void finishContractAdmire() {
        this.setItemSlot(EquipmentSlot.MAINHAND, this.contractAdmireSavedMainHand);
        this.pendingContractAdmireVisualMainHand = ItemStack.EMPTY;

        PendingContractAction action = this.pendingContractAction;
        UUID pendingOwner = this.pendingContractOwnerAfterAdmire;
        int pendingDays = this.pendingContractDaysAfterAdmire;
        int renewDays = this.pendingRenewDaysAfterAdmire;
        boolean usedBundle = this.pendingContractUsedBundle;
        UUID usedBundlePayer = this.pendingContractBundlePayerUuid;
        int usedBundleChange = this.pendingContractBundleChangeEmeralds;

        this.pendingContractAction = PendingContractAction.NONE;
        this.pendingContractOwnerAfterAdmire = null;
        this.pendingContractDaysAfterAdmire = 0;
        this.pendingRenewDaysAfterAdmire = 0;
        this.contractAdmirePlayerUuid = null;
        this.contractAdmireSavedMainHand = ItemStack.EMPTY;
        this.pendingContractUsedBundle = false;
        this.pendingContractBundlePayerUuid = null;
        this.pendingContractBundleChangeEmeralds = 0;

        if (!(this.level() instanceof ServerLevel sl)) {
            return;
        }

        if (usedBundle && usedBundlePayer != null && usedBundleChange > 0) {
            Player payer = sl.getPlayerByUUID(usedBundlePayer);
            if (payer != null) {
                this.dropEmeraldChangeTowards(payer, usedBundleChange);
            }
        }

        if (action == PendingContractAction.START_CONTRACT && pendingOwner != null && pendingDays > 0) {
            this.ownerUuid = pendingOwner;
            if (usedBundle && usedBundlePayer != null) {
                this.currentContractBundlePayerUuid = usedBundlePayer;
                Player payer = sl.getPlayerByUUID(usedBundlePayer);
                if (payer != null) {
                    this.grantBundleDiscount(payer);
                }
            }
            this.setCurrentOrder(MercenaryOrder.FOLLOW);
            this.addContractDays(pendingDays);
            this.getNavigation().stop();
            this.setTarget(null);
            this.level().broadcastEntityEvent(this, (byte) 7);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY(0.5), this.getZ(),
                    8, 0.4, 0.5, 0.4, 0.0);
            Player p = sl.getPlayerByUUID(pendingOwner);
            if (p != null) {
                String accept = (usedBundle && this.random.nextFloat() < BUNDLE_EASTER_EGG_CHANCE)
                        ? this.randomBundleEasterEggAcceptance()
                        : this.randomAcceptance();
                this.sendMercenaryMessage(p, accept);
                this.sendContractInfo(p, "Contrato iniciado (" + pendingDays + " día" + (pendingDays == 1 ? "" : "s") + ")");
            }
        } else if (action == PendingContractAction.RENEW_CONTRACT && renewDays > 0) {
            this.addContractDays(renewDays);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY(0.5), this.getZ(),
                    6, 0.35, 0.45, 0.35, 0.0);
            if (this.ownerUuid != null) {
                Player p = sl.getPlayerByUUID(this.ownerUuid);
                if (p != null) {
                    if (usedBundle && this.random.nextFloat() < BUNDLE_EASTER_EGG_CHANCE) {
                        this.sendMercenaryMessage(p, this.randomBundleEasterEggAcceptance());
                    }
                    if (usedBundle) {
                        this.currentContractBundlePayerUuid = p.getUUID();
                        this.grantBundleDiscount(p);
                    }
                    this.sendContractInfo(p, "Contrato extendido (+" + renewDays + " día" + (renewDays == 1 ? "" : "s") + ")");
                }
            }
        }
    }

    public void sendMercenaryMessage(Player player, String body) {
        if (player instanceof ServerPlayer serverPlayer) {
            Component prefix = Component.literal("[Mercenario] ")
                    .withStyle(ChatFormatting.GREEN);
            Component message = Component.literal("\"" + body + "\"")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            serverPlayer.sendSystemMessage(prefix.copy().append(message));
        }
    }

    public void sendContractInfo(Player player, String body) {
        if (player instanceof ServerPlayer serverPlayer) {
            Component message = Component.literal(body)
                    .withStyle(ChatFormatting.GOLD);
            serverPlayer.displayClientMessage(message, true);
        }
    }

    private void sendOrderOverlay(Player player, MercenaryOrder order) {
        if (player instanceof ServerPlayer serverPlayer) {
            Component message = Component.literal("Orden: " + order.getDisplayName())
                    .withStyle(ChatFormatting.WHITE);
            // true = mostrar en la barra de acción (sobre la hotbar), no en el chat
            serverPlayer.displayClientMessage(message, true);
        }
    }

    /**
     * Abre el inventario del mercenario para el jugador dado (solo server-side).
     * Requiere que el jugador sea el dueño actual.
     */
    private void openInventoryFor(ServerPlayer serverPlayer) {
        MercenaryInventory mercenaryInventory = this.mercenaryInventory;
        EmeraldMercenaryEntity self = this;

        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> (int) Math.ceil(self.getHealth());
                    case 1 -> (int) Math.ceil(self.getMaxHealth());
                    case 2 -> self.getExperience();
                    case 3 -> self.getMaxExperience();
                    case 4 -> self.getRank().ordinal();
                    case 5 -> self.getId();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Los datos solo se sincronizan del servidor al cliente; no aceptamos modificaciones cliente -> servidor.
            }

            @Override
            public int getCount() {
                return 6;
            }
        };

        serverPlayer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return EmeraldMercenaryEntity.this.getName();
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
                return new MercenaryMenu(syncId, playerInventory, mercenaryInventory, data);
            }
        });
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean isSneaking = player.isShiftKeyDown();
        boolean isAdmin = player.getAbilities().instabuild;

        // Clic derecho con esmeraldas → sistema de contrato
        boolean usingBundle = isBundleLikeStack(stack);
        if (stack.is(Items.EMERALD) || usingBundle) {
            if (!this.level().isClientSide() && this.isContractAdmiring()) {
                this.sendContractInfo(player, "Estoy ocupado.");
                return InteractionResult.CONSUME;
            }

            // Si ya está contratado, por ahora NO permitimos renovar/añadir tiempo.
            // En creativo lo dejamos pasar para abrir GUI, pero no para comprar más días.
            if (this.ownerUuid != null) {
                boolean canManage = this.ownerUuid.equals(player.getUUID()) || isAdmin;
                if (isSneaking && hand == InteractionHand.MAIN_HAND && canManage && !this.level().isClientSide()) {
                    int baseRate = this.getContractEmeraldsPerService();
                    int daysPerPurchase = this.getContractDaysPerPurchase();
                    if (daysPerPurchase <= 0) {
                        daysPerPurchase = 1;
                    }
                    if (baseRate <= 0 || (baseRate % daysPerPurchase) != 0) {
                        this.sendContractInfo(player, "No puedo renovar ahora.");
                        return InteractionResult.CONSUME;
                    }

                    boolean discountAvailable = this.isBundleDiscountAvailableFor(player);
                    int rate = this.getDiscountedContractRateFor(player, baseRate, daysPerPurchase);

                    int emeraldsPerDay = rate / daysPerPurchase;
                    int offered;
                    if (usingBundle) {
                        offered = this.countEmeraldsInBundle(stack);
                        if (offered < 0) {
                            this.sendContractInfo(player, "El saco debe contener solo esmeraldas.");
                            return InteractionResult.CONSUME;
                        }
                    } else {
                        offered = stack.getCount();
                    }
                    int toConsider = Math.min(offered, rate);

                    if (toConsider < emeraldsPerDay || (toConsider % emeraldsPerDay) != 0) {
                        this.sendContractInfo(player, "Solo acepto múltiplos de " + emeraldsPerDay + " esmeralda" + (emeraldsPerDay == 1 ? "" : "s") + ".");
                        return InteractionResult.CONSUME;
                    }

                    int maxTicks = MAX_STORED_CONTRACT_DAYS * TICKS_PER_DAY;
                    int remainingTicksAllowed = Math.max(0, maxTicks - this.contractTicksRemaining);
                    int maxAddDaysByCap = remainingTicksAllowed / TICKS_PER_DAY;
                    int daysToAdd = Math.min(toConsider / emeraldsPerDay, maxAddDaysByCap);

                    if (daysToAdd <= 0) {
                        this.sendContractInfo(player, "Contrato al máximo.");
                        return InteractionResult.CONSUME;
                    }

                    int emeraldsToConsume = daysToAdd * emeraldsPerDay;
                    ItemStack visualPayment = usingBundle ? stack.copyWithCount(1) : new ItemStack(Items.EMERALD);
                    if (!player.getAbilities().instabuild) {
                        if (usingBundle) {
                            stack.shrink(1);
                        } else {
                            stack.shrink(emeraldsToConsume);
                        }
                    }
                    if (discountAvailable) {
                        this.consumeBundleDiscount(player);
                    }
                    this.pendingContractUsedBundle = usingBundle;
                    this.pendingContractBundlePayerUuid = usingBundle ? player.getUUID() : null;
                    this.pendingContractBundleChangeEmeralds = usingBundle ? Math.max(0, offered - emeraldsToConsume) : 0;
                    this.pendingContractAdmireVisualMainHand = visualPayment;
                    this.startContractAdmire(player, PendingContractAction.RENEW_CONTRACT, daysToAdd);
                    return InteractionResult.CONSUME;
                }

                if (!this.level().isClientSide()) {
                    if (canManage) {
                        if (player instanceof ServerPlayer serverPlayer) {
                            this.openInventoryFor(serverPlayer);
                        }
                    } else {
                        this.sendContractInfo(player, "Ya tengo contrato.");
                    }
                }
                return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
            }

            // No permitimos que otro jugador compre el mercenario de su dueño actual
            if (!isAdmin && this.ownerUuid != null && !this.ownerUuid.equals(player.getUUID())) {
                if (!this.level().isClientSide()) {
                    this.sendContractInfo(player, "Ya tengo contrato.");
                }
                return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
            }

            if (!this.level().isClientSide()) {
                if (this.ownerUuid == null && !isAdmin && this.isBannedFromContract(player)) {
                    int currentDay = (int) (this.level().getDayTime() / (long) TICKS_PER_DAY);
                    int remaining = Math.max(0, this.bannedUntilDay - currentDay);
                    this.sendContractInfo(player, "Disponible nuevamente en " + remaining + " día" + (remaining == 1 ? "" : "s") + ".");
                    return InteractionResult.CONSUME;
                }
                int baseRate = this.getContractEmeraldsPerService();
                int daysPerPurchase = this.getContractDaysPerPurchase();
                int rate = this.getDiscountedContractRateFor(player, baseRate, daysPerPurchase);
                // Caso 1: sin dueño → flujo de dos pasos
                if (this.ownerUuid == null) {
                    boolean samePlayer = this.pendingContractPlayer != null && this.pendingContractPlayer.equals(player.getUUID());
                    boolean offerValid = samePlayer && (this.tickCount - this.pendingContractTick) <= CONTRACT_OFFER_TIMEOUT_TICKS;

                    if (!offerValid) {
                        // Primer clic: mensaje inmersivo de tarifa
                        this.pendingContractPlayer = player.getUUID();
                        this.pendingContractTick = this.tickCount;

                        // Prestar atención al jugador que hace la oferta durante unos segundos
                        this.getNavigation().stop();
                        this.setTarget(null);
                        this.attentionPlayer = player.getUUID();
                        this.attentionTicks = 100; // ~5 segundos a 20 tps

                        this.sendMercenaryMessage(player, this.randomProposal());
                        this.sendContractInfo(player,
                                "Tarifa: " + rate + " esmeralda" + (rate == 1 ? "" : "s")
                                        + " por " + daysPerPurchase + " día" + (daysPerPurchase == 1 ? "" : "s") + " de servicio.");
                    } else {
                        // Segundo clic → contratar
                        boolean discountAvailable = this.isBundleDiscountAvailableFor(player);
                        int offered;
                        if (usingBundle) {
                            offered = this.countEmeraldsInBundle(stack);
                            if (offered < 0) {
                                this.sendContractInfo(player, "El saco debe contener solo esmeraldas.");
                                return InteractionResult.CONSUME;
                            }
                        } else {
                            offered = stack.getCount();
                        }
                        if (offered < rate) {
                            this.sendContractInfo(player, "Faltan esmeraldas.");
                        } else {
                            ItemStack visualPayment = usingBundle ? stack.copyWithCount(1) : new ItemStack(Items.EMERALD);
                            if (!player.getAbilities().instabuild) {
                                if (usingBundle) {
                                    stack.shrink(1);
                                } else {
                                    stack.shrink(rate);
                                }
                            }
                            if (discountAvailable) {
                                this.consumeBundleDiscount(player);
                            }
                            this.pendingContractPlayer = null;
                            this.pendingContractUsedBundle = usingBundle;
                            this.pendingContractBundlePayerUuid = usingBundle ? player.getUUID() : null;
                            this.pendingContractBundleChangeEmeralds = usingBundle ? Math.max(0, offered - rate) : 0;
                            this.pendingContractAdmireVisualMainHand = visualPayment;
                            this.startContractAdmire(player, PendingContractAction.START_CONTRACT, daysPerPurchase);
                        }
                    }
                }
            }
            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }

        // Shift + clic derecho con cuerno → vincular/desvincular al grupo del cuerno
        if (isSneaking && hand == InteractionHand.MAIN_HAND && HornGroupManager.isGoatHorn(stack)) {
            if (this.ownerUuid != null && this.ownerUuid.equals(player.getUUID())) {
                if (!this.level().isClientSide()) {
                    boolean nowLinked = HornGroupManager.toggleMercenary(stack, this.getUUID());
                    if (this.level() instanceof ServerLevel sl) {
                        if (nowLinked) {
                            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                    this.getX(), this.getY() + 1.0, this.getZ(), 10, 0.4, 0.5, 0.4, 0.1);
                        } else {
                            sl.sendParticles(ParticleTypes.SMOKE,
                                    this.getX(), this.getY() + 1.0, this.getZ(), 8, 0.4, 0.5, 0.4, 0.05);
                        }
                    }
                    int count = HornGroupManager.getLinkedCount(stack);
                    String msg = nowLinked
                            ? "§a[♪] Vinculado al cuerno. (" + count + " total)"
                            : "§c[♪] Desvinculado del cuerno. (" + count + " restantes)";
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), false);
                }
            } else if (!this.level().isClientSide()) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§cEste mercenario no es tuyo."), false);
            }
            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }

        // Shift + clic derecho → ciclar orden (solo dueño o admin)
        // Solo procesar MAIN_HAND para evitar doble ejecución
        if (isSneaking && hand == InteractionHand.MAIN_HAND && this.ownerUuid != null) {
            boolean isOwner = this.ownerUuid.equals(player.getUUID());
            if ((isOwner || isAdmin) && !this.level().isClientSide()) {
                MercenaryOrder next = this.getCurrentOrder().next();
                this.setCurrentOrder(next);
                this.getNavigation().stop();
                this.setTarget(null);
                this.sendOrderOverlay(player, next);
                if (this.level() instanceof ServerLevel sl) {
                    sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.4f, 1.2f);
                }
            }
            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }

        // Clic derecho sin shift → abrir inventario del mercenario (solo si es el dueño)
        if (!isSneaking) {
            if (!this.level().isClientSide()) {
                if (this.ownerUuid != null && this.ownerUuid.equals(player.getUUID())) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        this.openInventoryFor(serverPlayer);
                    }
                    return InteractionResult.CONSUME;
                } else if (isAdmin && this.ownerUuid != null) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        this.openInventoryFor(serverPlayer);
                    }
                    return InteractionResult.CONSUME;
                } else if (this.ownerUuid == null) {
                    this.sendMercenaryMessage(player,
                            "No trabajo sin contrato.");
                    return InteractionResult.CONSUME;
                } else {
                    this.sendContractInfo(player, "Ya tengo contrato.");
                    return InteractionResult.CONSUME;
                }
            }
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    // === Skin / rank para texturas (simplificado) ===

    public String getSkinId() {
        if (this.skinId == null || this.skinId.isEmpty()) {
            // Elegir una skin "aleatoria" pero determinista usando el UUID persistente
            long least = this.getUUID().getLeastSignificantBits();
            int base = (int) (least & 0x7FFFFFFFL); // valor no negativo
            int index = base % AVAILABLE_SKINS.length; // 0..(n-1)
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
        return this.getRank().getTextureSuffix();
    }

}
