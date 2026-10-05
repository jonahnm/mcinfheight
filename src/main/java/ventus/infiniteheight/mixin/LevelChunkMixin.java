package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import ventus.infiniteheight.SparseColumnHolder;

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
        int y = pos.getY();
        LevelChunk self = (LevelChunk) (Object) this;
        LevelChunkSection section = self.getSection(self.getSectionIndex(y));
        cir.setReturnValue(section.hasOnlyAir() ? Blocks.AIR.defaultBlockState() : section.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15));
    }

    @Inject(method = "getFluidState(III)Lnet/minecraft/world/level/material/FluidState;", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$sparseGetFluid(int x, int y, int z, CallbackInfoReturnable<FluidState> cir) {
        LevelChunk self = (LevelChunk) (Object) this;
        LevelChunkSection section = self.getSection(self.getSectionIndex(y));
        cir.setReturnValue(section.hasOnlyAir() ? Fluids.EMPTY.defaultFluidState() : section.getFluidState(x & 15, y & 15, z & 15));
    }

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("RETURN"))
    private void infiniteheight$copyProtoColumns(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.chunk.ProtoChunk protoChunk, LevelChunk.PostLoadProcessor postLoad, CallbackInfo ci) {
        ((SparseColumnHolder) this).infiniteheight$columns().copyFrom(((SparseColumnHolder) protoChunk).infiniteheight$columns());
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void infiniteheight$importPacketSections(int chunkX, int chunkZ, ClientboundLevelChunkPacketData chunkData, CallbackInfo ci) {
        ((SparseColumnHolder) this).infiniteheight$importNativeSections();
    }
}
