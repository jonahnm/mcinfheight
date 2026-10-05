package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import ventus.infiniteheight.DeepTileFiller;
import ventus.infiniteheight.InfiniteHeight;
import ventus.infiniteheight.SparseColumns;

/**
 * 1.21.1 gives the chunk ownership of its {@code NoiseChunk} and fills using interpolated cells over
 * the chunk's declared height, so vanilla generation cannot be stretched to cover extra height.
 * Instead we generate deep tiles ourselves by sampling the final density function pointwise, then
 * copy ores from the matching layer of the vanilla column.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin implements DeepTileFiller {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Unique
    @Override
    public NoiseSettings infiniteheight$vanillaTile() {
        return this.settings.value().noiseSettings();
    }

    @Inject(method = "doFill", at = @At("RETURN"))
    private void infiniteheight$firstDeepTile(
        Blender blender,
        StructureManager structureManager,
        RandomState randomState,
        ChunkAccess chunk,
        int minSection,
        int sectionCount,
        CallbackInfoReturnable<ChunkAccess> cir
    ) {
        NoiseSettings vanilla = this.settings.value().noiseSettings();
        int origin = vanilla.minY() - InfiniteHeight.DEEP_SLICE;
        SparseColumns columns = SparseColumns.of(chunk);
        if (!columns.hasTile(origin)) {
            // Deliberately not marked: ore decoration has not run yet at this stage. The tile is
            // regenerated (terrain plus ores) by InfiniteHeight#ensureAround once the player
            // descends, which is also where it gets marked.
            this.infiniteheight$generateTile(chunk, origin, randomState);
        }
    }

    @Unique
    @Override
    public void infiniteheight$fillDeepTile(ChunkAccess chunk, int worldMinY, ServerLevel level) {
        this.infiniteheight$generateTile(chunk, worldMinY, level.getChunkSource().randomState());
    }

    @Unique
    private void infiniteheight$generateTile(ChunkAccess chunk, int worldMinY, RandomState randomState) {
        NoiseGeneratorSettings settings = this.settings.value();
        NoiseSettings vanilla = settings.noiseSettings();
        DensityFunction finalDensity = settings.noiseRouter().finalDensity();
        BlockState stone = settings.defaultBlock();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        ChunkPos pos = chunk.getPos();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();

        for (int localY = 0; localY < InfiniteHeight.DEEP_SLICE; localY++) {
            int worldY = worldMinY + localY;
            BlockState filler = worldY < 0 ? deepslate : stone;
            int sectionIndex = chunk.getSectionIndex(worldY);
            LevelChunkSection section = chunk.getSection(sectionIndex);
            int sectionY = worldY & 15;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int worldZ = baseZ + localZ;
                    DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(worldX, worldY, worldZ);
                    if (finalDensity.compute(ctx) > 0.0) {
                        section.setBlockState(localX, sectionY, localZ, filler, false);
                    }
                }
            }
        }

        this.infiniteheight$copyOres(chunk, worldMinY, vanilla.minY(), Math.min(64, vanilla.height()));
    }

    /**
     * Vanilla ore decoration only runs for the vanilla column, so the ore blocks it placed in the
     * bottom slice are replicated into every deep tile. Section alignment matches because both the
     * tile origin and the source base are multiples of 16.
     */
    @Unique
    private void infiniteheight$copyOres(ChunkAccess chunk, int worldMinY, int sourceBase, int sourceSpan) {
        int top = chunk.getSectionIndex(worldMinY + InfiniteHeight.DEEP_SLICE - 1);
        int bottom = chunk.getSectionIndex(worldMinY);
        for (int destIndex = Math.min(top, bottom); destIndex <= Math.max(top, bottom); destIndex++) {
            int destSectionY = destIndex + chunk.getMinSection();
            int relative = (destSectionY << 4) - worldMinY;
            int sourceSectionY = SectionPos.blockToSectionCoord(sourceBase + Math.floorMod(relative, sourceSpan));
            int sourceIndex = chunk.getSectionIndexFromSectionY(sourceSectionY);
            LevelChunkSection dest = chunk.getSection(destIndex);
            LevelChunkSection source = chunk.getSection(sourceIndex);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = source.getBlockState(x, y, z);
                        if (!state.isAir() && InfiniteHeight.isOre(state)) {
                            dest.setBlockState(x, y, z, state, false);
                        }
                    }
                }
            }
        }
    }
}