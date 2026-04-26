package emeraldwarriors.mercenary;

public enum MercenaryOrder {
    FOLLOW("Sígueme"),       // Sigue al owner, solo combate defensivo
    NEUTRAL("Sin asignación"), // Deambula sin combate proactivo; solo se defiende si lo atacan
    GUARD("Guarda aquí"),    // Posición fija, combate en radio
    PATROL("Patrullar zona"); // Ronda zona, combate activo en área

    private final String displayName;

    MercenaryOrder(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public MercenaryOrder next() {
        return switch (this) {
            case FOLLOW -> GUARD;
            case GUARD  -> PATROL;
            case PATROL -> NEUTRAL;
            case NEUTRAL -> FOLLOW;
        };
    }
}
