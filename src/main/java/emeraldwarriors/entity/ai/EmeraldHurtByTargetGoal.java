package emeraldwarriors.entity.ai;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

public class EmeraldHurtByTargetGoal extends TargetGoal {
    private final EmeraldMercenaryEntity mercenary;
    private LivingEntity attacker;
    private int timestamp;

    public EmeraldHurtByTargetGoal(EmeraldMercenaryEntity mercenary) {
        super(mercenary, false);
        this.mercenary = mercenary;
        this.setFlags(EnumSet.<Goal.Flag>of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity lastHurtBy = this.mercenary.getLastHurtByMob();
        if (lastHurtBy == null || !lastHurtBy.isAlive()) {
            return false;
        }

        UUID owner = this.mercenary.getOwnerUuid();
        if (owner != null && lastHurtBy instanceof Player p && owner.equals(p.getUUID())) {
            return false;
        }

        int hurtTimestamp = this.mercenary.getLastHurtByMobTimestamp();
        if (hurtTimestamp == this.timestamp) {
            return false;
        }

        this.attacker = lastHurtBy;
        this.timestamp = hurtTimestamp;

        return !(this.attacker instanceof Player)
                || (!this.attacker.isSpectator() && !((Player) this.attacker).isCreative());
    }

    @Override
    public void start() {
        this.mob.setTarget(this.attacker);
        super.start();
    }
}
