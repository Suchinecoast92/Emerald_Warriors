package emeraldwarriors.inventory;

import emeraldwarriors.menu.ModMenus;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Menú del inventario del mercenario — estilo inventario vanilla.
 * Layout (12 slots propios + 36 del jugador):
 *
 *  Columna izquierda (x=8):  armadura de arriba a abajo
 *    Slot 2 = Casco        (8, 18)
 *    Slot 3 = Peto         (8, 36)
 *    Slot 4 = Pantalones   (8, 54)
 *    Slot 5 = Botas        (8, 72)
 *
 *  Columna armas (x=30):
 *    Slot 0 = Mano derecha (30, 18)
 *    Slot 1 = Mano izq.    (30, 72)
 *
 *  Mochila: 6 slots en fila horizontal (x=62..152, y=72)
 *    Slots 6-11
 *
 *  Zona inventario jugador: y=117 (3 filas) + y=175 (hotbar)
 */
public class MercenaryMenu extends AbstractContainerMenu {

    private final MercenaryInventory mercenaryInventory;
    private final ContainerData data;

    // Layout: equipamiento arriba, inventario jugador abajo
    private static final int ARMOR_X     = 8;
    private static final int WEAPON_X    = 30;
    private static final int BAG_X       = 62;
    private static final int EQUIP_TOP_Y = 18;

    private static final int PLAYER_INV_X    = 8;
    private static final int PLAYER_INV_Y    = 117;
    private static final int PLAYER_HOTBAR_Y = 175;

    public MercenaryMenu(int syncId, Inventory playerInventory, MercenaryInventory mercenaryInventory) {
        this(syncId, playerInventory, mercenaryInventory, new SimpleContainerData(5));
    }

    public MercenaryMenu(int syncId, Inventory playerInventory, MercenaryInventory mercenaryInventory, ContainerData data) {
        super(ModMenus.MERCENARY_MENU, syncId);
        this.mercenaryInventory = mercenaryInventory;
        this.data = data;

        mercenaryInventory.startOpen(playerInventory.player);

        this.addDataSlots(data);

        // --- Armadura (columna izquierda, 4 slots verticales) ---
        this.addSlot(iconSlot(mercenaryInventory, MercenaryInventory.SLOT_HELMET,     ARMOR_X, EQUIP_TOP_Y,      "container/slot/helmet"));
        this.addSlot(iconSlot(mercenaryInventory, MercenaryInventory.SLOT_CHESTPLATE, ARMOR_X, EQUIP_TOP_Y + 18, "container/slot/chestplate"));
        this.addSlot(iconSlot(mercenaryInventory, MercenaryInventory.SLOT_LEGGINGS,   ARMOR_X, EQUIP_TOP_Y + 36, "container/slot/leggings"));
        this.addSlot(iconSlot(mercenaryInventory, MercenaryInventory.SLOT_BOOTS,      ARMOR_X, EQUIP_TOP_Y + 54, "container/slot/boots"));

        // --- Armas (segunda columna) ---
        this.addSlot(iconSlot(mercenaryInventory, MercenaryInventory.SLOT_MAIN_HAND, WEAPON_X, EQUIP_TOP_Y,      "container/slot/sword"));
        this.addSlot(iconSlot(mercenaryInventory, MercenaryInventory.SLOT_OFF_HAND,  WEAPON_X, EQUIP_TOP_Y + 54, "container/slot/shield"));

        // --- Mochila del mercenario: 6 slots en fila horizontal ---
        for (int col = 0; col < 6; col++) {
            int slotIndex = MercenaryInventory.SLOT_BAG_START + col;
            this.addSlot(new Slot(mercenaryInventory, slotIndex,
                    BAG_X + col * 18, EQUIP_TOP_Y + 54));
        }

        // --- Inventario del jugador (3 filas de 9) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // --- Barra rápida del jugador ---
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    PLAYER_INV_X + col * 18, PLAYER_HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            int mercenarySlots = MercenaryInventory.SIZE;
            int playerInvStart = mercenarySlots;
            int playerInvEnd = playerInvStart + 27;
            int hotbarEnd = playerInvEnd + 9;

            if (index < mercenarySlots) {
                if (!this.moveItemStackTo(stack, playerInvStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, 0, mercenarySlots, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.mercenaryInventory.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.mercenaryInventory.stopOpen(player);
    }

    public MercenaryInventory getMercenaryInventory() {
        return this.mercenaryInventory;
    }

    public int getMercHealth() {
        return this.data.get(0);
    }

    public int getMercMaxHealth() {
        return this.data.get(1);
    }

    public int getMercExp() {
        return this.data.get(2);
    }

    public int getMercMaxExp() {
        return this.data.get(3);
    }

    public int getMercRankOrdinal() {
        return this.data.get(4);
    }

    private static Slot iconSlot(Container container, int slotIndex, int x, int y, String spritePath) {
        final Identifier icon = Identifier.withDefaultNamespace(spritePath);
        return new Slot(container, slotIndex, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return switch (slotIndex) {
                    case MercenaryInventory.SLOT_HELMET -> isArmorForSlot(stack, EquipmentSlot.HEAD);
                    case MercenaryInventory.SLOT_CHESTPLATE -> isArmorForSlot(stack, EquipmentSlot.CHEST);
                    case MercenaryInventory.SLOT_LEGGINGS -> isArmorForSlot(stack, EquipmentSlot.LEGS);
                    case MercenaryInventory.SLOT_BOOTS -> isArmorForSlot(stack, EquipmentSlot.FEET);
                    case MercenaryInventory.SLOT_MAIN_HAND -> isMainHandItem(stack);
                    case MercenaryInventory.SLOT_OFF_HAND -> isOffHandItem(stack);
                    default -> super.mayPlace(stack);
                };
            }

            @Override
            public int getMaxStackSize() {
                // Equipo: 1 por slot; otros usan el límite por defecto
                return switch (slotIndex) {
                    case MercenaryInventory.SLOT_MAIN_HAND,
                            MercenaryInventory.SLOT_OFF_HAND,
                            MercenaryInventory.SLOT_HELMET,
                            MercenaryInventory.SLOT_CHESTPLATE,
                            MercenaryInventory.SLOT_LEGGINGS,
                            MercenaryInventory.SLOT_BOOTS -> 1;
                    default -> super.getMaxStackSize();
                };
            }

            @Override
            public Identifier getNoItemIcon() {
                return icon;
            }
        };
    }

    private static boolean isArmorForSlot(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return false;
        }

        // 1.21.11: usamos los atributos de equipo para saber si un ítem es
        // equipable en un slot concreto (casco, peto, etc.).
        final boolean[] found = new boolean[1];
        stack.forEachModifier(slot, (attribute, modifier) -> found[0] = true);
        return found[0];
    }

    private static boolean hasMainHandAttributes(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        final boolean[] found = new boolean[1];
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> found[0] = true);
        return found[0];
    }

    private static boolean isMainHandItem(ItemStack stack) {
        // Mano principal: cualquier ítem que tenga atributos para MAINHAND
        // (espadas, hachas, picos, lanzas, arcos, etc. en el sistema nuevo).
        return hasMainHandAttributes(stack);
    }

    private static boolean isOffHandItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Escudo y tótem vanilla siempre permitidos en mano secundaria
        if (stack.is(Items.SHIELD) || stack.is(Items.TOTEM_OF_UNDYING)) {
            return true;
        }

        // También permitimos armas/herramientas que tengan atributos de MAINHAND
        return hasMainHandAttributes(stack);
    }
}

