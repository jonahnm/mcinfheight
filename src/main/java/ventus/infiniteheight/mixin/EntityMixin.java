package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "checkBelowWorld", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$disableVoid(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$keepAlive(CallbackInfo ci) {
        ci.cancel();
    }
}
