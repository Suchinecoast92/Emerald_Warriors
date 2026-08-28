package emeraldwarriors.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobAiAccessor {

    @Accessor("moveControl")
    void emeraldWarriors$setMoveControl(MoveControl moveControl);

    @Accessor("navigation")
    void emeraldWarriors$setNavigation(PathNavigation navigation);
}
