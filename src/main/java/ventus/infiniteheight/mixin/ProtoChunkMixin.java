package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ProtoChunk;

import ventus.infiniteheight.InfiniteHeight;

@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin {
    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockPos infiniteheight$shiftGetBlock(BlockPos pos) {
        return InfiniteHeight.shiftIfNeeded(pos);
    }

    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true)
    private BlockPos infiniteheight$shiftGetFluid(BlockPos pos) {
        return InfiniteHeight.shiftIfNeeded(pos);
    }

    @ModifyVariable(method = "setBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockPos infiniteheight$shiftSetBlock(BlockPos pos) {
        return InfiniteHeight.shiftIfNeeded(pos);
    }

    @ModifyVariable(method = "markPosForPostProcessing", at = @At("HEAD"), argsOnly = true)
    private BlockPos infiniteheight$shiftPostProcess(BlockPos pos) {
        return InfiniteHeight.shiftIfNeeded(pos);
    }
}
