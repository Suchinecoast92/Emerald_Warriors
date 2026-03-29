package emeraldwarriors.mercenary;

public enum MercenaryOrder {
    FOLLOW("Sígueme"),
    STAY("Espera aquí"),
    PATROL("Patrullar zona"),
    NONE("Sin asignación");

    private final String displayName;

    MercenaryOrder(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public MercenaryOrder next() {
        MercenaryOrder[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
