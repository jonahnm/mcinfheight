package ventus.infiniteheight;

import java.util.Set;
import java.util.function.ObjIntConsumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
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

    public static final int MIN_Y = -524272;
    public static final int MAX_Y = 524271;
    public static final int WORLD_BORDER = 300000;
    public static final int RENDER_SECTION_WINDOW = 32;
    public static final int LIGHT_SECTION_WINDOW = 40;
    public static final int DEEP_FLOOR = -2048;

    private static final ThreadLocal<Integer> FILL_Y_OFFSET = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<NoiseSettings> ACTIVE_TILE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DEEP_CONTINUATION = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> CARVER_PASS = ThreadLocal.withInitial(() -> false);
    private static volatile int FOCUS_Y = 64;

    public InfiniteHeight(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onLevelTick);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Infinite Height: sparse columns, 20-bit packed Y ({} to {}), deep floor {}", MIN_Y, MAX_Y, DEEP_FLOOR);
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Player player = level.getNearestPlayer(0.0, focusY(), 0.0, -1.0, false);
            if (player == null && !level.players().isEmpty()) {
                player = level.players().getFirst();
            }
            if (player != null) {
                setFocusY(player.getBlockY());
                ensureAround(level, player);
            }
        }
    }

    public static void setFocusY(int y) {
        FOCUS_Y = y;
    }

    public static int focusY() {
        return FOCUS_Y;
    }

    public static int dummyMinY() {
        return -512;
    }

    public static int dummyHeight() {
        return 1024;
    }

    public static int windowMinY() {
        return dummyMinY();
    }

    public static int windowMaxY() {
        return dummyMinY() + dummyHeight() - 1;
    }

    public static int windowHeight() {
        return dummyHeight();
    }

    public static int lightMinSectionY() {
        return SectionPos.blockToSectionCoord(FOCUS_Y) - LIGHT_SECTION_WINDOW;
    }

    public static int lightSectionCount() {
        return LIGHT_SECTION_WINDOW * 2 + 2;
    }

    public static void beginTile(NoiseSettings tile, int worldMinY) {
        beginTile(tile, worldMinY, worldMinY < tile.minY());
    }

    public static void beginTile(NoiseSettings tile, int worldMinY, boolean deepContinuation) {
        ACTIVE_TILE.set(tile);
        FILL_Y_OFFSET.set(worldMinY - tile.minY());
        DEEP_CONTINUATION.set(deepContinuation);
    }

    public static void endTile() {
        FILL_Y_OFFSET.set(0);
        CARVER_PASS.set(false);
        ACTIVE_TILE.remove();
        DEEP_CONTINUATION.set(false);
    }

    public static boolean isDeepContinuation() {
        return DEEP_CONTINUATION.get();
    }

    public static boolean inCarverPass() {
        return CARVER_PASS.get();
    }

    public static void setCarverPass(boolean carverPass) {
        CARVER_PASS.set(carverPass);
    }

    public static NoiseSettings deepSlice(NoiseSettings tile) {
        int slice = Math.min(64, tile.height());
        return NoiseSettings.create(tile.minY(), slice);
    }

    public static BlockState withoutBedrock(BlockState state) {
        return state.is(Blocks.BEDROCK) ? Blocks.DEEPSLATE.defaultBlockState() : state;
    }

    public static int fillYOffset() {
        return FILL_Y_OFFSET.get();
    }

    public static int activeWorldMinY() {
        NoiseSettings tile = ACTIVE_TILE.get();
        return tile == null ? 0 : tile.minY() + FILL_Y_OFFSET.get();
    }

    public static NoiseSettings activeTile() {
        return ACTIVE_TILE.get();
    }

    public static boolean isVanillaTileY(int blockY) {
        NoiseSettings tile = ACTIVE_TILE.get();
        return tile != null && blockY >= tile.minY() && blockY < tile.minY() + tile.height();
    }

    public static BlockPos shiftIfNeeded(BlockPos pos) {
        int offset = fillYOffset();
        if (offset == 0 || !isVanillaTileY(pos.getY())) {
            return pos;
        }
        return pos.atY(pos.getY() + offset);
    }

    public static int wrapQuartY(int quartY, NoiseSettings tile) {
        int quartMin = QuartPos.fromBlock(tile.minY());
        int quartHeight = Math.max(1, QuartPos.fromBlock(tile.height()));
        if (quartY < quartMin) {
            int slice = Math.max(1, QuartPos.fromBlock(Math.min(64, tile.height())));
            return quartMin + Math.floorMod(quartY - quartMin, slice);
        }
        return Math.floorMod(quartY - quartMin, quartHeight) + quartMin;
    }

    public static NoiseSettings tileSettings(Holder<NoiseGeneratorSettings> settings) {
        return settings.value().noiseSettings();
    }

    public static int tileOrigin(int blockY, NoiseSettings tile) {
        if (blockY < tile.minY()) {
            int slice = Math.min(64, tile.height());
            return tile.minY() + Math.floorDiv(blockY - tile.minY(), slice) * slice;
        }
        int relative = blockY - tile.minY();
        return tile.minY() + Math.floorDiv(relative, tile.height()) * tile.height();
    }

    public static void forEachTile(NoiseSettings tile, int minBlockY, int maxBlockY, ObjIntConsumer<NoiseSettings> consumer) {
        if (tile.height() <= 0) {
            return;
        }
        int origin = tileOrigin(minBlockY, tile);
        while (origin <= maxBlockY) {
            consumer.accept(tile, origin);
            origin += tile.height();
        }
    }

    public static void ensureAround(ServerLevel level, Player player) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator) || !(generator instanceof TileFiller filler)) {
            return;
        }
        NoiseSettings tile = noiseGenerator.generatorSettings().value().noiseSettings();
        if (player.getBlockY() >= tile.minY()) {
            return;
        }
        ChunkAccess chunk = level.getChunk(
            SectionPos.blockToSectionCoord(player.getBlockX()),
            SectionPos.blockToSectionCoord(player.getBlockZ())
        );
        if (!(chunk instanceof LevelChunk levelChunk)) {
            return;
        }
        int origin = tileOrigin(player.getBlockY(), tile);
        int step = deepSlice(tile).height();
        SparseColumns columns = SparseColumns.of(chunk);
        boolean filled = false;
        for (int nearby = origin - step; nearby <= origin + step; nearby += step) {
            if (nearby >= tile.minY() || nearby < DEEP_FLOOR || columns.hasTile(nearby)) {
                continue;
            }
            columns.markTile(nearby);
            filler.infiniteheight$fillTile(chunk, deepSlice(tile), nearby, level, Set.of());
            filled = true;
        }
        if (filled) {
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(levelChunk, level.getLightEngine(), null, null);
            for (ServerPlayer watcher : level.getChunkSource().chunkMap.getPlayers(levelChunk.getPos(), false)) {
                watcher.connection.send(packet);
            }
        }
    }
}