package emeraldwarriors.entity;

import emeraldwarriors.entity.ai.EmeraldBowAttackGoal;
import emeraldwarriors.entity.ai.EmeraldFollowOwnerGoal;
import emeraldwarriors.entity.ai.EmeraldMeleeAttackGoal;
import emeraldwarriors.entity.ai.EmeraldProtectOwnerGoal;
import emeraldwarriors.entity.ai.GuardPositionGoal;
import emeraldwarriors.entity.ai.OwnerHurtTargetGoal;
import emeraldwarriors.entity.ai.PatrolAroundPointGoal;
import emeraldwarriors.entity.ai.RetreatLowHpGoal;
import emeraldwarriors.entity.ai.ShieldAgainstCreeperGoal;
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
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;

import java.util.UUID;

public class EmeraldMercenaryEntity extends PathfinderMob implements RangedAttackMob {

    private static final int TICKS_PER_DAY = 24000;
    private static final int CONTRACT_OFFER_TIMEOUT_TICKS = 200; // ~10 segundos

    private static final EntityDataAccessor<Integer> DATA_RANK_ORDINAL = SynchedEntityData.defineId(EmeraldMercenaryEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORDER_ORDINAL = SynchedEntityData.defineId(EmeraldMercenaryEntity.class, EntityDataSerializers.INT);

    private MercenaryRole currentRole = MercenaryRole.NONE;

    private EmeraldMeleeAttackGoal meleeAttackGoal;
    private EmeraldBowAttackGoal bowAttackGoal;

    private MercenaryRole lastAppliedRole = null;
    private Boolean lastAppliedHasArrows = null;

    // Dueño y apariencia básica
    private UUID ownerUuid;
    private String skinId;
    private MercenaryRank rank = MercenaryRank.RECRUIT;
    private int contractTicksRemaining;
    private int contractEmeraldsPerService;
    private int contractDaysPerPurchase;

    // Sistema de órdenes
    private MercenaryOrder currentOrder = MercenaryOrder.NONE;
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

    // Inventario persistente del mercenario (equipo + mochila)
    private final MercenaryInventory mercenaryInventory = new MercenaryInventory(this);

    // Estado temporal para la "oferta" de contrato (primer click de esmeraldas)
    private UUID pendingContractPlayer;
    private int pendingContractTick;

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
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RANK_ORDINAL, MercenaryRank.RECRUIT.ordinal());
        builder.define(DATA_ORDER_ORDINAL, MercenaryOrder.NONE.ordinal());
    }

    public MercenaryRank getRank() {
        int ordinal = this.getEntityData().get(DATA_RANK_ORDINAL);
        MercenaryRank[] values = MercenaryRank.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return MercenaryRank.RECRUIT;
        }
        return values[ordinal];
    }

    private void setRank(MercenaryRank rank) {
        this.rank = rank;
        this.getEntityData().set(DATA_RANK_ORDINAL, rank.ordinal());
    }

    // === Sistema de órdenes ===

    public MercenaryOrder getCurrentOrder() {
        int ordinal = this.getEntityData().get(DATA_ORDER_ORDINAL);
        MercenaryOrder[] values = MercenaryOrder.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return MercenaryOrder.NONE;
        }
        return values[ordinal];
    }

    public void setCurrentOrder(MercenaryOrder order) {
        this.currentOrder = order;
        this.getEntityData().set(DATA_ORDER_ORDINAL, order.ordinal());

        // Al cambiar de orden, fijar posiciones de guardia/patrulla
        if (order == MercenaryOrder.STAY) {
            this.guardPos = this.blockPosition();
        } else if (order == MercenaryOrder.PATROL) {
            this.patrolCenter = this.blockPosition();
        }
    }

    public BlockPos getGuardPos() {
        return this.guardPos;
    }

    public BlockPos getPatrolCenter() {
        return this.patrolCenter;
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
        this.getContractEmeraldsPerService();
        this.getContractDaysPerPurchase();

        // Aplicar stats del rango y curar a full
        this.applyRankAttributes();
        this.setHealth(this.getMaxHealth());

        // Orden por defecto al spawnear: sin asignación
        this.setCurrentOrder(MercenaryOrder.NONE);

        return data;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)  // Base RECRUIT, se ajusta en applyRankAttributes
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
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

        output.putString("MercenaryRank", this.getRank().name());
        if (this.contractEmeraldsPerService > 0) {
            output.putInt("ContractRate", this.contractEmeraldsPerService);
        }
        if (this.contractDaysPerPurchase > 0) {
            output.putInt("ContractDaysPerPurchase", this.contractDaysPerPurchase);
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

        input.getString("Owner").ifPresent(value -> {
            try {
                this.ownerUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.ownerUuid = null;
            }
        });

        input.getString("SkinId").ifPresent(value -> this.skinId = value);

        this.contractTicksRemaining = input.getIntOr("ContractTicks", this.contractTicksRemaining);

        input.getString("MercenaryRank").ifPresent(value -> {
            try {
                this.setRank(MercenaryRank.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                this.setRank(MercenaryRank.RECRUIT);
            }
        });
        this.contractEmeraldsPerService = input.getIntOr("ContractRate", this.contractEmeraldsPerService);
        this.contractDaysPerPurchase = input.getIntOr("ContractDaysPerPurchase", this.contractDaysPerPurchase);

        this.experience = input.getIntOr("MercenaryXp", this.experience);
        this.maxExperience = input.getIntOr("MercenaryMaxXp", this.maxExperience);

        if (this.ownerUuid == null) {
            this.contractTicksRemaining = 0;
        }

        // Forzar pickup después de cargar NBT (Mob.readAdditionalSaveData lo sobreescribe a false por defecto)
        this.setCanPickUpLoot(true);

        // Orden
        input.getString("MercenaryOrder").ifPresent(value -> {
            try {
                this.currentOrder = MercenaryOrder.valueOf(value);
                this.getEntityData().set(DATA_ORDER_ORDINAL, this.currentOrder.ordinal());
            } catch (IllegalArgumentException ignored) {
                this.setCurrentOrder(MercenaryOrder.NONE);
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

        if (this.contractEmeraldsPerService <= 0
                || this.contractEmeraldsPerService < min
                || this.contractEmeraldsPerService > max) {
            int span = Math.max(1, (max - min) + 1);
            long least = this.getUUID().getLeastSignificantBits();
            int base = (int) (least ^ (least >>> 32));
            if (base == Integer.MIN_VALUE) {
                base = 0;
            }
            base = Math.abs(base);
            this.contractEmeraldsPerService = min + (base % span);
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

    @Override
    protected void registerGoals() {
        // Prioridad 0: Supervivencia básica
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Prioridad 0: Levantar escudo contra creepers a punto de explotar
        this.goalSelector.addGoal(0, new ShieldAgainstCreeperGoal(this, 6.0D));

        // Prioridad 1: Retirarse con poca vida (todos los rangos)
        this.goalSelector.addGoal(1, new RetreatLowHpGoal(this, 1.2D));

        // Prioridad 2: Ataque cuerpo a cuerpo (con animación de swing)
        this.meleeAttackGoal = new EmeraldMeleeAttackGoal(this, 1.1D, true);
        this.bowAttackGoal = new EmeraldBowAttackGoal(this, 0.9D, 20, 15.0F);

        // Prioridad 3: Movimiento según orden
        this.goalSelector.addGoal(3, new EmeraldFollowOwnerGoal(this, 1.0D, 5.0F, 2.0F));
        this.goalSelector.addGoal(3, new GuardPositionGoal(this, 1.0D, 8.0D));

        // Prioridad 4: Patrullar zona (solo en orden PATROL)
        this.goalSelector.addGoal(4, new PatrolAroundPointGoal(this, 0.9D));

        // Prioridad 8-10: Comportamiento idle
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        // Target goals (prioridad de objetivos según diseño)
        // 0: Mob que está pegando al dueño ahora mismo
        this.targetSelector.addGoal(0, new EmeraldProtectOwnerGoal(this));
        // 1: Mob/jugador que el dueño haya golpeado recientemente
        this.targetSelector.addGoal(1, new OwnerHurtTargetGoal(this));
        // 2: Mob que está pegando al mercenario (autodefensa)
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        // 3: Hostil más cercano dentro del radio de detección
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, true));

        if (this.meleeAttackGoal != null) {
            this.goalSelector.addGoal(4, this.meleeAttackGoal);
        }
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

    public void addContractDays(int days) {
        if (days <= 0) {
            return;
        }
        this.contractTicksRemaining += days * TICKS_PER_DAY;
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

        boolean hasBow = !main.isEmpty() && main.getItem() instanceof BowItem;
        // En 1.21.11 con mappings oficiales evitamos depender de clases concretas como SwordItem.
        // Consideramos "arma melee" cualquier cosa que no sea arco y no esté vacía.
        boolean hasMeleeWeapon = !main.isEmpty() && !hasBow;

        if (hasBow) {
            this.currentRole = MercenaryRole.ARCHER;
        } else {
            // Sin arma o con arma melee, trátalo como GUARDIAN para que siempre tenga IA de combate cuerpo a cuerpo.
            this.currentRole = MercenaryRole.GUARDIAN;
        }
    }

    private void refreshCombatRoleAndGoals() {
        if (this.level().isClientSide()) {
            return;
        }

        this.updateCombatRoleFromEquipment();

        boolean isArcher = this.currentRole == MercenaryRole.ARCHER;
        boolean hasArrows = isArcher && this.hasAnyArrowsInBag();

        if (this.currentRole == this.lastAppliedRole && Boolean.valueOf(hasArrows).equals(this.lastAppliedHasArrows)) {
            return;
        }

        if (this.meleeAttackGoal != null) {
            this.goalSelector.removeGoal(this.meleeAttackGoal);
        }
        if (this.bowAttackGoal != null) {
            this.goalSelector.removeGoal(this.bowAttackGoal);
        }

        if (isArcher && hasArrows && this.bowAttackGoal != null) {
            this.goalSelector.addGoal(4, this.bowAttackGoal);
        } else if (this.meleeAttackGoal != null) {
            this.goalSelector.addGoal(4, this.meleeAttackGoal);
        }

        this.lastAppliedRole = this.currentRole;
        this.lastAppliedHasArrows = hasArrows;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack bowStack = this.getMainHandItem();
        if (!(bowStack.getItem() instanceof BowItem)) {
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
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, 14 - serverLevel.getDifficulty().getId() * 4);
        serverLevel.addFreshEntity(arrow);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.ownerUuid == null && this.contractTicksRemaining > 0) {
                this.contractTicksRemaining = 0;
            }
            if (this.contractTicksRemaining > 0 && this.ownerUuid != null) {
                this.contractTicksRemaining--;
                if (this.contractTicksRemaining == 0) {
                    this.onContractExpired();
                }
            }

            // Mantener la atención y detener el movimiento mientras dura la oferta de contrato
            if (this.attentionTicks > 0 && this.attentionPlayer != null) {
                // Forzar parada cada tick para que ningún goal nos mueva
                this.getNavigation().stop();
                Player p = this.level().getPlayerByUUID(this.attentionPlayer);
                if (p != null) {
                    this.getLookControl().setLookAt(p, 30.0F, this.getMaxHeadXRot());
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
                    this.reactiveShieldTicks = 50;
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

            if (this.tickCount % 20 == 0) {
                this.refreshCombatRoleAndGoals();
            }
        }
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
        boolean result = super.doHurtTarget(level, target);

        // Ganar EXP si el objetivo murió
        if (result && target instanceof LivingEntity living && !living.isAlive()) {
            this.addExperience(getExpForKill(living));
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
        this.ownerUuid = null;
        this.getNavigation().stop();
        this.setTarget(null);
    }

    private void sendMercenaryMessage(Player player, String body) {
        if (player instanceof ServerPlayer serverPlayer) {
            Component prefix = Component.literal("[Mercenario] ")
                    .withStyle(ChatFormatting.GREEN);
            Component message = Component.literal("\"" + body + "\"")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            serverPlayer.sendSystemMessage(prefix.copy().append(message));
        }
    }

    private void sendContractInfo(Player player, String body) {
        if (player instanceof ServerPlayer serverPlayer) {
            Component prefix = Component.literal("[Contrato] ")
                    .withStyle(ChatFormatting.GOLD);
            Component message = Component.literal(body)
                    .withStyle(ChatFormatting.GRAY);
            serverPlayer.sendSystemMessage(prefix.copy().append(message));
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
        if (stack.is(Items.EMERALD)) {
            // Si ya está contratado, por ahora NO permitimos renovar/añadir tiempo.
            // En creativo lo dejamos pasar para abrir GUI, pero no para comprar más días.
            if (this.ownerUuid != null) {
                if (!this.level().isClientSide()) {
                    if (this.ownerUuid.equals(player.getUUID()) || isAdmin) {
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
                int rate = this.getContractEmeraldsPerService();
                int daysPerPurchase = this.getContractDaysPerPurchase();
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
                        int offered = stack.getCount();
                        if (offered < rate) {
                            this.sendContractInfo(player, "Faltan esmeraldas.");
                        } else {
                            int toConsume = rate;
                            if (!player.getAbilities().instabuild) {
                                stack.shrink(toConsume);
                            }
                            this.setOwner(player);
                            this.setCurrentOrder(MercenaryOrder.FOLLOW);
                            this.addContractDays(daysPerPurchase);
                            this.getNavigation().stop();
                            this.setTarget(null);
                            this.level().broadcastEntityEvent(this, (byte) 7);
                            this.sendMercenaryMessage(player, this.randomAcceptance());
                            this.sendContractInfo(player,
                                    "+" + daysPerPurchase + " día" + (daysPerPurchase == 1 ? "" : "s") + " de servicio.");
                            this.pendingContractPlayer = null;
                        }
                    }
                }
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
