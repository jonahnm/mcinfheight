package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

import ventus.infiniteheight.InfiniteHeight;

@Mixin(WorldGenerationContext.class)
public abstract class WorldGenerationContextMixin {
    @Shadow
    @Final
    @Mutable
    private int minY;

    @Shadow
    @Final
    @Mutable
    private int height;

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/LevelHeightAccessor;)V", at = @At("RETURN"))
    private void infiniteheight$clampToActiveTile(ChunkGenerator generator, LevelHeightAccessor heightAccessor, CallbackInfo ci) {
        NoiseSettings tile = InfiniteHeight.activeTile();
        if (tile == null || !InfiniteHeight.isDeepContinuation()) {
            return;
        }
        this.minY = InfiniteHeight.activeWorldMinY();
        this.height = tile.height();
    }
}