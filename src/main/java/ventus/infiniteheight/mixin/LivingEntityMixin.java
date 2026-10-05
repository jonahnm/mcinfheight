package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$noVoidDamage(CallbackInfo ci) {
        ci.cancel();
    }
}
