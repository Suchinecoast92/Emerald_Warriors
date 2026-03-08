package emeraldwarriors.inventory;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Contenedor interno del mercenario: 12 slots en total.
 * Slot 0  = arma principal (mano derecha)
 * Slot 1  = mano izquierda (escudo, tótem, poción, etc.)
 * Slot 2  = casco
 * Slot 3  = peto
 * Slot 4  = pantalones
 * Slot 5  = botas
 * Slots 6-11 = mochila (6 slots)
 */
public class MercenaryInventory extends SimpleContainer {

    public static final int SLOT_MAIN_HAND   = 0;
    public static final int SLOT_OFF_HAND    = 1;
    public static final int SLOT_HELMET      = 2;
    public static final int SLOT_CHESTPLATE  = 3;
    public static final int SLOT_LEGGINGS    = 4;
    public static final int SLOT_BOOTS       = 5;
    public static final int SLOT_BAG_START   = 6;
    public static final int SIZE             = 12;

    private final EmeraldMercenaryEntity owner;

    /**
     * Constructor usado en el cliente (no conoce la entidad dueña).
     * No sincroniza equipo visible porque owner es null.
     */
    public MercenaryInventory() {
        this(null);
    }

    /**
     * Constructor server-side con referencia al EmeraldMercenaryEntity dueño.
     * Permite sincronizar los slots 0-5 con los EquipmentSlot reales.
     */
    public MercenaryInventory(EmeraldMercenaryEntity owner) {
        super(SIZE);
        this.owner = owner;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        super.setItem(index, stack);

        if (this.owner == null) {
            return;
        }

        // Sincronizar los primeros 6 slots con el equipo visible del entity
        switch (index) {
            case SLOT_MAIN_HAND -> this.owner.setItemSlot(EquipmentSlot.MAINHAND, stack);
            case SLOT_OFF_HAND -> this.owner.setItemSlot(EquipmentSlot.OFFHAND, stack);
            case SLOT_HELMET -> this.owner.setItemSlot(EquipmentSlot.HEAD, stack);
            case SLOT_CHESTPLATE -> this.owner.setItemSlot(EquipmentSlot.CHEST, stack);
            case SLOT_LEGGINGS -> this.owner.setItemSlot(EquipmentSlot.LEGS, stack);
            case SLOT_BOOTS -> this.owner.setItemSlot(EquipmentSlot.FEET, stack);
            default -> {
                // Slots de mochila (6-11) no afectan al equipo visible directamente.
            }
        }
    }
}
