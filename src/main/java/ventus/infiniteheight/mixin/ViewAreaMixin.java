package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.world.level.Level;

import ventus.infiniteheight.InfiniteHeight;

/**
 * 1.21.1 replaced {@code RotatingSectionStorage} with {@link ViewArea}, which sizes its section grid
 * from the level height and only ever repositions on X/Z. For unbounded Y we cap the vertical grid
 * to a sliding window and re-centre it on the camera.
 */
@Mixin(ViewArea.class)
public abstract class ViewAreaMixin {
    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected int sectionGridSizeY;

    @Unique
    private int infiniteheight$baseSection = Integer.MIN_VALUE;

    @Redirect(
        method = "createSections",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I")
    )
    private int infiniteheight$createBase(Level level) {
        return this.infiniteheight$baseBlockY();
    }

    @Redirect(
        method = "repositionCamera",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I")
    )
    private int infiniteheight$repositionBase(Level level) {
        this.infiniteheight$slideToCamera();
        return this.infiniteheight$baseBlockY();
    }

    @Redirect(
        method = "setDirty",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinSection()I")
    )
    private int infiniteheight$dirtyBaseSection(Level level) {
        return this.infiniteheight$baseSection();
    }

    @Redirect(
        method = "getRenderSectionAt",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I")
    )
    private int infiniteheight$lookupBase(Level level) {
        return this.infiniteheight$baseBlockY();
    }

    @Inject(method = "setViewDistance", at = @At("RETURN"))
    private void infiniteheight$capVerticalGrid(int viewDistance, CallbackInfo ci) {
        this.sectionGridSizeY = InfiniteHeight.renderSectionWindow();
        this.infiniteheight$baseSection = this.infiniteheight$cameraSection() - this.sectionGridSizeY / 2;
    }

    @Unique
    private void infiniteheight$slideToCamera() {
        int window = this.sectionGridSizeY;
        if (window <= 0) {
            return;
        }
        int camera = this.infiniteheight$cameraSection();
        int margin = Math.max(2, window / 4);
        int base = this.infiniteheight$baseSection();
        if (camera >= base + margin && camera <= base + window - margin) {
            return;
        }
        this.infiniteheight$baseSection = camera - window / 2;
    }

    @Unique
    private int infiniteheight$baseSection() {
        if (this.infiniteheight$baseSection == Integer.MIN_VALUE) {
            this.infiniteheight$baseSection = this.infiniteheight$cameraSection() - Math.max(1, this.sectionGridSizeY) / 2;
        }
        return this.infiniteheight$baseSection;
    }

    @Unique
    private int infiniteheight$baseBlockY() {
        return this.infiniteheight$baseSection() << 4;
    }

    @Unique
    private int infiniteheight$cameraSection() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return this.level.getMinSection();
        }
        return net.minecraft.core.SectionPos.blockToSectionCoord(
            net.minecraft.core.BlockPos.containing(minecraft.gameRenderer.getMainCamera().getPosition()).getY()
        );
    }
}