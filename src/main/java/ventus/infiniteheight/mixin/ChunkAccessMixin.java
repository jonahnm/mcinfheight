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
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;

import ventus.infiniteheight.InfiniteHeight;
import ventus.infiniteheight.SparseColumnHolder;
import ventus.infiniteheight.SparseColumns;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements SparseColumnHolder {
    @Shadow
    @Final
    protected LevelChunkSection[] sections;

    @Unique
    private final SparseColumns infiniteheight$columns = new SparseColumns();

    @Override
    public SparseColumns infiniteheight$columns() {
        return this.infiniteheight$columns;
    }

    @Override
    public void infiniteheight$importNativeSections() {
        if (this.sections == null) {
            return;
        }
        for (int i = 0; i < this.sections.length; i++) {
            if (this.sections[i] != null) {
                this.infiniteheight$columns.put(this.infiniteheight$sectionY(i), this.sections[i]);
            }
        }
    }

    @Unique
    private int infiniteheight$sectionY(int sectionIndex) {
        return ((LevelHeightAccessor) this).getSectionYFromSectionIndex(sectionIndex);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void infiniteheight$initColumns(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory containerFactory,
        long inhabitedTime,
        LevelChunkSection[] sections,
        BlendingData blendingData,
        CallbackInfo ci
    ) {
        this.infiniteheight$columns.setFactory(containerFactory);
        ChunkAccess self = (ChunkAccess) (Object) this;
        if (self.getLevel() != null) {
            this.infiniteheight$columns.setFactory(self.getLevel().palettedContainerFactory());
        }
        if (this.sections != null) {
            for (int i = 0; i < this.sections.length; i++) {
                if (this.sections[i] != null) {
                    this.infiniteheight$columns.put(this.infiniteheight$sectionY(i), this.sections[i]);
                }
            }
        }
    }

    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$sparseSection(int sectionIndex, CallbackInfoReturnable<LevelChunkSection> cir) {
        int sectionY = this.infiniteheight$sectionY(sectionIndex);
        LevelChunkSection section = this.infiniteheight$columns.get(sectionY);
        if (this.sections != null && sectionIndex >= 0 && sectionIndex < this.sections.length && this.sections[sectionIndex] != null) {
            if (section == null || (section.hasOnlyAir() && !this.sections[sectionIndex].hasOnlyAir())) {
                section = this.sections[sectionIndex];
                this.infiniteheight$columns.put(sectionY, section);
            }
        }
        if (section == null) {
            section = this.infiniteheight$columns.getOrCreate(sectionY);
        }
        if (this.sections != null && sectionIndex >= 0 && sectionIndex < this.sections.length) {
            this.sections[sectionIndex] = section;
        }
        cir.setReturnValue(section);
    }

    @Inject(method = "getSections", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$syncSections(CallbackInfoReturnable<LevelChunkSection[]> cir) {
        if (this.sections != null) {
            for (int i = 0; i < this.sections.length; i++) {
                LevelChunkSection section = this.infiniteheight$columns.get(this.infiniteheight$sectionY(i));
                if (section != null) {
                    this.sections[i] = section;
                }
            }
        }
        cir.setReturnValue(this.sections);
    }

    @Inject(method = "getHighestFilledSectionIndex", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$highestFilled(CallbackInfoReturnable<Integer> cir) {
        int highest = Integer.MIN_VALUE;
        LevelHeightAccessor height = (LevelHeightAccessor) this;
        for (var entry : this.infiniteheight$columns.all().int2ObjectEntrySet()) {
            if (!entry.getValue().hasOnlyAir()) {
                int index = height.getSectionIndexFromSectionY(entry.getIntKey());
                if (this.sections == null || (index >= 0 && index < this.sections.length)) {
                    highest = Math.max(highest, index);
                }
            }
        }
        cir.setReturnValue(highest == Integer.MIN_VALUE ? -1 : highest);
    }

    @Inject(method = "getNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$sparseBiome(int quartX, int quartY, int quartZ, CallbackInfoReturnable<Holder<Biome>> cir) {
        int sectionY = QuartPos.toSection(quartY);
        LevelChunkSection section = this.infiniteheight$columns.getOrCreate(sectionY);
        cir.setReturnValue(section.getNoiseBiome(quartX & 3, quartY & 3, quartZ & 3));
    }

    @Inject(method = "fillBiomesFromNoise", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$fillWindowBiomes(BiomeResolver biomeResolver, CallbackInfo ci) {
        ChunkAccess self = (ChunkAccess) (Object) this;
        ChunkPos pos = self.getPos();
        int quartMinX = QuartPos.fromBlock(pos.getMinBlockX());
        int quartMinZ = QuartPos.fromBlock(pos.getMinBlockZ());
        net.minecraft.world.level.levelgen.NoiseSettings tile = InfiniteHeight.activeTile();
        int minSectionY = self.getHeightAccessorForGeneration().getMinSectionY();
        int maxSectionY = self.getHeightAccessorForGeneration().getMaxSectionY();
        if (tile != null) {
            minSectionY = net.minecraft.core.SectionPos.blockToSectionCoord(tile.minY());
            maxSectionY = net.minecraft.core.SectionPos.blockToSectionCoord(tile.minY() + tile.height() - 1);
        }
        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            LevelChunkSection section = this.infiniteheight$columns.getOrCreate(sectionY);
            section.fillBiomesFromNoise(biomeResolver, quartMinX, QuartPos.fromSection(sectionY), quartMinZ);
        }
        ci.cancel();
    }

    @Inject(method = "collectBiomesInPalette", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$collectSparseBiomes(java.util.Set<Holder<Biome>> output, CallbackInfo ci) {
        for (LevelChunkSection section : this.infiniteheight$columns.all().values()) {
            section.getBiomes().forEachInPalette(output::add);
        }
        ci.cancel();
    }

    @Inject(method = "getOrCreateOffsetList", at = @At("HEAD"), cancellable = true)
    private static void infiniteheight$safeOffsetList(ShortList[] list, int sectionIndex, CallbackInfoReturnable<ShortList> cir) {
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
}
