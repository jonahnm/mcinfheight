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
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
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

    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

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
        return sectionIndex + this.levelHeightAccessor.getMinSection();
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
            this.infiniteheight$columns.setBiomes(self.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME));
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
        if (section == null) {
            if (this.sections != null && sectionIndex >= 0 && sectionIndex < this.sections.length && this.sections[sectionIndex] != null) {
                section = this.sections[sectionIndex];
            } else if (this.infiniteheight$columns.factoryPresent()) {
                section = this.infiniteheight$columns.getOrCreate(sectionY);
            }
        }
        if (section == null) {
            // No biome registry available yet: fall back to the first existing section if we can.
            section = this.infiniteheight$columns.firstExisting();
        }
        if (section == null) {
            cir.setReturnValue(null);
            return;
        }
        if (this.sections != null && sectionIndex >= 0 && sectionIndex < this.sections.length) {
            this.sections[sectionIndex] = section;
        }
        this.infiniteheight$columns.put(sectionY, section);
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
        int highestSectionY = Integer.MIN_VALUE;
        for (var entry : this.infiniteheight$columns.all().int2ObjectEntrySet()) {
            if (!entry.getValue().hasOnlyAir()) {
                highestSectionY = Math.max(highestSectionY, entry.getIntKey());
            }
        }
        if (highestSectionY == Integer.MIN_VALUE) {
            cir.setReturnValue(-1);
            return;
        }
        cir.setReturnValue(highestSectionY - this.levelHeightAccessor.getMinSection());
    }

    @Inject(method = "getNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$sparseBiome(int quartX, int quartY, int quartZ, CallbackInfoReturnable<Holder<Biome>> cir) {
        int sectionY = QuartPos.toSection(quartY);
        LevelChunkSection section = this.infiniteheight$columns.get(sectionY);
        if (section == null && this.infiniteheight$columns.factoryPresent()) {
            section = this.infiniteheight$columns.getOrCreate(sectionY);
            this.infiniteheight$columns.put(sectionY, section);
        }
        if (section == null) {
            cir.setReturnValue(this.infiniteheight$anyBiome());
            return;
        }
        cir.setReturnValue(section.getNoiseBiome(quartX & 3, quartY & 3, quartZ & 3));
    }

    @Inject(method = "fillBiomesFromNoise", at = @At("HEAD"), cancellable = true)
    private void infiniteheight$fillAllBiomes(BiomeResolver resolver, Climate.Sampler sampler, CallbackInfo ci) {
        ChunkAccess self = (ChunkAccess) (Object) this;
        ChunkPos pos = self.getPos();
        int quartMinX = QuartPos.fromBlock(pos.getMinBlockX());
        int quartMinZ = QuartPos.fromBlock(pos.getMinBlockZ());
        int minQuartY = QuartPos.fromBlock(InfiniteHeight.DEEP_FLOOR);
        int maxQuartY = QuartPos.fromBlock(InfiniteHeight.CEILING);
        int quartMin = QuartPos.fromBlock(this.levelHeightAccessor.getMinBuildHeight());
        int quartMax = QuartPos.fromBlock(this.levelHeightAccessor.getMaxBuildHeight());
        int span = Math.max(1, quartMax - quartMin);
        for (int quartY = minQuartY; quartY <= maxQuartY; quartY++) {
            int sectionY = QuartPos.toSection(quartY);
            LevelChunkSection section = this.infiniteheight$columns.get(sectionY);
            if (section == null) {
                if (!this.infiniteheight$columns.factoryPresent()) {
                    continue;
                }
                section = this.infiniteheight$columns.getOrCreate(sectionY);
                this.infiniteheight$columns.put(sectionY, section);
            }
            int wrapped = quartMin + Math.floorMod(quartY - quartMin, span);
            section.fillBiomesFromNoise(resolver, sampler, quartMinX, wrapped, quartMinZ);
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

    @Unique
    private Holder<Biome> infiniteheight$anyBiome() {
        for (LevelChunkSection section : this.infiniteheight$columns.all().values()) {
            return section.getNoiseBiome(0, 0, 0);
        }
        return null;
    }
}