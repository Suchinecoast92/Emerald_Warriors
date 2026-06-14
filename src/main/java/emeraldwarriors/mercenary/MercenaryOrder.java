package emeraldwarriors.mercenary;

import net.minecraft.network.chat.Component;

public enum MercenaryOrder {
    FOLLOW("emerald_warriors.order.follow"),
    NEUTRAL("emerald_warriors.order.neutral"),
    GUARD("emerald_warriors.order.guard"),
    PATROL("emerald_warriors.order.patrol");

    private final String translationKey;

    MercenaryOrder(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(this.translationKey);
    }

    public MercenaryOrder next() {
        return switch (this) {
            case FOLLOW -> GUARD;
            case GUARD -> PATROL;
            case PATROL -> NEUTRAL;
            case NEUTRAL -> FOLLOW;
        };
    }
}
