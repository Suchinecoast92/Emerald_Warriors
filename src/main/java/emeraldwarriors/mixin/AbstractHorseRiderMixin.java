package emeraldwarriors.mixin;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla only accepts a Player as a horse's controlling passenger, so a mercenary rider is
 * treated as cargo: the mount keeps its own wander goals and overwrites the paths the rider
 * sets, leaving the mercenary unable to steer.
 *
 * Reporting the rider here is the vanilla route for mob-ridden vehicles: Mob.updateControlFlags()
 * disables the mount's MOVE/JUMP/LOOK goals whenever the controller is a Mob. Movement still runs
 * on the mount's own navigation because LivingEntity only reads rider input for Player controllers.
 */
@Mixin(AbstractHorse.class)
public class AbstractHorseRiderMixin {

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void mercenaryRiderTakesControl(CallbackInfoReturnable<LivingEntity> cir) {
        Entity rider = ((Entity) (Object) this).getFirstPassenger();
        if (rider instanceof EmeraldMercenaryEntity mercenary) {
            cir.setReturnValue(mercenary);
        }
    }
}
