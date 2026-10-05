package ventus.infiniteheight;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(InfiniteHeight.MOD_ID)
public class InfiniteHeight {
    public static final String MOD_ID = "infiniteheight";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Widened BlockPos packing: horizontal bound lowered so Y gains 8 bits (12 -> 20). */
    public static final int MIN_Y = -524272;
    public static final int MAX_Y = 524271;

    /** Height of one generated deep tile, matching the bottom slice of the vanilla column. */
    public static final int DEEP_SLICE = 64;

    /** Lowest generated block Y. */
    public static final int DEEP_FLOOR = -2048;

    /** Highest Y covered by sparse biome fill. */
    public static final int CEILING = 2048;

    /** Number of chunk sections the client render window covers vertically. */
    public static final int RENDER_SECTION_WINDOW = 33;

    /** Only read, never mutated, so a single instance is safe to share across packets. */
    private static final java.util.BitSet NO_LIGHT_UPDATES = new java.util.BitSet();

    public InfiniteHeight(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onLevelTick);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info(
            "Infinite Height (1.21.1): 20-bit packed Y ({} to {}), deep tiles to {}, render window {} sections",
            MIN_Y,
            MAX_Y,
            DEEP_FLOOR,
            RENDER_SECTION_WINDOW
        );
    }

    public static int renderSectionWindow() {
        return RENDER_SECTION_WINDOW;
    }

    public static BlockState withoutBedrock(BlockState state) {
        return state.is(Blocks.BEDROCK) ? Blocks.DEEPSLATE.defaultBlockState() : state;
    }

    /** 1.21.1 has no aggregate {@code minecraft:ores} tag, so the per-ore tags are listed here. */
    public static boolean isOre(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.COAL_ORES)
            || state.is(net.minecraft.tags.BlockTags.IRON_ORES)
            || state.is(net.minecraft.tags.BlockTags.COPPER_ORES)
            || state.is(net.minecraft.tags.BlockTags.GOLD_ORES)
            || state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)
            || state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)
            || state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)
            || state.is(net.minecraft.tags.BlockTags.EMERALD_ORES);
    }

    /** Tile origin for a deep tile containing {@code blockY}. */
    public static int deepTileOrigin(int blockY, int vanillaMinY) {
        int relative = blockY - vanillaMinY;
        return vanillaMinY + Math.floorDiv(relative, DEEP_SLICE) * DEEP_SLICE;
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                ensureAround(level, player);
            }
        }
    }

    /**
     * Generates the deep tiles around a player who has descended below the vanilla column, then
     * resends the chunk so the client receives the newly filled sections.
     */
    public static void ensureAround(ServerLevel level, Player player) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof DeepTileFiller filler)) {
            return;
        }
        NoiseSettings vanilla = filler.infiniteheight$vanillaTile();
        if (vanilla.height() <= 0 || player.getBlockY() >= vanilla.minY()) {
            // Common case: above the vanilla column, nothing to do and no chunk lookup.
            return;
        }
        LevelChunk chunk = level.getChunkSource().getChunk(
            SectionPos.blockToSectionCoord(player.getBlockX()),
            SectionPos.blockToSectionCoord(player.getBlockZ()),
            false
        );
        if (chunk == null || chunk.getMinBuildHeight() >= vanilla.minY()) {
            // Dimension does not extend below the vanilla column (e.g. the Nether), so a deep tile
            // would fall outside the chunk's sections and never reach the client.
            return;
        }
        SparseColumns columns = SparseColumns.of(chunk);
        int origin = deepTileOrigin(player.getBlockY(), vanilla.minY());
        boolean filled = false;
        for (int tile = origin - DEEP_SLICE; tile <= origin + DEEP_SLICE; tile += DEEP_SLICE) {
            if (tile >= vanilla.minY() || tile < DEEP_FLOOR || columns.hasTile(tile)) {
                continue;
            }
            columns.markTile(tile);
            filler.infiniteheight$fillDeepTile(chunk, tile, level);
            filled = true;
        }
        if (filled) {
            // Empty masks, not null: null would make the packet re-serialise light for every one
            // of the dimension's light sections (258 for a 4096-tall world, most of it sky light
            // for empty air above the surface). The column's existing light is already correct on
            // the client, and the new deep sections are unlit by design like any other cave.
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                chunk,
                level.getLightEngine(),
                NO_LIGHT_UPDATES,
                NO_LIGHT_UPDATES
            );
            for (ServerPlayer watcher : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)) {
                watcher.connection.send(packet);
            }
        }
    }
}