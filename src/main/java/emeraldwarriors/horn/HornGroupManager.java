package emeraldwarriors.horn;

import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages NBT-based group data stored on goat horn ItemStacks.
 * Data layout inside CustomData:
 *   emerald_warriors:
 *     horn_group_id: "uuid-string"
 *     linked_mercenaries: ["uuid1", "uuid2", ...]
 *     pending_order: "FOLLOW" | "GUARD" | "PATROL"
 */
public class HornGroupManager {

    private static final String ROOT_KEY     = "emerald_warriors";
    private static final String GROUP_ID_KEY = "horn_group_id";
    private static final String MERCS_KEY    = "linked_mercenaries";
    private static final String ORDER_KEY    = "pending_order";

    /** Returns true if the stack is a vanilla Goat Horn. */
    public static boolean isGoatHorn(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.GOAT_HORN);
    }

    // ── Read helpers ─────────────────────────────────────────────────

    private static CompoundTag readRoot(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.isEmpty()) return new CompoundTag();
        CompoundTag top = data.copyTag();
        return top.getCompoundOrEmpty(ROOT_KEY);
    }

    /** Returns all mercenary UUIDs linked to this horn. */
    public static List<UUID> getLinkedMercenaries(ItemStack stack) {
        ListTag list = readRoot(stack).getListOrEmpty(MERCS_KEY);
        List<UUID> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                result.add(UUID.fromString(list.getStringOr(i, "")));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public static boolean isMercenaryLinked(ItemStack stack, UUID mercId) {
        return getLinkedMercenaries(stack).contains(mercId);
    }

    /** Returns the currently stored order (default: FOLLOW). */
    public static MercenaryOrder getOrder(ItemStack stack) {
        String orderStr = readRoot(stack).getStringOr(ORDER_KEY, MercenaryOrder.FOLLOW.name());
        try {
            return MercenaryOrder.valueOf(orderStr);
        } catch (IllegalArgumentException e) {
            return MercenaryOrder.FOLLOW;
        }
    }

    /** Returns how many mercenaries are linked to this horn. */
    public static int getLinkedCount(ItemStack stack) {
        return getLinkedMercenaries(stack).size();
    }

    // ── Write helpers ─────────────────────────────────────────────────

    /** Adds a mercenary UUID to this horn's group. */
    public static void addMercenary(ItemStack stack, UUID mercId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag root = tag.getCompoundOrEmpty(ROOT_KEY);
            ListTag list = root.getListOrEmpty(MERCS_KEY);

            // Avoid duplicates
            for (int i = 0; i < list.size(); i++) {
                if (list.getStringOr(i, "").equals(mercId.toString())) {
                    tag.put(ROOT_KEY, root);
                    return;
                }
            }
            ListTag mutable = new ListTag();
            mutable.addAll(list);
            mutable.add(StringTag.valueOf(mercId.toString()));
            root.put(MERCS_KEY, mutable);

            if (!root.contains(GROUP_ID_KEY)) root.putString(GROUP_ID_KEY, UUID.randomUUID().toString());
            if (!root.contains(ORDER_KEY))    root.putString(ORDER_KEY, MercenaryOrder.FOLLOW.name());
            tag.put(ROOT_KEY, root);
        });
    }

    /** Removes a mercenary UUID from this horn's group. */
    public static void removeMercenary(ItemStack stack, UUID mercId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag root = tag.getCompoundOrEmpty(ROOT_KEY);
            ListTag oldList = root.getListOrEmpty(MERCS_KEY);
            ListTag newList = new ListTag();
            for (int i = 0; i < oldList.size(); i++) {
                String s = oldList.getStringOr(i, "");
                if (!s.equals(mercId.toString())) {
                    newList.add(StringTag.valueOf(s));
                }
            }
            root.put(MERCS_KEY, newList);
            tag.put(ROOT_KEY, root);
        });
    }

    /** Toggles a mercenary. Returns true if NOW linked. */
    public static boolean toggleMercenary(ItemStack stack, UUID mercId) {
        if (isMercenaryLinked(stack, mercId)) {
            removeMercenary(stack, mercId);
            return false;
        } else {
            addMercenary(stack, mercId);
            return true;
        }
    }

    /** Cycles FOLLOW → GUARD → PATROL → FOLLOW. Returns the new order. */
    public static MercenaryOrder cycleOrder(ItemStack stack) {
        MercenaryOrder next = getOrder(stack).next();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag root = tag.getCompoundOrEmpty(ROOT_KEY);
            root.putString(ORDER_KEY, next.name());
            if (!root.contains(GROUP_ID_KEY)) root.putString(GROUP_ID_KEY, UUID.randomUUID().toString());
            tag.put(ROOT_KEY, root);
        });
        return next;
    }
}
