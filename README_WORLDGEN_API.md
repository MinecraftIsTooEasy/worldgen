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
  "structures": [],
  "loot": {
    "enabled": true,
    "profiles": []
  }
}
```

### Root fields

- `enabled`: global switch.
- `schematicsDir`: base directory for relative `schematicPath`.
- `structures`: structure entry array.
- `loot.enabled`: global marker-chest loot switch (recommended).
- `loot.profiles`: loot profile array (recommended).
- Legacy root keys are still supported: `lootTableEnabled` / `lootEnabled` / `lootProfiles`.

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
- `lootTableEnabled` (optional, legacy): override switch for this structure entry (alias: `lootEnabled`). If absent, uses root loot switch.
- `lootProfile` (optional, legacy): loot profile id, default `default`. Controls marker-chest loot rules.
- `loot.enabled` (optional, recommended): per-structure loot switch. If absent, uses root `loot.enabled`.
- `loot.profile` (optional, recommended): per-structure loot profile id, default `default`.
- `biomeMode` (optional): `whitelist` or `blacklist`.
- `biomeIds` (optional): biome id list, such as `[4, 21]`.
- `biomeNames` (optional): biome name list, such as `["Forest", "Taiga"]`.

`biomeIds` and `biomeNames` can be used together.

### Loot profiles (player editable)

Recommended layout is a root `loot` block (placed below `structures` for readability):

- `loot.enabled`: enable/disable marker-chest loot table replacement.
- `loot.profiles`: profile array.
- Each structure can choose profile via `loot.profile`.

`lootProfiles` entry fields:

- `id`: profile id.
- `markers`: marker-to-level mapping array.
- `markers[].item`: item registry-like name (recommended), such as `"minecraft:stick"` / `"stick"` / `"item.stick"`.
- `markers[].itemId`: numeric item id (legacy compatible).
- `markers[].level`: marker level.
- `levels`: level table array.
- `levels[].level`: level index.
- `levels[].minRolls` / `levels[].maxRolls`: min/max roll count per chest.
- `levels[].entries`: loot pool array.
- `levels[].entries[].item`: item registry-like name (recommended).
- `levels[].entries[].itemId`: numeric item id (legacy compatible).
- `levels[].entries[].meta` / `min` / `max` / `weight`: entry metadata, count range, and weight.
- `levels[].artifactChances` (optional): extra array passed to `WeightedRandomChestContent.generateChestContents`.

If a chest in schematic contains exactly one marker item, loot is generated when loot switch is enabled (`loot.enabled`, legacy `lootTableEnabled`) using selected profile (`loot.profile`, legacy `lootProfile`).

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
      "biomeNames": ["Forest", "Taiga"],
      "loot": {
        "enabled": true,
        "profile": "default"
      }
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
      "distanceScope": "same_name",
      "loot": {
        "enabled": true,
        "profile": "default"
      }
    }
  ],
  "loot": {
    "enabled": true,
    "profiles": []
  }
}
```

## Generation behavior

- Before placement, the generator clears the full schematic cuboid (`width * height * length`) with air.
- Then it places non-air blocks from schematic data and restores tile entities.
- It also reads and spawns `Entities` from the schematic (player entities are ignored).
- Marker-chest loot generation is supported: if a chest in schematic contains exactly one marker item, it will be replaced with generated loot on placement.
- Default marker mapping: `stick->1`, `flint->2`, `coal->3`, `iron_ingot->4`, `gold_ingot->5`, `diamond->6`.
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

## Treasure Map (one-time binding)

- A default `Treasure Map` item is provided in creative tab `Tools`.
- Default target query is `stronghold`. First right click finds the nearest target and stores fixed coordinates in item NBT.
- After binding, right click only reports the fixed coordinates and will not rebind.
- Search output goes directly to chat; both vanilla structure IDs and custom structure names are supported.

### Treasure map developer API

- `com.github.hahahha.WorldGen.world.structure.api.TreasureMapApi`
- `com.github.hahahha.WorldGen.item.treasure.TreasureMapDefinition`

Example (call before item registration event):

```java
TreasureMapApi.register(
        TreasureMapApi.builder("examplemod:desert_treasure_map", "desert_pyramid")
                .namespace("examplemod")
                .textureName("desert_treasure_map")
                .unlocalizedName("desert_treasure_map")
                .itemName("item.examplemod.desert_treasure_map.name", "Desert Treasure Map")
                .targetDisplayName("Desert Pyramid")
                .searchRadius(30000)
                .build());
```

## Developer API (compatible)

- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi`
- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig`
- `com.github.hahahha.WorldGen.world.structure.api.StructureLootApi`
- `com.github.hahahha.WorldGen.world.structure.StructureLootProfile`

Example source file (pure API registration, no config-file mixing):

- `com.github.hahahha.WorldGen.world.structure.example.Test10ApiStructureRegistration`

`test10.schematic` example (explicit loot-profile registration + full explicit default structure parameters):

```java
private static final StructureLootProfile TEST10_LOOT_PROFILE = StructureLootApi.builder("test10_loot")
        .marker(Item.stick, 1)
        .marker(Item.diamond, 6)
        .level(
                1,
                3,
                5,
                new WeightedRandomChestContent[]{
                        StructureLootApi.entry(Item.coal, 0, 1, 3, 20),
                        StructureLootApi.entry(Item.flint, 0, 1, 2, 15)
                })
        .level(
                6,
                6,
                9,
                new WeightedRandomChestContent[]{
                        StructureLootApi.entry(Item.diamond, 0, 1, 2, 10),
                        StructureLootApi.entry(Item.ingotGold, 0, 2, 5, 18)
                })
        .build();

StructureWorldgenConfig config = StructureWorldgenApi.builder("/assets/worldgen/structures/test10.schematic")
        .structureName("test10")
        .dimension(Dimension.OVERWORLD)
        .weight(1)
        .chance(40)
        .attempts(1)
        .surface(true)
        .yRange(0, 255)
        .yOffset(0)
        .centerOnAnchor(true)
        .minDistance(0)
        .distanceScope(StructureWorldgenConfig.DistanceScope.ALL)
        .lootTableEnabled(true)
        .lootProfile(TEST10_LOOT_PROFILE)
        .build();

StructureWorldgenApi.register(event, config);
```

## Optional file API

- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfigFileApi`

```java
StructureWorldgenConfigFileApi.registerFromDefaultConfig(event);
StructureWorldgenConfigFileApi.registerFromFile(event, new File("config/my-structures.jsonc"));
```
