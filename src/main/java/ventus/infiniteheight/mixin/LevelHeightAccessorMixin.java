package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.LevelHeightAccessor;

@Mixin(LevelHeightAccessor.class)
public interface LevelHeightAccessorMixin {
    @Inject(method = "isOutsideBuildHeight(I)Z", at = @At("HEAD"), cancellable = true)
    default void infiniteheight$neverOutside(int blockY, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}