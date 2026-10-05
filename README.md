# Infinite Height — 1.21.1 backport

> **Status: partial.** See [What works](#what-works) and [What is not ported](#what-is-not-ported).
> The 26.3 line lives on `main`.

NeoForge 21.1.x (Minecraft 1.21.1, Java 21) branch of the Infinite Height mod.

## What works

- **Widened BlockPos Y packing.** `BlockPosMixin` lowers the horizontal packing bound from
  `30000000` to `2000000` via `@ModifyConstant` on `<clinit>`. Vanilla keeps 26 bits per
  horizontal axis (12-bit Y); this drops each to 22, giving **20-bit Y: -524272 to 524271**.
- **Generation below -64.** Dimensions are raised to `min_y -2048 / height 4096`, so deep sections
  live inside the chunk's `sections` array and therefore reach the client in the normal chunk
  packet. Vanilla generation is unaffected: `fillFromNoise` calls
  `clampToHeightAccessor`, which pins it to the noise settings' own 384 blocks no matter how tall
  the dimension is.
  `NoiseBasedChunkGeneratorMixin` then generates 64-block deep tiles itself by sampling
  `noiseRouter().finalDensity()` pointwise through `DensityFunction.SinglePointContext`:
  density > 0 places deepslate (below Y 0) or stone, otherwise air, giving continuous cave and
  rock strata. The first tile is generated during `doFill`; deeper tiles are generated on demand
  by `InfiniteHeight#ensureAround` when the player descends, after which the chunk is resent so
  the client sees them.
- **Ores below -64.** Vanilla ore decoration only covers the vanilla column, so
  `infiniteheight$copyOres` replicates the ore blocks from the matching layer of the vanilla
  column into every deep tile (1.21.1 has no aggregate `minecraft:ores` tag, so the per-ore tags
  are checked individually).
- **Biomes outside the column.** `fillBiomesFromNoise` is replaced to fill sparse sections from
  -2048 to 2048, wrapping quart Y back into the chunk's own biome range.
- **Sparse chunk columns.** `ChunkAccessMixin` keeps sections in an
  `Int2ObjectMap<LevelChunkSection>` keyed by section Y, so a chunk can hold sections outside the
  vanilla build height without a proportionally sized array. The native `sections` array is kept
  in sync for everything inside the build height.
- **Build and place outside vanilla height.** `LevelHeightAccessorMixin` makes
  `isOutsideBuildHeight` always false, so writes at any Y are accepted; `LevelChunkMixin` routes
  reads for out-of-range section indices to the sparse map, including on proto→level promotion.
- **No void damage.** `EntityMixin` / `LivingEntityMixin` cancel `checkBelowWorld` and
  `onBelowWorld`.
- **Sliding render window.** `ViewAreaMixin` replaces the 26.3 `RotatingSectionStorage` hack:
  1.21.1 uses `ViewArea`, which sizes its grid from level height and only repositions on X/Z.
  The mixin caps the vertical grid to `RENDER_SECTION_WINDOW` (33) sections and re-centres it on
  the camera with a margin, redirecting `Level.getMinBuildHeight()` / `getMinSection()` inside
  `createSections`, `repositionCamera`, `setDirty` and `getRenderSectionAt`.
- **No bedrock** is written (`LevelChunkSectionMixin` swaps it for deepslate).

## Known limitations

- **Deep caves are dry.** The self-driven tile fill uses the sign of the final density only, so
  caves below -64 are air rather than aquifer-filled water/lava. 1.21.1 exposes aquifers only via
  `Aquifer.create(NoiseChunk, ...)`, and the chunk-owned `NoiseChunk` is cached for the vanilla
  column, so reusing it outside that range would read out of its grid. Porting the aquifer means
  constructing one per tile and shifting the `FunctionContext` Y for carvers.
- **Carvers are not run below -64.** Noise caves come from the density field; the large winding
  tunnels produced by `applyCarvers` (which runs as a separate chunk-status step in 1.21.1) are
  absent at depth.
- **Deep terrain repeats every 64 blocks**, because each tile samples the same Y range and shifts
  it. This is inherent to continuing a fixed 384-block noise column downward.
- Ore distribution in deep tiles is a copy of the bottom 64 blocks of the vanilla column, so it
  repeats on the same 64-block period.
- Generation above the vanilla column (Y > 320) is **not** implemented — only downward
  continuation is.

Also absent, because the classes/hooks moved or were removed in 1.21.1:

| 26.3 mixin | Why it is gone |
| --- | --- |
| `NoiseBasedChunkGeneratorMixin` | `createNoiseChunk` takes no `NoiseSettings`; no `generateCarvers`; `buildSurface` reshaped |
| `WorldGenerationContextMixin` | still viable, but only needed for deep-tile carvers |
| `AquiferMixin` | `computeSubstance` now takes a `DensityFunction.FunctionContext`, not `(x, y, z, density)` |
| `HeightRangePlacementMixin` | 1.21.1 placement modifiers are `Stream`-based, not callback-based |
| `ChunkGeneratorMixin` | `doCreateBiomes` is private on `NoiseBasedChunkGenerator`, not on `ChunkGenerator` |
| `ProtoChunkMixin` | Y-shift redirects only make sense with the old tile scheme |
| `WorldBorderMixin` | constructor shape differs; border limit not reapplied |
| `SerializableChunkDataMixin` | needed on 26.3 to widen the section-Y byte; 1.21.1 serialises sections differently |

## Build

```bash
./gradlew build
```

Jar: `build/libs/infiniteheight-1.0.0.jar`. Requires NeoForge 21.1.0+.

## Testing status

**Not run in-game.** This branch is compile-verified only, and every mixin target was checked
against the 1.21.1 bytecode with `javap`. That is not the same as a successful launch — on the
26.3 line nearly every real failure was runtime-only. Expect to shake out crashes on first run in
a 1.21.1 instance.