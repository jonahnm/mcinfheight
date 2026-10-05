package ventus.infiniteheight;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Chunk-local bookkeeping that vanilla has no place for.
 *
 * <p>Sections live in vanilla's native {@code sections} array for every section inside the
 * dimension; this map only holds sections pushed outside that range, so it stays near empty and
 * never duplicates native sections.
 */
public final class SparseColumns {
    private final Int2ObjectMap<LevelChunkSection> sections = new Int2ObjectOpenHashMap<>();
    private final IntSet generatedTiles = new IntOpenHashSet();
    private Int2ObjectMap<BlockState> oreTemplate;
    private Registry<Biome> biomes;

    public void setBiomes(Registry<Biome> biomes) {
        if (biomes != null) {
            this.biomes = biomes;
        }
    }

    public boolean factoryPresent() {
        return this.biomes != null;
    }

    public void merge(SparseColumns other) {
        if (other.biomes != null) {
            this.biomes = other.biomes;
        }
        this.generatedTiles.addAll(other.generatedTiles);
        this.sections.putAll(other.sections);
        if (this.oreTemplate == null) {
            this.oreTemplate = other.oreTemplate;
        }
    }

    public LevelChunkSection get(int sectionY) {
        return this.sections.get(sectionY);
    }

    public LevelChunkSection getOrCreate(int sectionY) {
        LevelChunkSection section = this.sections.get(sectionY);
        if (section == null) {
            section = createSection();
            this.sections.put(sectionY, section);
        }
        return section;
    }

    public Int2ObjectMap<BlockState> oreTemplate() {
        return this.oreTemplate;
    }

    public void setOreTemplate(Int2ObjectMap<BlockState> oreTemplate) {
        this.oreTemplate = oreTemplate;
    }

    public boolean hasTile(int tileOrigin) {
        return this.generatedTiles.contains(tileOrigin);
    }

    public void markTile(int tileOrigin) {
        this.generatedTiles.add(tileOrigin);
    }

    public static SparseColumns of(ChunkAccess chunk) {
        SparseColumns columns = ((SparseColumnHolder) chunk).infiniteheight$columns();
        if (!columns.factoryPresent() && chunk.getLevel() != null) {
            columns.setBiomes(
                chunk.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
            );
        }
        return columns;
    }

    private LevelChunkSection createSection() {
        if (this.biomes == null) {
            throw new IllegalStateException("Biome registry is missing; cannot create chunk section");
        }
        return new LevelChunkSection(this.biomes);
    }

    public static int sectionYOf(int blockY) {
        return SectionPos.blockToSectionCoord(blockY);
    }
}