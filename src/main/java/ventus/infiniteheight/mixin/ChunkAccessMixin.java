package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;

import ventus.infiniteheight.SparseColumnHolder;
import ventus.infiniteheight.SparseColumns;

/**
 * Vanilla already materialises every section inside the dimension (the ctor calls
 * {@code replaceMissingSections}) and already fills biomes across the whole range, so none of that
 * is reimplemented here. These hooks only widen the lookups to sections outside the dimension,
 * and they deliberately take the native array fast path first: vanilla calls {@code getSection}
 * for essentially every block access, so that path must stay a plain array read.
 */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements SparseColumnHolder {
    @Shadow
    @Final
    protected LevelChunkSection[] sections;

    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Unique
    private final SparseColumns infiniteheight$columns = new SparseColumns();

    @Override
    public SparseColumns infiniteheight$columns() {
        return this.infiniteheight$columns;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void infiniteheight$initColumns(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        Registry<Biome> biomes,
        long inhabitedTime,
        LevelChunkSection[] sections,
        BlendingData blendingData,
        CallbackInfo ci
    ) {
        this.infiniteheight$columns.setBiomes(biomes);
        ChunkAccess self = (ChunkAccess) (Object) this;
        if (self.getLevel() != null) {
            this.infiniteheight$columns.setBiomes(
                self.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
            );
        }
    }

    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$sparseSection(int sectionIndex, CallbackInfoReturnable<LevelChunkSection> cir) {
        int sectionY = sectionIndex + this.levelHeightAccessor.getMinSection();
        if (this.infiniteheight$inNativeRange(sectionY)) {
            // Inside the dimension vanilla reads the array directly; do no map work at all.
            return;
        }
        LevelChunkSection section = this.infiniteheight$columns.get(sectionY);
        if (section == null && this.infiniteheight$columns.factoryPresent()) {
            section = this.infiniteheight$columns.getOrCreate(sectionY);
        }
        cir.setReturnValue(section);
    }

    @Inject(method = "getOrCreateOffsetList", at = @At("HEAD"), cancellable = true)
    private static void infiniteheight$safeOffsetList(
        ShortList[] list,
        int sectionIndex,
        CallbackInfoReturnable<ShortList> cir
    ) {
        if (list == null || sectionIndex < 0 || sectionIndex >= list.length) {
            cir.setReturnValue(new ShortArrayList());
            return;
        }
        ShortList result = list[sectionIndex];
        if (result == null) {
            result = new ShortArrayList();
            list[sectionIndex] = result;
        }
        cir.setReturnValue(result);
    }

    @Unique
    private boolean infiniteheight$inNativeRange(int sectionY) {
        return this.sections != null
            && sectionY >= this.levelHeightAccessor.getMinSection()
            && sectionY < this.levelHeightAccessor.getMaxSection();
    }
}