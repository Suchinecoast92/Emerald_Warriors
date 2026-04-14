package emeraldwarriors.mercenary;

public enum MercenaryRank {
    //                  suffix    hp   dmgMul  kbRes  detectR  maxChase  guardR  patrolR  retreatHp
    RECRUIT(           "cobre",   20,  1.00,   0.00,   8,       10,       6,      6,      0.20),
    SOLDIER(           "hierro",  24,  1.05,   0.10,  12,       14,       7,      8,      0.22),
    SENTINEL(          "oro",     26,  1.10,   0.15,  14,       18,       8,     10,      0.25),
    VETERAN(           "esmeralda", 30, 1.15,  0.20,  16,       22,       9,     11,      0.28),
    ANCIENT_GUARD(     "diamante", 36, 1.25,   0.30,  18,       24,      10,     12,      0.30);

    private final String textureSuffix;
    private final int maxHealth;
    private final double damageMultiplier;
    private final double knockbackResistance;
    private final int detectionRadius;
    private final int maxChaseFromAnchor;
    private final int guardRadius;
    private final int patrolRadius;
    private final double retreatHpFraction;

    MercenaryRank(String textureSuffix, int maxHealth, double damageMultiplier, double knockbackResistance,
                  int detectionRadius, int maxChaseFromAnchor, int guardRadius, int patrolRadius,
                  double retreatHpFraction) {
        this.textureSuffix = textureSuffix;
        this.maxHealth = maxHealth;
        this.damageMultiplier = damageMultiplier;
        this.knockbackResistance = knockbackResistance;
        this.detectionRadius = detectionRadius;
        this.maxChaseFromAnchor = maxChaseFromAnchor;
        this.guardRadius = guardRadius;
        this.patrolRadius = patrolRadius;
        this.retreatHpFraction = retreatHpFraction;
    }

    public String getTextureSuffix() { return this.textureSuffix; }
    public int getMaxHealth() { return this.maxHealth; }
    public double getDamageMultiplier() { return this.damageMultiplier; }
    public double getKnockbackResistance() { return this.knockbackResistance; }
    public int getDetectionRadius() { return this.detectionRadius; }
    public int getMaxChaseFromAnchor() { return this.maxChaseFromAnchor; }
    public int getGuardRadius() { return this.guardRadius; }
    public int getPatrolRadius() { return this.patrolRadius; }
    public double getRetreatHpFraction() { return this.retreatHpFraction; }
}
