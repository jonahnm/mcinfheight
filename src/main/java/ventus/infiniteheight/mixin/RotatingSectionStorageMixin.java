package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.core.SectionPos;

import ventus.infiniteheight.InfiniteHeight;

@Mixin(RotatingSectionStorage.class)
public abstract class RotatingSectionStorageMixin<T extends RotatingSectionStorage.Value> {
    @Shadow
    @Final
    @Mutable
    private int minY;

    @Shadow
    @Final
    @Mutable
    private int maxY;

    @Shadow
    @Final
    private int radius;

    @Shadow
    @Final
    private int sectionGridSizeY;

    @Shadow
    @Final
    private int sectionGridSizeXZ;

    @Shadow
    private SectionPos centerSectionPos;

    @Shadow
    @Final
    private RotatingSectionStorage.Node<T>[] nodes;

    @Shadow
    private int getSectionIndex(int x, int y, int z) {
        throw new AssertionError();
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static int infiniteheight$windowMinY(int minY, int radius, int originalMinY, int originalMaxY) {
        int height = originalMaxY - originalMinY + 1;
        int window = InfiniteHeight.RENDER_SECTION_WINDOW * 2 + 1;
        return height <= window ? minY : 0;
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static int infiniteheight$windowMaxY(int maxY, int radius, int originalMinY, int originalMaxY) {
        int height = originalMaxY - originalMinY + 1;
        int window = InfiniteHeight.RENDER_SECTION_WINDOW * 2 + 1;
        return height <= window ? maxY : window - 1;
    }

    @Inject(method = "repositionCenter", at = @At("HEAD"))
    private void infiniteheight$slideY(SectionPos newCenterSectionPos, CallbackInfoReturnable<Boolean> cir) {
        int half = this.sectionGridSizeY / 2;
        int margin = Math.max(4, half / 4);
        if (newCenterSectionPos.y() >= this.minY + margin && newCenterSectionPos.y() <= this.maxY - margin) {
            return;
        }
        int newMinY = newCenterSectionPos.y() - half;
        int newMaxY = newMinY + this.sectionGridSizeY - 1;
        if (newMinY == this.minY && newMaxY == this.maxY) {
            return;
        }
        this.minY = newMinY;
        this.maxY = newMaxY;
    }
}
