package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import ventus.infiniteheight.InfiniteHeight;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "doCreateBiomes", at = @At("HEAD"))
    private void infiniteheight$beginBiomeTile(Blender blender, RandomState randomState, ChunkAccess protoChunk, CallbackInfo ci) {
        if ((Object) this instanceof NoiseBasedChunkGenerator generator) {
            NoiseSettings tile = InfiniteHeight.tileSettings(generator.generatorSettings());
            InfiniteHeight.beginTile(tile, tile.minY());
        }
    }

    @Inject(method = "doCreateBiomes", at = @At("RETURN"))
    private void infiniteheight$endBiomeTile(Blender blender, RandomState randomState, ChunkAccess protoChunk, CallbackInfo ci) {
        InfiniteHeight.endTile();
    }

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void infiniteheight$beginDecorTile(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, net.minecraft.world.level.StructureManager structureManager, CallbackInfo ci) {
        if ((Object) this instanceof NoiseBasedChunkGenerator generator) {
            NoiseSettings tile = InfiniteHeight.tileSettings(generator.generatorSettings());
            InfiniteHeight.beginTile(tile, tile.minY());
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void infiniteheight$endDecorTile(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, net.minecraft.world.level.StructureManager structureManager, CallbackInfo ci) {
        InfiniteHeight.endTile();
    }

    @Redirect(method = "doCreateBiomes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getMinY()I"))
    private int infiniteheight$biomeMinY(ChunkAccess chunk) {
        NoiseSettings tile = InfiniteHeight.activeTile();
        return tile != null ? InfiniteHeight.DEEP_FLOOR : chunk.getMinY();
    }

    @Redirect(method = "doCreateBiomes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getHeight()I"))
    private int infiniteheight$biomeHeight(ChunkAccess chunk) {
        NoiseSettings tile = InfiniteHeight.activeTile();
        return tile != null ? tile.minY() + tile.height() - InfiniteHeight.DEEP_FLOOR : chunk.getHeight();
    }
}
