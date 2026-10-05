# Infinite Height

NeoForge 26.3 mod that makes vertical Minecraft effectively unbounded.

Vanilla's 12-bit packed Y and fixed per-chunk section arrays are replaced with:

- widened 20-bit packed Y (`-524272` to `524271`), using vanilla's own packing layout
- sparse per-column section maps, so chunks only store sections that exist
- a sliding render window that follows the camera vertically
- vanilla Overworld / Nether / End terrain, continued as you climb or dig

Below `-64` generation continues as deepslate with caves, ores and biomes, down to `-2048`,
with no bedrock and no void damage. Generation above and below the vanilla column is
stretched on demand as you move, so chunks stay cheap.

True mathematical infinity is impossible on a finite machine. This is the real
unlimited-column approach: you can keep going and the world keeps generating.

## Requirements

- Minecraft 26.3
- NeoForge 26.3.0.48-beta or newer

## Usage

Create a **new world**. Existing worlds keep their old generated chunks.

Dug-out range is generated as you move; nothing is pre-generated far below spawn.

## Run

```bash
./gradlew runClient
```

## Build

```bash
./gradlew build
```

Jar: `build/libs/infiniteheight-1.0.0.jar`.

## How it works

| Area | Change |
| --- | --- |
| `BlockPos` | `@ModifyConstant` on the horizontal packing bound, widening Y from 12 to 20 bits |
| `ChunkAccess` | sparse `Int2ObjectMap<LevelChunkSection>` keyed by section Y, synced with the native section array |
| `NoiseBasedChunkGenerator` | generates the vanilla column plus one deep tile at load, then more tiles on demand |
| `WorldGenerationContext` | clamps the generation range to the active tile so carvers and the material rule work below `-64` |
| `Aquifer.NoiseBasedAquifer` | shifts carver Y back into the sampled tile so the aquifer cache stays in range |
| `RotatingSectionStorage` | slides the render window vertically instead of covering the whole column |
| `SerializableChunkData` | section Y written as an int instead of a byte |

### Mixin notes

- Do not `@Shadow` `ChunkAccess` fields/methods inherited from `LevelChunk`, and do not
  `@Shadow` interface defaults such as `LevelHeightAccessor.getSectionYFromSectionIndex`.
- Do not `@Redirect` record accessors such as `SerializableChunkData.SectionData.y()`.
- `@Inject` at `HEAD` of a constructor must be `static`.
- Vanilla post-processing is a fixed `ShortList[]`; extra tiles overflow it unless the
  index is bounds-checked.