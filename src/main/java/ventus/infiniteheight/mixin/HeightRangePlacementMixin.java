package ventus.infiniteheight.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

import ventus.infiniteheight.InfiniteHeight;

@Mixin(HeightRangePlacement.class)
public abstract class HeightRangePlacementMixin {
    @Shadow
    @Final
    private HeightProvider height;

    @Inject(method = "modify", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$alsoDeep(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output, CallbackInfo ci) {
        int y = this.height.sample(random, context);
        output.accept(origin.atY(y));
        NoiseSettings tile = InfiniteHeight.activeTile();
        if (tile != null) {
            int slice = Math.min(64, tile.height());
            if (y >= tile.minY() && y < tile.minY() + slice) {
                output.accept(origin.atY(y - slice));
            }
        }
        ci.cancel();
    }
}
