package ventus.infiniteheight;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

public final class SparseColumns {
    private final Int2ObjectMap<LevelChunkSection> sections = new Int2ObjectOpenHashMap<>();
    private final IntSet generatedTiles = new IntOpenHashSet();
    private PalettedContainerFactory factory;

    public void setFactory(PalettedContainerFactory factory) {
        if (factory != null) {
            this.factory = factory;
        }
    }

    public PalettedContainerFactory factory() {
        return this.factory;
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

    public boolean hasTile(int tileOrigin) {
        return this.generatedTiles.contains(tileOrigin);
    }

    public void markTile(int tileOrigin) {
        this.generatedTiles.add(tileOrigin);
    }

    public void copyFrom(SparseColumns other) {
        if (other.factory != null) {
            this.factory = other.factory;
        }
        this.generatedTiles.addAll(other.generatedTiles);
        this.sections.putAll(other.sections);
    }

    public IntSet generatedTiles() {
        return this.generatedTiles;
    }

    public LevelChunkSection sectionForIndex(ChunkAccess chunk, int index) {
        return getOrCreate(chunk.getSectionYFromSectionIndex(index));
    }

    public static SparseColumns of(ChunkAccess chunk) {
        SparseColumns columns = ((SparseColumnHolder) chunk).infiniteheight$columns();
        Level level = chunk.getLevel();
        if (level != null) {
            columns.setFactory(level.palettedContainerFactory());
        }
        return columns;
    }

    public static int sectionY(ChunkAccess chunk, int blockY) {
        return SectionPos.blockToSectionCoord(blockY);
    }

    private LevelChunkSection createSection() {
        if (this.factory != null) {
            return new LevelChunkSection(this.factory);
        }
        throw new IllegalStateException("Chunk section factory is missing");
    }
}
