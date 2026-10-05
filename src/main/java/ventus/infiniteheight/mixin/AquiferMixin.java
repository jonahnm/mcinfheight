package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.level.levelgen.Aquifer;

import ventus.infiniteheight.InfiniteHeight;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class AquiferMixin {
    @ModifyVariable(method = "computeSubstance", at = @At("HEAD"), argsOnly = true, index = 1)
    private int infiniteheight$shiftToTileY(int blockY) {
        if (!InfiniteHeight.inCarverPass()) {
            return blockY;
        }
        int offset = InfiniteHeight.fillYOffset();
        return offset == 0 ? blockY : blockY - offset;
    }
}