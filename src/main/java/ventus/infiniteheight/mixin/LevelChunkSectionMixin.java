package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

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
}