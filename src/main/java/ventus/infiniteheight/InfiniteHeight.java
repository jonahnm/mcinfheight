package ventus.infiniteheight;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(InfiniteHeight.MOD_ID)
public class InfiniteHeight {
    public static final String MOD_ID = "infiniteheight";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Widened BlockPos packing: horizontal bound lowered so Y gains 8 bits (12 -> 20). */
    public static final int MIN_Y = -524272;
    public static final int MAX_Y = 524271;

    /** Vertical span covered by sparse biome fill. */
    public static final int DEEP_FLOOR = -2048;
    public static final int CEILING = 2048;

    /** Number of chunk sections the client render window covers vertically. */
    public static final int RENDER_SECTION_WINDOW = 33;

    public InfiniteHeight(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info(
            "Infinite Height (1.21.1): sparse columns, 20-bit packed Y ({} to {}), render window {} sections",
            MIN_Y,
            MAX_Y,
            RENDER_SECTION_WINDOW
        );
    }

    public static int renderSectionWindow() {
        return RENDER_SECTION_WINDOW;
    }

    public static BlockState withoutBedrock(BlockState state) {
        return state.is(Blocks.BEDROCK) ? Blocks.DEEPSLATE.defaultBlockState() : state;
    }
}