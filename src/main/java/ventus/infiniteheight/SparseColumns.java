package ventus.infiniteheight;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class SparseColumns {
    private final Int2ObjectMap<LevelChunkSection> sections = new Int2ObjectOpenHashMap<>();
    private final IntSet generatedTiles = new IntOpenHashSet();
    private Registry<Biome> biomes;

    public void setBiomes(Registry<Biome> biomes) {
        if (biomes != null) {
            this.biomes = biomes;
        }
    }

    public boolean factoryPresent() {
        return this.biomes != null;
    }

    public LevelChunkSection firstExisting() {
        for (LevelChunkSection section : this.sections.values()) {
            return section;
        }
        return null;
    }

    public void merge(SparseColumns other) {
        if (other.biomes != null) {
            this.biomes = other.biomes;
        }
        this.generatedTiles.addAll(other.generatedTiles);
        this.sections.putAll(other.sections);
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

    public void put(int sectionY, LevelChunkSection section) {
        this.sections.put(sectionY, section);
    }

    public Int2ObjectMap<LevelChunkSection> all() {
        return this.sections;
    }

    public IntSet generatedTiles() {
        return this.generatedTiles;
    }

    public boolean hasTile(int tileOrigin) {
        return this.generatedTiles.contains(tileOrigin);
    }

    public void markTile(int tileOrigin) {
        this.generatedTiles.add(tileOrigin);
    }

    public static SparseColumns of(ChunkAccess chunk) {
        SparseColumns columns = ((SparseColumnHolder) chunk).infiniteheight$columns();
        if (chunk.getLevel() != null) {
            columns.setBiomes(chunk.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME));
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