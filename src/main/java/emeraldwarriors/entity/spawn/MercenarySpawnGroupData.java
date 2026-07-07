package emeraldwarriors.entity.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.SpawnGroupData;

import java.util.UUID;

/** Estado compartido entre mercenarios del mismo spawn natural en grupo. */
public final class MercenarySpawnGroupData implements SpawnGroupData {

    public boolean mountedPatrol;
    public UUID leaderId;
    public BlockPos patrolAnchor;
    public int membersSpawned;

    public MercenarySpawnGroupData() {
    }
}
