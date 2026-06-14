package emeraldwarriors.mercenary;

import net.minecraft.network.chat.Component;

public final class MercenaryTranslations {

    private MercenaryTranslations() {
    }

    public static Component days(int count) {
        return Component.translatable(count == 1 ? "emerald_warriors.unit.day" : "emerald_warriors.unit.days", count);
    }

    public static Component emeralds(int count) {
        return Component.translatable(count == 1 ? "emerald_warriors.unit.emerald" : "emerald_warriors.unit.emeralds", count);
    }

    public static Component mercenaries(int count) {
        return Component.translatable(count == 1 ? "emerald_warriors.unit.mercenary" : "emerald_warriors.unit.mercenaries", count);
    }

    public static Component contractOffer(int emeralds, int days) {
        String key = "emerald_warriors.contract.offer."
                + (emeralds == 1 ? "1" : "n")
                + "_"
                + (days == 1 ? "1" : "n");
        return Component.translatable(key, emeralds, days);
    }
}
