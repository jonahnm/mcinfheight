package ventus.infiniteheight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseSettings;

/** Implemented by the chunk generator so the mod can drive generation outside the vanilla column. */
public interface DeepTileFiller {
    /** The generator's own noise column, i.e. the range vanilla fills. */
    NoiseSettings infiniteheight$vanillaTile();

    /** Generates a 64-block deep tile whose bottom sits at {@code worldMinY}. */
    void infiniteheight$fillDeepTile(ChunkAccess chunk, int worldMinY, ServerLevel level);
}