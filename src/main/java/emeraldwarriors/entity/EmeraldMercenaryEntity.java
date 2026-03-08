package emeraldwarriors.entity;

import emeraldwarriors.entity.ai.EmeraldFollowOwnerGoal;
import emeraldwarriors.entity.ai.EmeraldMeleeAttackGoal;
import emeraldwarriors.entity.ai.EmeraldProtectOwnerGoal;
import emeraldwarriors.inventory.MercenaryInventory;
import emeraldwarriors.inventory.MercenaryMenu;
import emeraldwarriors.mercenary.MercenaryRank;
import emeraldwarriors.mercenary.MercenaryRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public class EmeraldMercenaryEntity extends PathfinderMob {

    private static final int TICKS_PER_DAY = 24000;
    private static final int CONTRACT_OFFER_TIMEOUT_TICKS = 200; // ~10 segundos

    private MercenaryRole currentRole = MercenaryRole.NONE;

    private EmeraldMeleeAttackGoal meleeAttackGoal;

    // Dueño y apariencia básica
    private UUID ownerUuid;
    private String skinId;
    private MercenaryRank rank = MercenaryRank.RECRUIT;
    private int contractTicksRemaining;

    // Progreso de experiencia del mercenario (para futura progresión de rango)
    private int experience;
    private int maxExperience = 100;

    // Inventario persistente del mercenario (equipo + mochila)
    private final MercenaryInventory mercenaryInventory = new MercenaryInventory(this);

    // Estado temporal para la "oferta" de contrato (primer click de esmeraldas)
    private UUID pendingContractPlayer;
    private int pendingContractTick;

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

        output.putInt("MercenaryXp", this.experience);
        output.putInt("MercenaryMaxXp", this.maxExperience);
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

        this.experience = input.getIntOr("MercenaryXp", this.experience);
        this.maxExperience = input.getIntOr("MercenaryMaxXp", this.maxExperience);
    }

    @Override
    protected void registerGoals() {
        // Prioridad 0: Supervivencia
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Prioridad 2: Ataque cuerpo a cuerpo (con animación de swing)
        this.meleeAttackGoal = new EmeraldMeleeAttackGoal(this, 1.2D, true);

        // Prioridad 5: Seguir al dueño
        this.goalSelector.addGoal(5, new EmeraldFollowOwnerGoal(this, 1.0D, 5.0F, 2.0F));

        // Prioridad 8-10: Comportamiento idle
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        // Target goals
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new EmeraldProtectOwnerGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, true));

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
        if (!this.level().isClientSide()) {
            if (this.contractTicksRemaining > 0 && this.ownerUuid != null) {
                this.contractTicksRemaining--;
                if (this.contractTicksRemaining == 0) {
                    this.onContractExpired();
                }
            }
            if (this.tickCount % 20 == 0) {
                this.refreshCombatRoleAndGoals();
            }
        }
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
                    case 4 -> self.rank.ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Los datos solo se sincronizan del servidor al cliente; no aceptamos modificaciones cliente -> servidor.
            }

            @Override
            public int getCount() {
                return 5;
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

        // Shift + clic derecho con esmeraldas → sistema de contrato
        if (isSneaking && stack.is(Items.EMERALD)) {
            // No permitimos que otro jugador compre el mercenario de su dueño actual
            if (this.ownerUuid != null && !this.ownerUuid.equals(player.getUUID())) {
                return super.mobInteract(player, hand);
            }

            if (!this.level().isClientSide()) {
                // Caso 1: sin dueño → flujo de dos pasos
                if (this.ownerUuid == null) {
                    boolean samePlayer = this.pendingContractPlayer != null && this.pendingContractPlayer.equals(player.getUUID());
                    boolean offerValid = samePlayer && (this.tickCount - this.pendingContractTick) <= CONTRACT_OFFER_TIMEOUT_TICKS;

                    if (!offerValid) {
                        // Primer clic: mensaje inmersivo de tarifa
                        this.pendingContractPlayer = player.getUUID();
                        this.pendingContractTick = this.tickCount;
                        this.sendMercenaryMessage(player,
                                "Una esmeralda por cada jornada. Esa es mi tarifa.");
                    } else {
                        // Segundo clic → contratar
                        int days = stack.getCount();
                        if (days <= 0) {
                            this.sendMercenaryMessage(player,
                                    "No veo nada en tu mano que me convenza.");
                        } else {
                            if (!player.getAbilities().instabuild) {
                                stack.shrink(days);
                            }
                            this.setOwner(player);
                            this.addContractDays(days);
                            this.getNavigation().stop();
                            this.setTarget(null);
                            this.level().broadcastEntityEvent(this, (byte) 7);
                            this.sendMercenaryMessage(player,
                                    "Trato hecho. " + days + " jornada" + (days == 1 ? "" : "s") + " a tu servicio.");
                        }
                        this.pendingContractPlayer = null;
                    }
                } else {
                    // Caso 2: ya eres el dueño → extender contrato
                    int days = stack.getCount();
                    if (days > 0) {
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(days);
                        }
                        this.addContractDays(days);
                        this.sendMercenaryMessage(player,
                                days + " jornada" + (days == 1 ? "" : "s") + " más. Seguiré contigo.");
                    }
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
                } else if (this.ownerUuid == null) {
                    this.sendMercenaryMessage(player,
                            "No trabajo sin contrato.");
                    return InteractionResult.CONSUME;
                } else {
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
        return this.rank.getTextureSuffix();
    }

}
