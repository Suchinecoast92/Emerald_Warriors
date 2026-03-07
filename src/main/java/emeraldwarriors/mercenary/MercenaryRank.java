package emeraldwarriors.mercenary;

public enum MercenaryRank {
    RECRUIT("cobre"),
    SOLDIER("hierro"),
    SENTINEL("oro"),
    VETERAN("esmeralda"),
    ANCIENT_GUARD("diamante");

    private final String textureSuffix;

    MercenaryRank(String textureSuffix) {
        this.textureSuffix = textureSuffix;
    }

    public String getTextureSuffix() {
        return this.textureSuffix;
    }
}
