package ventus.infiniteheight;

import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseSettings;

public interface TileFiller {
    void infiniteheight$fillTile(ChunkAccess chunk, NoiseSettings tile, int worldMinY, ServerLevel level, Set<Holder<Biome>> biomes);
}
