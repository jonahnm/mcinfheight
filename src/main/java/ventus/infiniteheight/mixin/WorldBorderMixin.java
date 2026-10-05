package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.border.WorldBorder;

import ventus.infiniteheight.InfiniteHeight;

@Mixin(WorldBorder.class)
public abstract class WorldBorderMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/level/border/WorldBorder$Settings;)V", at = @At("RETURN"))
    private void infiniteheight$limitHorizontal(WorldBorder.Settings settings, CallbackInfo ci) {
        ((WorldBorder) (Object) this).setAbsoluteMaxSize(InfiniteHeight.WORLD_BORDER);
    }
}
