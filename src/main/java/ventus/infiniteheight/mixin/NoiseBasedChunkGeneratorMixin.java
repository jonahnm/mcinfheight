package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
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
 * 1.21.1 clamps vanilla's noise fill to its own column and owns the {@code NoiseChunk}, so extra
 * height cannot be produced by stretching vanilla. Deep tiles are generated on demand instead.
 *
 * <p>Cost matters here: sampling the final density once per block would take 16384 evaluations per
 * tile, roughly 13x vanilla's whole noise pass for a chunk. Vanilla itself samples on a coarse cell
 * grid and interpolates, so this does the same and drops to a couple hundred evaluations.
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

    @Unique
    @Override
    public void infiniteheight$fillDeepTile(ChunkAccess chunk, int worldMinY, ServerLevel level) {
        NoiseSettings vanilla = this.settings.value().noiseSettings();
        if (
            worldMinY < chunk.getMinBuildHeight()
                || worldMinY + InfiniteHeight.DEEP_SLICE > chunk.getMaxBuildHeight()
        ) {
            return;
        }
        this.infiniteheight$generateTile(chunk, worldMinY, vanilla, level.getChunkSource().randomState());
    }

    @Unique
    private void infiniteheight$generateTile(
        ChunkAccess chunk,
        int worldMinY,
        NoiseSettings vanilla,
        RandomState randomState
    ) {
        NoiseGeneratorSettings settings = this.settings.value();
        // RandomState's router is the wired one (RandomState maps the settings template through
        // NoiseWiringHelper); the settings template itself carries unresolved noise holders.
        DensityFunction finalDensity = randomState.router().finalDensity();
        ChunkPos pos = chunk.getPos();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();

        int cellW = Math.max(1, vanilla.noiseSizeHorizontal());
        int cellH = Math.max(1, vanilla.noiseSizeVertical());

        int x0 = Math.floorDiv(baseX, cellW);
        int z0 = Math.floorDiv(baseZ, cellW);
        int y0 = Math.floorDiv(worldMinY, cellH);
        int nx = Math.floorDiv(baseX + 15, cellW) - x0 + 2;
        int nz = Math.floorDiv(baseZ + 15, cellW) - z0 + 2;
        int ny = Math.floorDiv(worldMinY + InfiniteHeight.DEEP_SLICE - 1, cellH) - y0 + 2;

        double[] cells = new double[nx * ny * nz];
        int index = 0;
        for (int cy = 0; cy < ny; cy++) {
            int sampleY = (y0 + cy) * cellH;
            for (int cz = 0; cz < nz; cz++) {
                int sampleZ = (z0 + cz) * cellW;
                for (int cx = 0; cx < nx; cx++) {
                    int sampleX = (x0 + cx) * cellW;
                    cells[index++] = finalDensity.compute(
                        new DensityFunction.SinglePointContext(sampleX, sampleY, sampleZ)
                    );
                }
            }
        }

        BlockState stone = settings.defaultBlock();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();

        for (int localY = 0; localY < InfiniteHeight.DEEP_SLICE; localY++) {
            int worldY = worldMinY + localY;
            int sectionIndex = chunk.getSectionIndex(worldY);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(sectionIndex);
            int sectionY = worldY & 15;
            BlockState filler = worldY < 0 ? deepslate : stone;

            int cy = Math.floorDiv(worldY, cellH) - y0;
            float ty = (float) (worldY - (y0 + cy) * cellH) / cellH;

            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int cx = Math.floorDiv(worldX, cellW) - x0;
                float tx = (float) (worldX - (x0 + cx) * cellW) / cellW;

                for (int localZ = 0; localZ < 16; localZ++) {
                    int worldZ = baseZ + localZ;
                    int cz = Math.floorDiv(worldZ, cellW) - z0;
                    float tz = (float) (worldZ - (z0 + cz) * cellW) / cellW;

                    if (infiniteheight$lerp(cells, nx, nz, cx, cy, cz, tx, ty, tz) > 0.0) {
                        section.setBlockState(localX, sectionY, localZ, filler, false);
                    }
                }
            }
        }

        this.infiniteheight$applyOres(chunk, worldMinY, vanilla);
    }

    @Unique
    private static double infiniteheight$lerp(
        double[] cells,
        int nx,
        int nz,
        int cx,
        int cy,
        int cz,
        float tx,
        float ty,
        float tz
    ) {
        int i000 = (cy * nz + cz) * nx + cx;
        int i100 = i000 + 1;
        int i010 = i000 + nx;
        int i110 = i010 + 1;
        int i001 = i000 + nx * nz;
        int i101 = i001 + 1;
        int i011 = i001 + nx * nz;
        int i111 = i011 + 1;

        double x00 = cells[i000] + (cells[i100] - cells[i000]) * tx;
        double x10 = cells[i010] + (cells[i110] - cells[i010]) * tx;
        double x01 = cells[i001] + (cells[i101] - cells[i001]) * tx;
        double x11 = cells[i011] + (cells[i111] - cells[i011]) * tx;
        double z0v = x00 + (x10 - x00) * ty;
        double z1v = x01 + (x11 - x01) * ty;
        return z0v + (z1v - z0v) * tz;
    }

    /**
     * Vanilla only decorates its own column, so the ore blocks it placed in the bottom slice are
     * read once per chunk into a template and stamped into every deep tile. Copying block by block
     * would otherwise mean 16384 reads per tile for a few hundred ores.
     */
    @Unique
    private void infiniteheight$applyOres(ChunkAccess chunk, int worldMinY, NoiseSettings vanilla) {
        SparseColumns columns = SparseColumns.of(chunk);
        Int2ObjectMap<BlockState> template = columns.oreTemplate();
        if (template == null) {
            template = infiniteheight$buildOreTemplate(chunk, vanilla);
            columns.setOreTemplate(template);
        }
        for (Int2ObjectMap.Entry<BlockState> entry : template.int2ObjectEntrySet()) {
            int key = entry.getIntKey();
            int relY = (key >>> 8) & 63;
            int localX = (key >> 4) & 15;
            int localZ = key & 15;
            int worldY = worldMinY + relY;
            if (worldY < chunk.getMinBuildHeight() || worldY >= chunk.getMaxBuildHeight()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(worldY));
            int sectionY = worldY & 15;
            if (section.getBlockState(localX, sectionY, localZ).isAir()) {
                continue;
            }
            section.setBlockState(localX, sectionY, localZ, entry.getValue(), false);
        }
    }

    @Unique
    private static Int2ObjectMap<BlockState> infiniteheight$buildOreTemplate(
        ChunkAccess chunk,
        NoiseSettings vanilla
    ) {
        Int2ObjectMap<BlockState> template = new Int2ObjectOpenHashMap<>();
        int sourceBase = vanilla.minY();
        int span = Math.min(InfiniteHeight.DEEP_SLICE, vanilla.height());
        for (int relY = 0; relY < span; relY++) {
            int sourceY = sourceBase + relY;
            if (sourceY < chunk.getMinBuildHeight() || sourceY >= chunk.getMaxBuildHeight()) {
                continue;
            }
            LevelChunkSection source = chunk.getSection(chunk.getSectionIndex(sourceY));
            if (source.hasOnlyAir()) {
                continue;
            }
            int sectionY = sourceY & 15;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = source.getBlockState(x, sectionY, z);
                    if (InfiniteHeight.isOre(state)) {
                        template.put((relY << 8) | (x << 4) | z, state);
                    }
                }
            }
        }
        return template;
    }
}