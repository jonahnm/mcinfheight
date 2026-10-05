# Infinite Height — 1.21.1 backport

> **Status: partial.** See [What works](#what-works) and [What is not ported](#what-is-not-ported).
> The 26.3 line lives on `main`.

NeoForge 21.1.x (Minecraft 1.21.1, Java 21) branch of the Infinite Height mod.

## What works

- **Widened BlockPos Y packing.** `BlockPosMixin` lowers the horizontal packing bound from
  `30000000` to `2000000` via `@ModifyConstant` on `<clinit>`. Vanilla keeps 26 bits per
  horizontal axis (12-bit Y); this drops each to 22, giving **20-bit Y: -524272 to 524271**.
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
- **Biomes outside the column.** `fillBiomesFromNoise` is replaced to fill sparse sections from
  -2048 to 2048, wrapping quart Y back into the chunk's own biome range.
- **No bedrock** is written (`LevelChunkSectionMixin` swaps it for deepslate).

Dimensions are intentionally left at vanilla height, so vanilla generation is untouched.

## What is not ported

Terrain generation outside the vanilla column — the deep tiles to -2048 with caves, ores and
copied biomes that exist on `main` — **is not implemented on this branch.** 1.21.1 cannot simply
use a taller dimension: the noise chunk is created and cached by the chunk
(`ChunkAccess.getOrCreateNoiseChunk`), and its fill loop iterates the chunk's declared height
using interpolated noise cells, so a raised build height produces garbage rather than stretched
terrain. Porting this needs a self-driven tile generator rather than an adaptation of vanilla's.

Also absent, because the classes/hooks moved or were removed in 1.21.1:

| 26.3 mixin | Why it is gone |
| --- | --- |
| `NoiseBasedChunkGeneratorMixin` | `createNoiseChunk` takes no `NoiseSettings`; no `generateCarvers`; `buildSurface` reshaped |
| `WorldGenerationContextMixin` | still viable, but only needed for deep-tile carvers |
| `AquiferMixin` | `computeSubstance` now takes a `DensityFunction.FunctionContext`, not `(x, y, z, density)` |
| `HeightRangePlacementMixin` | 1.21.1 placement modifiers are `Stream`-based, not callback-based |
| `SerializableChunkDataMixin` | `SerializableChunkData` does not exist in 1.21.1 |
| `ChunkGeneratorMixin` | `doCreateBiomes` is private on `NoiseBasedChunkGenerator`, not on `ChunkGenerator` |
| `ProtoChunkMixin` | Y-shift redirects only make sense with deep tiles |
| `WorldBorderMixin` | constructor shape differs; border limit not reapplied |

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