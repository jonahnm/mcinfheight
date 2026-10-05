package ventus.infiniteheight.mixin;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;

import ventus.infiniteheight.InfiniteHeight;
import ventus.infiniteheight.SparseColumns;
import ventus.infiniteheight.TileFiller;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin implements TileFiller {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    private NoiseChunk createNoiseChunk(
        ChunkAccess chunk,
        StructureManager structureManager,
        Blender blender,
        RandomState randomState,
        NoiseSettings noiseSettings
    ) {
        throw new AssertionError();
    }

    @Shadow
    private void doFill(NoiseChunk noiseChunk, ChunkAccess chunk) {
        throw new AssertionError();
    }

    @Shadow
    private void buildSurface(
        ChunkAccess protoChunk,
        NoiseChunk noiseChunk,
        RandomState randomState,
        BiomeManager biomeManager,
        Set<Holder<Biome>> possibleBiomes,
        MaterialRule materialRule
    ) {
        throw new AssertionError();
    }

    @Shadow
    private void generateCarvers(
        ChunkAccess chunk,
        Blender blender,
        NoiseChunk noiseChunk,
        RandomState randomState,
        BiomeManager biomeManager,
        WorldGenRegion carverBiomeRegion,
        MaterialRule materialRule
    ) {
        throw new AssertionError();
    }

    @Override
    public void infiniteheight$fillTile(ChunkAccess chunk, NoiseSettings tile, int worldMinY, ServerLevel level, Set<Holder<Biome>> biomes) {
        NoiseSettings vanilla = this.settings.value().noiseSettings();
        boolean deep = worldMinY < vanilla.minY();
        NoiseSettings sample = deep ? InfiniteHeight.deepSlice(vanilla) : tile;
        InfiniteHeight.beginTile(sample, worldMinY, deep);
        try (NoiseChunk noiseChunk = this.createNoiseChunk(chunk, level.structureManager(), Blender.empty(), level.getChunkSource().randomState(), sample)) {
            int topSection = chunk.getSectionIndex(worldMinY + sample.height() - 1);
            int bottomSection = chunk.getSectionIndex(worldMinY);
            int max = Math.max(topSection, bottomSection);
            int min = Math.min(topSection, bottomSection);
            for (int sectionIndex = max; sectionIndex >= min; sectionIndex--) {
                chunk.getSection(sectionIndex).acquire();
            }
            try {
                this.doFill(noiseChunk, chunk);
            } finally {
                for (int sectionIndex = max; sectionIndex >= min; sectionIndex--) {
                    chunk.getSection(sectionIndex).release();
                }
            }
            RandomState randomState = level.getChunkSource().randomState();
            BiomeManager biomeManager = level.getBiomeManager();
            MaterialRule materialRule = this.settings.value().materialRule().value();
            this.buildSurface(chunk, noiseChunk, randomState, biomeManager, biomes, materialRule);
            InfiniteHeight.setCarverPass(deep);
            try {
                this.generateCarvers(chunk, Blender.empty(), noiseChunk, randomState, biomeManager, null, materialRule);
            } finally {
                InfiniteHeight.setCarverPass(false);
            }
            if (deep) {
                this.infiniteheight$copyDeepTileDetails(chunk, worldMinY, sample.height());
            }
        } finally {
            InfiniteHeight.endTile();
        }
    }

    @Inject(method = "buildTerrain", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$vanillaColumnOnly(
        ChunkAccess chunk,
        Blender blender,
        RandomState randomState,
        StructureManager structureManager,
        BiomeManager biomeManager,
        WorldGenRegion carverBiomeRegion,
        Set<Holder<Biome>> possibleBiomes,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
    ) {
        NoiseSettings tile = this.settings.value().noiseSettings();
        if (tile.height() <= 0 || chunk.getHeight() <= tile.height()) {
            return;
        }
        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            this.infiniteheight$generateColumn(chunk, blender, randomState, structureManager, biomeManager, carverBiomeRegion, possibleBiomes, tile, tile.minY(), false);
            SparseColumns.of(chunk).markTile(tile.minY());
            NoiseSettings deep = InfiniteHeight.deepSlice(tile);
            int deepOrigin = tile.minY() - deep.height();
            this.infiniteheight$generateColumn(chunk, blender, randomState, structureManager, biomeManager, carverBiomeRegion, possibleBiomes, deep, deepOrigin, true);
            SparseColumns.of(chunk).markTile(deepOrigin);
            return chunk;
        }, Util.backgroundExecutor().forName("buildTerrain")));
    }

    @Unique
    private void infiniteheight$generateColumn(
        ChunkAccess chunk,
        Blender blender,
        RandomState randomState,
        StructureManager structureManager,
        BiomeManager biomeManager,
        WorldGenRegion carverBiomeRegion,
        Set<Holder<Biome>> possibleBiomes,
        NoiseSettings tile,
        int worldMinY,
        boolean deep
    ) {
        InfiniteHeight.beginTile(tile, worldMinY, deep);
        try (NoiseChunk noiseChunk = this.createNoiseChunk(chunk, structureManager, blender, randomState, tile)) {
            int topSection = chunk.getSectionIndex(worldMinY + tile.height() - 1);
            int bottomSection = chunk.getSectionIndex(worldMinY);
            int max = Math.max(topSection, bottomSection);
            int min = Math.min(topSection, bottomSection);
            for (int sectionIndex = max; sectionIndex >= min; sectionIndex--) {
                chunk.getSection(sectionIndex).acquire();
            }
            try {
                this.doFill(noiseChunk, chunk);
            } finally {
                for (int sectionIndex = max; sectionIndex >= min; sectionIndex--) {
                    chunk.getSection(sectionIndex).release();
                }
            }
            MaterialRule materialRule = this.settings.value().materialRule().value();
            this.buildSurface(chunk, noiseChunk, randomState, biomeManager, possibleBiomes, materialRule);
            InfiniteHeight.setCarverPass(deep);
            try {
                this.generateCarvers(chunk, blender, noiseChunk, randomState, biomeManager, carverBiomeRegion, materialRule);
            } finally {
                InfiniteHeight.setCarverPass(false);
            }
            if (deep) {
                this.infiniteheight$copyDeepTileDetails(chunk, worldMinY, tile.height());
            }
        } finally {
            InfiniteHeight.endTile();
        }
    }

    @Unique
    private void infiniteheight$copyDeepTileDetails(ChunkAccess chunk, int worldMinY, int height) {
        NoiseSettings vanilla = this.settings.value().noiseSettings();
        int sourceBase = vanilla.minY();
        int sourceSpan = Math.min(64, vanilla.height());
        ChunkPos pos = chunk.getPos();
        int quartMinX = QuartPos.fromBlock(pos.getMinBlockX());
        int quartMinZ = QuartPos.fromBlock(pos.getMinBlockZ());
        int top = chunk.getSectionIndex(worldMinY + height - 1);
        int bottom = chunk.getSectionIndex(worldMinY);
        for (int destIndex = Math.min(top, bottom); destIndex <= Math.max(top, bottom); destIndex++) {
            int destY = ((LevelHeightAccessor) chunk).getSectionYFromSectionIndex(destIndex);
            int blockOffset = (destY << 4) - worldMinY;
            int sourceY = sourceBase + Math.floorMod(blockOffset, sourceSpan);
            int sourceIndex = chunk.getSectionIndexFromSectionY(SectionPos.blockToSectionCoord(sourceY));
            LevelChunkSection dest = chunk.getSection(destIndex);
            LevelChunkSection source = chunk.getSection(sourceIndex);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = source.getBlockState(x, y, z);
                        if (!state.isAir() && state.is(BlockTags.ORES)) {
                            dest.setBlockState(x, y, z, state, false);
                        }
                    }
                }
            }
            LevelChunkSection biomeSource = chunk.getSection(sourceIndex);
            final LevelChunkSection copyFrom = biomeSource;
            dest.fillBiomesFromNoise(
                (quartX, quartY, quartZ) -> copyFrom.getNoiseBiome(quartX & 3, quartY & 3, quartZ & 3),
                quartMinX,
                QuartPos.fromSection(destY),
                quartMinZ
            );
        }
    }

    @Redirect(
        method = "doFill",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState infiniteheight$noBedrock(
        LevelChunkSection section,
        int x,
        int y,
        int z,
        BlockState state,
        boolean lock
    ) {
        return section.setBlockState(x, y, z, InfiniteHeight.withoutBedrock(state), lock);
    }

    @Redirect(
        method = "doFill",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getSectionIndex(I)I")
    )
    private int infiniteheight$offsetSectionIndex(ChunkAccess chunk, int blockY) {
        return chunk.getSectionIndex(blockY + InfiniteHeight.fillYOffset());
    }

    @Redirect(
        method = "doFill",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/Heightmap;update(IIILnet/minecraft/world/level/block/state/BlockState;)Z"
        )
    )
    private boolean infiniteheight$offsetHeightmap(Heightmap heightmap, int x, int y, int z, BlockState state) {
        return heightmap.update(x, y + InfiniteHeight.fillYOffset(), z, state);
    }

    @Redirect(
        method = "doFill",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;")
    )
    private BlockPos.MutableBlockPos infiniteheight$offsetPostProcess(BlockPos.MutableBlockPos pos, int x, int y, int z) {
        return pos.set(x, y + InfiniteHeight.fillYOffset(), z);
    }

    @Redirect(
        method = "iterateNoiseColumn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseSettings;clampToHeightAccessor(Lnet/minecraft/world/level/LevelHeightAccessor;)Lnet/minecraft/world/level/levelgen/NoiseSettings;"
        )
    )
    private NoiseSettings infiniteheight$keepVanillaColumn(NoiseSettings settings, LevelHeightAccessor heightAccessor) {
        return settings.height() > 0 ? settings : settings.clampToHeightAccessor(heightAccessor);
    }
}