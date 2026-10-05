package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.NoiseSettings;

import ventus.infiniteheight.InfiniteHeight;

@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin {
    @ModifyVariable(
        method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BlockState infiniteheight$noBedrock(BlockState state) {
        return InfiniteHeight.withoutBedrock(state);
    }

    @Redirect(
        method = "fillBiomesFromNoise",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/BiomeResolver;getNoiseBiome(III)Lnet/minecraft/core/Holder;"
        )
    )
    private Holder<Biome> infiniteheight$wrapBiomeSample(BiomeResolver resolver, int quartX, int quartY, int quartZ) {
        NoiseSettings tile = InfiniteHeight.activeTile();
        if (tile != null) {
            quartY = InfiniteHeight.wrapQuartY(quartY, tile);
        }
        return resolver.getNoiseBiome(quartX, quartY, quartZ);
    }
}
