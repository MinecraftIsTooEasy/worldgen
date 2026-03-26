# WorldGen Structure Generation

Chinese version: `README_WORLDGEN_API_CN.md`

This mod provides two independent entry points:

1. Player config entry: `config/worldgen-structures.jsonc`
2. Developer API entry: `StructureWorldgenApi` / `StructureWorldgenConfig`

These two paths do not affect each other.

## Player Config

On first start, these are auto-created:

- `config/worldgen-structures.jsonc`
- `schematics/` (sibling to `config/`)

Config file format is JSONC (JSON + comments).

### Path rules (`schematicsDir` + `schematicPath`)

`schematicPath` supports:

1. Classpath resource: `"/assets/<modid>/structures/<name>.schematic"` or `"classpath:assets/<modid>/structures/<name>.schematic"`
2. Absolute path: `"F:/your/path/<name>.schematic"`
3. Relative path: `"ruins/<name>.schematic"` (resolved from `schematicsDir`)

`schematicsDir` default is `"schematics"` and is resolved as a config-sibling folder:

- normal runtime: `.minecraft/schematics`
- version/pack runtime: `<version-or-pack-dir>/schematics`
- dev workspace: `run/schematics`

Note: classpath resources come from packaged mod resources (`src/main/resources`), not from `run/`.

### Default template

```jsonc
{
  "enabled": true,
  "schematicsDir": "schematics",
  "structures": []
}
```

### Root fields

- `enabled`: global switch.
- `schematicsDir`: base directory for relative `schematicPath`.
- `structures`: structure entry array.

### Structure entry fields

- `enabled`: per-entry switch.
- `name` (optional): custom structure display/search name for the structure compass.
- `schematicPath`: structure file path.
- `dimension`: `overworld` / `the_nether` / `the_end`.
- `weight`: registration weight, `>= 1`.
- `chance`: denominator of probability, `>= 1` (for example `40` means about `1/40`).
- `attempts`: attempts per decoration pass, `>= 1`.
- `surface`: `true` uses surface height, `false` uses random `minY..maxY`.
- `minY`: minimum Y, `0..255`.
- `maxY`: maximum Y, `0..255` and `>= minY`.
- `yOffset`: final Y offset.
- `centerOnAnchor`: center-align structure to anchor.
- `minDistance` (optional): minimum horizontal distance to existing generated structure boxes. Default `0` (disabled).
- `distanceScope` (optional): spacing scope, `all` (default) or `same_name`.
- `biomeMode` (optional): `whitelist` or `blacklist`.
- `biomeIds` (optional): biome id list, such as `[4, 21]`.
- `biomeNames` (optional): biome name list, such as `["Forest", "Taiga"]`.

`biomeIds` and `biomeNames` can be used together.

### Example

```jsonc
{
  "enabled": true,
  "schematicsDir": "schematics",
  "structures": [
    {
      "enabled": true,
      "name": "test1",
      "schematicPath": "test1.schematic",
      "dimension": "overworld",
      "weight": 1,
      "chance": 40,
      "attempts": 1,
      "surface": true,
      "minY": 0,
      "maxY": 255,
      "yOffset": 0,
      "centerOnAnchor": true,
      "minDistance": 256,
      "distanceScope": "all",
      "biomeMode": "whitelist",
      "biomeNames": ["Forest", "Taiga"]
    },
    {
      "enabled": true,
      "name": "test2",
      "schematicPath": "test2.schematic",
      "dimension": "the_nether",
      "weight": 1,
      "chance": 40,
      "attempts": 1,
      "surface": true,
      "minY": 0,
      "maxY": 255,
      "yOffset": 0,
      "centerOnAnchor": true,
      "minDistance": 384,
      "distanceScope": "same_name"
    }
  ]
}
```

## Generation behavior

- Before placement, the generator clears the full schematic cuboid (`width * height * length`) with air.
- Then it places non-air blocks from schematic data and restores tile entities.
- On success, it records generated structure path/name/dimension/position/size for later search and spacing checks.
- Spacing checks are same-dimension only and use horizontal (XZ) bounding-box distance.
- If actual distance is `< minDistance`, this generation attempt is skipped.

## Structure Compass (creative only)

- Item name: `Structure Compass` (`结构指南针` in Chinese translation).
- Texture uses vanilla compass texture key (`compass`).
- No survival obtain method is added; item is available in creative tab `Tools`.
- Right click opens a GUI, no manual command typing required.

### GUI behavior

- Vanilla structure buttons are filtered by current dimension.
- Custom structure buttons are loaded from config names and filtered by current dimension (max 10 buttons).
- You can still type a custom query manually in the input box.
- `List` button shows queryable structures (vanilla + custom records).

### Search behavior

- Backend command is `/wgs <query>` (GUI sends it automatically).
- Search radius is fixed at `20000` blocks.
- Maximum returned results per search is `5`.
- Custom structure search uses generated records only (no retroactive backfill for old worlds).
- Vanilla search supports known + predictive positions (including unloaded regions) and merges overlapping candidates for large structures.

### Queryable vanilla structure IDs

- `fortress` (Nether)
- `stronghold` (Overworld)
- `village` (Overworld)
- `mineshaft` (Overworld)
- `temple` (Overworld)
- `desert_pyramid` (Overworld)
- `jungle_pyramid` (Overworld)
- `witch_hut` (Overworld)

## Developer API (compatible)

- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi`
- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig`

Simple example:

```java
StructureWorldgenApi.register(event, "/assets/examplemod/structures/ruin_a.schematic");
```

Builder example with newer fields:

```java
StructureWorldgenConfig config = StructureWorldgenApi.builder("F:/packs/schematics/ruin_a.schematic")
        .structureName("ruin_a")
        .dimension(Dimension.OVERWORLD)
        .weight(1)
        .chance(40)
        .attempts(1)
        .surface(true)
        .yRange(0, 255)
        .yOffset(0)
        .centerOnAnchor(true)
        .minDistance(256)
        .distanceScope(StructureWorldgenConfig.DistanceScope.ALL)
        .build();

StructureWorldgenApi.register(event, config);
```

## Optional file API

- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfigFileApi`

```java
StructureWorldgenConfigFileApi.registerFromDefaultConfig(event);
StructureWorldgenConfigFileApi.registerFromFile(event, new File("config/my-structures.jsonc"));
```
