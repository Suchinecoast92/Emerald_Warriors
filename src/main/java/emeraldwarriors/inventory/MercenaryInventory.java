package emeraldwarriors.inventory;

import net.minecraft.world.SimpleContainer;

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

    public MercenaryInventory() {
        super(SIZE);
    }
}
