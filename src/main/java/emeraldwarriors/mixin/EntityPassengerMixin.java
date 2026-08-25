package emeraldwarriors.mixin;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.mount.MercenaryMounts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Vanilla inserts a mounting {@link Player} at passenger index 0 (front seat) even when a
 * mercenary is already riding. That swaps seats: the player renders in front while the
 * mercenary is pushed to the back. Keep the mercenary in the front seat and place the
 * player behind as a passenger.
 */
@Mixin(Entity.class)
public abstract class EntityPassengerMixin {

    @Redirect(
            method = "addPassenger",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(ILjava/lang/Object;)V")
    )
    private void emeraldWarriors$keepMercenaryInFrontSeat(List<Object> passengers, int index, Object passenger) {
        Entity vehicle = (Entity) (Object) this;
        if (index == 0
                && passenger instanceof Player player
                && !vehicle.level().isClientSide()
                && MercenaryMounts.isSupportedMount(vehicle)) {
            for (Entity existing : vehicle.getPassengers()) {
                if (existing instanceof EmeraldMercenaryEntity) {
                    passengers.add(player);
                    return;
                }
            }
        }
        passengers.add(index, passenger);
    }

    @Redirect(
            method = "addPassenger",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z")
    )
    private boolean emeraldWarriors$promoteMercenaryToFrontSeat(List<Object> passengers, Object passenger) {
        Entity vehicle = (Entity) (Object) this;
        if (passenger instanceof EmeraldMercenaryEntity mercenary
                && !vehicle.level().isClientSide()
                && MercenaryMounts.isSupportedMount(vehicle)
                && vehicle.getFirstPassenger() instanceof Player) {
            passengers.add(0, mercenary);
            return true;
        }
        return passengers.add(passenger);
    }
}
