package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.core.BlockPos;

@Mixin(BlockPos.class)
public abstract class BlockPosMixin {
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 30000000))
    private static int infiniteheight$widerY(int original) {
        return 2_000_000;
    }
}
