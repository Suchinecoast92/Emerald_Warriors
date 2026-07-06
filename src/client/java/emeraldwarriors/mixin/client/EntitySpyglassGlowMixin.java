package emeraldwarriors.mixin.client;

import emeraldwarriors.spyglass.SpyglassClientGlowTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySpyglassGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void emeraldWarriors$spyglassClientGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (SpyglassClientGlowTracker.shouldGlow(self.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
