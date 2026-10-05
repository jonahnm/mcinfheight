package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import ventus.infiniteheight.SparseColumnHolder;
import ventus.infiniteheight.SparseColumns;

/**
 * Reads inside the dimension are left entirely to vanilla, which already bounds-checks and reads
 * the native array. Only Y outside the dimension is routed to the sparse map, so ordinary block
 * lookups cost nothing extra.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow
    @Final
    private Level level;

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$sparseGetBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (this.level.isDebug()) {
            return;
        }
        LevelChunk self = (LevelChunk) (Object) this;
        int y = pos.getY();
        if (!self.isOutsideBuildHeight(y)) {
            return;
        }
        LevelChunkSection section = this.infiniteheight$sparseSection(y);
        if (section == null || section.hasOnlyAir()) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }
        cir.setReturnValue(section.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15));
    }

    @Inject(method = "getFluidState(III)Lnet/minecraft/world/level/material/FluidState;", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$sparseGetFluid(int x, int y, int z, CallbackInfoReturnable<FluidState> cir) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!self.isOutsideBuildHeight(y)) {
            return;
        }
        LevelChunkSection section = this.infiniteheight$sparseSection(y);
        if (section == null || section.hasOnlyAir()) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        cir.setReturnValue(section.getFluidState(x & 15, y & 15, z & 15));
    }

    @Unique
    private LevelChunkSection infiniteheight$sparseSection(int y) {
        SparseColumns columns = ((SparseColumnHolder) this).infiniteheight$columns();
        return columns.get(SectionPos.blockToSectionCoord(y));
    }

    @Inject(
        method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
        at = @At("RETURN")
    )
    private void infiniteheight$copyProtoColumns(
        net.minecraft.server.level.ServerLevel level,
        net.minecraft.world.level.chunk.ProtoChunk protoChunk,
        LevelChunk.PostLoadProcessor postLoad,
        CallbackInfo ci
    ) {
        ((SparseColumnHolder) this).infiniteheight$columns().merge(((SparseColumnHolder) protoChunk).infiniteheight$columns());
    }
}