# Infinite Height — 1.21.1 backport

> **Status: partial.** See [What works](#what-works) and [Known limitations](#known-limitations).
> The 26.3 line lives on `main`.

NeoForge 21.1.x (Minecraft 1.21.1, Java 21) branch of the Infinite Height mod.

## What works

- **Widened BlockPos Y packing.** `BlockPosMixin` lowers the horizontal packing bound from
  `30000000` to `2000000` via `@ModifyConstant` on `<clinit>`. Vanilla keeps 26 bits per
  horizontal axis (12-bit Y); this drops each to 22, giving **20-bit Y: -524272 to 524271**.
- **Generation below -64.** The overworld and overworld_caves dimension types are raised to
  `min_y -2048 / height 4096`, so deep sections live inside the chunk's `sections` array and reach
  the client in the normal chunk packet. Vanilla generation is unaffected: `fillFromNoise` calls
  `clampToHeightAccessor`, which pins it to the noise settings' own 384 blocks however tall the
  dimension is.

  `NoiseBasedChunkGeneratorMixin` implements `DeepTileFiller` and generates 64-block deep tiles on
  demand: `InfiniteHeight#ensureAround` fills the tiles around a player who has descended below the
  vanilla column, then resends the chunk so the client receives the new sections. Density is
  sampled through `RandomState#router`'s `finalDensity()` on the same coarse cell grid vanilla uses
  (`noiseSizeHorizontal` x `noiseSizeVertical`) and trilinearly interpolated — about 225
  evaluations per tile instead of 16384, which would be ~13x vanilla's whole noise pass for a
  chunk. Density > 0 places deepslate below Y 0 and stone above, otherwise air.

  The Nether and The End are left at vanilla height: deep generation is overworld-only, and
  `ensureAround` refuses to build tiles in a dimension that does not extend below its noise column
  (they would land outside the chunk's sections and never reach the client).
- **Ores below -64.** Vanilla ore decoration only covers the vanilla column, so the ore blocks in
  the bottom 64 blocks of that column are read **once per chunk** into a template and stamped into
  every deep tile. Reading the source column per tile would be 16384 lookups for a few hundred
  ores. 1.21.1 has no aggregate `minecraft:ores` tag, so the per-ore tags are checked individually.
- **Biomes** need no special handling: vanilla already fills biomes across the chunk's whole
  section range, which now covers -2048 to 2048.
- **Sections outside the dimension** are the only sparse case. `ChunkAccess` materialises every
  section inside the dimension itself (`replaceMissingSections`), so `SparseColumns` only holds
  sections pushed past the dimension bounds and doubles as tile/ore-template bookkeeping.
- **Build and place across the whole range.** With the dimension actually spanning -2048 to 2048,
  vanilla's own `isOutsideBuildHeight` now admits that range, so `LevelHeightAccessorMixin` was
  removed rather than forced to always return false. That also stops writes below the Nether's
  floor from creating sections that are never saved or sent. `LevelChunkMixin` only routes reads
  to the sparse map when Y is genuinely outside the dimension.
- **No void damage.** `EntityMixin` / `LivingEntityMixin` cancel `checkBelowWorld` and
  `onBelowWorld`.
- **Sliding render window.** `ViewAreaMixin` replaces the 26.3 `RotatingSectionStorage` hack:
  1.21.1 uses `ViewArea`, which sizes its grid from level height and only repositions on X/Z.
  The mixin caps the vertical grid to `RENDER_SECTION_WINDOW` (33) sections and re-centres it on
  the camera with a margin, redirecting `Level.getMinBuildHeight()` / `getMinSection()` inside
  `createSections`, `repositionCamera`, `setDirty` and `getRenderSectionAt`.
- **No bedrock** is written (`LevelChunkSectionMixin` swaps it for deepslate).

## Known limitations

- **Section count is the remaining cost.** A 4096-tall dimension is 256 sections per chunk against
  vanilla's 24, and vanilla allocates all of them in the chunk ctor. That is ~10x the section
  objects, light sections and chunk-packet parse work on every chunk load, and there is no way
  around it while keeping both -2048 and 2048: the save format stores section Y as a **signed
  byte**, so -128..127 (Y -2048..2048) is the hard limit for vanilla-compatible chunk data.
  Lowering `min_y` or `height` is the lever if this is still too expensive.

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