# WorldGenLib 结构世界生成说明（中文）

英文版：`README_WORLDGEN_API.md`
当前版本：`1.3.0`
更新日志：`ChangeLogCN.md`

## 1.3.0 更新要点

- 结构检索能力增强：`StructureGenerationDataStore` 采用精确匹配 + 空间索引 + bigram 模糊索引，`VanillaStructureSearchService` 合并“已记录 + 预测候选”并做去重。
- 配置/GUI/API 缓存链路补强：玩家配置根对象缓存、`itemId` 解析缓存、维度与 `distanceScope` 解析缓存、指南针自定义结构缓存、模组名 LRU 缓存、配置文件 API 结构列表缓存。
- 新增并全量接入 `StringNormalization`（`trimToNull` / `trimLowerToNull` / `isBlank` / `normalizePath` / `extractModIdFromAssetsPath`），统一配置、指令、GUI、结构与藏宝图的字符串处理。
- `StructureItemIdResolver` 升级为原子别名缓存 + miss 节流刷新，减少反射扫描与重复构建开销。
- 藏宝图、战利品与实体替换链路统一到同一套归一化和物品名解析流程（`TreasureMapApi` / `StructureLootApi` / `StructureEntityReplacementApi`）。
- 稳定性修正：`resolveModDisplayName` 的异常捕获收敛为 `catch (RuntimeException | LinkageError)`。

本模组提供两个互不影响的入口：

1. 玩家配置入口：`config/WorldGenLib-structures.jsonc`
2. 开发者 API 入口：`StructureWorldGenLibApi` / `StructureWorldGenLibConfig`

## 玩家配置入口

首次启动会自动创建：

- `config/WorldGenLib-structures.jsonc`
- `schematics/`（与 `config/` 同级）

配置格式是 JSONC（JSON + 注释）。

### 路径规则（`schematicsDir` + `schematicPath`）

`schematicPath` 支持：

1. Classpath 资源路径：`"/assets/<modid>/structures/<name>.schematic"` 或 `"classpath:assets/<modid>/structures/<name>.schematic"`
2. 绝对路径：`"F:/your/path/<name>.schematic"`
3. 相对路径：`"ruins/<name>.schematic"`（基于 `schematicsDir` 解析）

`schematicsDir` 默认是 `"schematics"`，解析为与 `config` 同级目录：

- 正常运行：`.minecraft/schematics`
- 版本/整合包目录运行：`<版本或整合包目录>/schematics`
- 开发环境：`run/schematics`

注意：Classpath 资源来自模组打包资源（`src/main/resources`），不是 `run/` 目录。

### 默认模板

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

### 根级字段

- `enabled`：总开关。
- `schematicsDir`：相对 `schematicPath` 的基础目录。
- `structures`：结构条目数组。
- `loot.enabled`：标记箱战利品替换总开关（推荐写法）。
- `loot.profiles`：战利品配置档数组（推荐写法）。
- `entityReplacement.enabled`：生物替换总开关（推荐写法）。
- `entityReplacement.profiles`：生物替换配置档数组（推荐写法）。
- 兼容旧写法：根级 `lootTableEnabled` / `lootEnabled` / `lootProfiles` 仍然支持。

### 结构条目字段

- `enabled`：单条开关。
- `name`（可选）：结构指南针显示/搜索用名称。
- `schematicPath`：结构文件路径。
- `dimension`：`overworld` / `the_nether` / `the_end`。
- `weight`：注册权重，`>= 1`。
- `chance`：概率分母，`>= 1`（例如 `40` 表示约 `1/40`）。
- `attempts`：每次装饰阶段尝试次数，`>= 1`。
- 实际尝试次数为 `attempts * weight`（超过 `Integer.MAX_VALUE` 时会截断）。
- `surface`：`true` 用地表高度，`false` 用 `minY..maxY` 随机高度。
- `minY`：最小 Y，`0..255`。
- `maxY`：最大 Y，`0..255` 且 `>= minY`。
- `yOffset`：最终 Y 偏移。
- `centerOnAnchor`：是否以锚点居中放置结构。
- `minDistance`（可选）：与已生成结构包围盒的最小水平距离（方块）。默认 `0`（关闭）。
- `distanceScope`（可选）：间距作用范围，`all`（默认）或 `same_name`。
- `lootTableEnabled`（可选，旧写法）：当前结构条目的战利品开关（别名：`lootEnabled`）；未填写时继承根级战利品开关。
- `lootProfile`（可选，旧写法）：战利品配置档 ID，默认 `default`。用于标记箱子刷战利品规则。
- `loot.enabled`（可选，推荐）：当前结构条目的战利品开关；未填写时继承根级 `loot.enabled`。
- `loot.profile`（可选，推荐）：当前结构条目的战利品配置档 ID，默认 `default`。
- `entityReplacementEnabled`（可选，旧写法）：当前结构条目的生物替换开关；未填写时继承根级开关。
- `entityReplacementProfile`（可选，旧写法）：当前结构条目的生物替换配置档 ID，默认 `default`。
- `entityReplacement.enabled`（可选，推荐）：当前结构条目的生物替换开关；未填写时继承根级 `entityReplacement.enabled`。
- `entityReplacement.profile`（可选，推荐）：当前结构条目的生物替换配置档 ID，默认 `default`。
- `biomeMode`（可选）：`whitelist` 或 `blacklist`。
- `biomeIds`（可选）：群系 ID 列表，例如 `[4, 21]`。
- `biomeNames`（可选）：群系名列表，例如 `["Forest", "Taiga"]`。

`biomeIds` 和 `biomeNames` 可以同时使用。

### 战利品配置（玩家可改）

建议把战利品集中写在根级 `loot` 块（放在 `structures` 后面更清晰）：

- `loot.enabled`：是否启用标记箱战利品表。
- `loot.profiles`：配置档数组。
- 结构条目可用 `loot.profile` 指定配置档。

`lootProfiles` 条目字段：

- `id`：配置档 ID。
- `markers`：标记物到等级映射数组。
- `markers[].item`：物品注册名（推荐），例如 `"minecraft:stick"` / `"stick"` / `"item.stick"`。
- `markers[].itemId`：数字物品 ID（兼容旧写法）。
- `markers[].level`：等级。
- `levels`：等级表数组。
- `levels[].level`：等级号。
- `levels[].minRolls` / `levels[].maxRolls`：每箱最少/最多抽取次数。
- `levels[].entries`：物品池数组。
- `levels[].entries[].item`：物品注册名（推荐）。
- `levels[].entries[].itemId`：数字物品 ID（兼容旧写法）。
- `levels[].entries[].meta` / `min` / `max` / `weight`：元数据、数量范围、权重。
- `levels[].artifactChances`（可选）：传递给底层 `WeightedRandomChestContent.generateChestContents` 的额外参数。

如果结构里箱子内“只有一个标记物”，且战利品开关（`loot.enabled` / 兼容 `lootTableEnabled`）开启，会按所选配置档（`loot.profile` / 兼容 `lootProfile`）刷箱子内容。

### 配置示例

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
      },
      "entityReplacement": {
        "enabled": true,
        "profile": "test10_entity"
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
  },
  "entityReplacement": {
    "enabled": true,
    "profiles": [
      {
        "id": "test10_entity",
        "levels": [
          {
            "level": 1,
            "target": "Zombie"
          },
          {
            "level": 2,
            "target": "Skeleton",
            "equipment": {
              "mainHand": { "item": "swordIron" },
              "helmet": { "item": "helmetIron" }
            },
            "drops": [
              { "item": "ingotIron", "min": 1, "max": 1, "chance": 1.0 }
            ]
          },
          {
            "level": 3,
            "target": "Spider"
          }
        ]
      }
    ]
  }
}
```

## 当前生成行为

- 放置结构前会先把整个结构体积（`width * height * length`）清空为空气。
- 然后再放置 `.schematic` 里的非空气方块，并恢复方块实体。
- 会读取并生成 `.schematic` 里的 `Entities`（玩家实体会被忽略）。
- 支持按结构 profile 的生物替换：会在生成前按等级 `1/2/3` 将实体替换为目标生物。
- 默认内置 `test10` 的替换映射：`1 -> Zombie`、`2 -> Skeleton`、`3 -> Spider`。
- 生物替换配置档可自定义装备（主手/靴子/护腿/胸甲/头盔）和额外掉落表。
- 支持“标记箱子刷战利品”：若结构中的箱子只放了一个标记物，会在生成时清空并按等级表填充战利品。
- 标记等级默认映射：`stick->1`、`flint->2`、`coal->3`、`iron_ingot->4`、`gold_ingot->5`、`diamond->6`。
- 成功生成后会记录结构路径/名称/维度/坐标/尺寸，用于后续搜索与间距判定。
- 间距判定只在同维度内进行，按 XZ 平面的包围盒距离计算。
- 如果实际距离 `< minDistance`，本次生成会跳过。

生物替换调试开关：

```powershell
.\gradlew runClient -Dworldgenlib.debug.entityReplacement=true
```

## 结构指南针（仅创造模式）

- 物品名：`Structure Compass`（中文翻译：`结构指南针`）。
- 材质贴图使用原版指南针材质键（`compass`）。
- 未添加生存获取方式，仅在创造模式 `Tools` 分类可用。
- 右键直接打开 GUI，不需要手动输入指令。

### GUI 行为

- 原版结构按钮按当前维度过滤显示。
- 自定义结构按钮从配置读取名称，并按当前维度过滤（最多 10 个按钮）。
- 也可以在输入框手动输入任意结构名搜索。
- `可查列表` 按钮会输出可查询结构（原版 + 自定义记录）。

### 搜索行为

- 底层命令仍是 `/wgs <query>`（由 GUI 自动发送）。
- 搜索半径固定为 `20000` 格。
- 单次最多返回 `5` 个最近结果。
- 自定义结构查询只查“已记录的生成结果”（不会回填老存档历史结构）。
- 原版结构查询包含“已记录 + 未加载区块预测”，并对大型结构候选进行合并去重。

### 可查询原版结构 ID

- `fortress`（下界）
- `stronghold`（主世界）
- `village`（主世界）
- `mineshaft`（主世界）
- `temple`（主世界）
- `desert_pyramid`（主世界）
- `jungle_pyramid`（主世界）
- `witch_hut`（主世界）

## 藏宝图（一次性绑定）

- 默认提供 `Treasure Map`（中文：`藏宝图`），位于创造模式 `Tools` 分类。
- 默认追踪目标是 `stronghold`，首次右键会搜索最近目标并把坐标写入物品 NBT。
- 一旦绑定，后续右键只会回显固定坐标，不会再次重绑到新位置。
- 搜索结果会直接输出到聊天框，支持原版结构 ID 和自定义结构名（基于已记录结构数据）。

### 藏宝图开发者 API

- `com.github.hahahha.WorldGenLib.world.structure.api.TreasureMapApi`
- `com.github.hahahha.WorldGenLib.item.treasure.TreasureMapDefinition`

示例（在物品注册事件前调用）：

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

## 开发者 API（兼容保留）

- `com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibApi`
- `com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibConfig`
- `com.github.hahahha.WorldGenLib.world.structure.api.StructureLootApi`
- `com.github.hahahha.WorldGenLib.world.structure.api.StructureEntityReplacementApi`
- `com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementProfile`
- `com.github.hahahha.WorldGenLib.world.structure.StructureLootProfile`

示例代码文件（纯 API 注册，不混用配置文件）：

- `com.github.hahahha.WorldGenLib.world.structure.example.Test10ApiStructureRegistration`

`test10.schematic` 示例（战利品显式注册 + 结构全参数默认值显式填写）：

```java
// =========================
// 1) 战利品 Profile 定义
// =========================
// 约定：全部使用 namespaced key（modid:item），不使用数字 ID。
// 好处：可读性高、跨模组冲突风险低、后续迁移更稳。
private static final StructureLootProfile TEST10_LOOT_PROFILE = StructureLootApi.builder("test10_loot")
        // 标记物 -> 等级映射（结构箱子里放 1 个标记物时触发对应等级掉落）
        .marker("minecraft:stick", 1)
        .marker("minecraft:diamond", 6)
        // 等级 1~3：minRoll=3, maxRoll=5
        .level(
                1,
                3,
                5,
                new WeightedRandomChestContent[]{
                        // entry(item, meta, min, max, weight)
                        StructureLootApi.entry("minecraft:coal", 0, 1, 3, 20),
                        StructureLootApi.entry("minecraft:stick", 0, 1, 2, 15)
                })
        // 等级 6：更高价值掉落
        .level(
                6,
                6,
                9,
                new WeightedRandomChestContent[]{
                        StructureLootApi.entry("minecraft:diamond", 0, 1, 2, 10),
                        StructureLootApi.entry("minecraft:ingotGold", 0, 2, 5, 18)
                })
        .build();

// 缓存实体替换 profile，避免重复构建。
private static volatile StructureEntityReplacementProfile cachedEntityReplacementProfile;

// =========================
// 2) 实体替换 Profile（懒加载）
// =========================
// 重点：不要在注册阶段提前解析装备物品键。
// 原因：启动早期物品别名表可能尚未稳定，容易出现“某些键偶发解析失败”。
private static StructureEntityReplacementProfile getEntityReplacementProfile() {
    StructureEntityReplacementProfile cached = cachedEntityReplacementProfile;
    if (cached != null) {
        return cached;
    }
    synchronized (Test10ApiStructureRegistration.class) {
        cached = cachedEntityReplacementProfile;
        if (cached != null) {
            return cached;
        }
        StructureEntityReplacementProfile created = createEntityReplacementProfile();
        cachedEntityReplacementProfile = created;
        return created;
    }
}

// 实际构建替换规则。
private static StructureEntityReplacementProfile createEntityReplacementProfile() {
    StructureEntityReplacementApi.EntityReplacementProfileBuilder builder =
            StructureEntityReplacementApi.builder("test10_entity")
                    // 等级 1：僵尸
                    .level(1, "Zombie")
                    // 等级 2：骷髅（并附加装备/掉落）
                    .level(2, "Skeleton")
                    // 等级 3：蜘蛛
                    .level(3, "Spider");

    // 等级 2 主手：铁剑
    ItemStack mainHand = tryResolveStack("minecraft:swordIron", 1, 0);
    if (mainHand != null) {
        builder.mainHand(2, mainHand);
    }

    // 等级 2 头盔：铁头盔
    ItemStack helmet = tryResolveStack("minecraft:helmetIron", 1, 0);
    if (helmet != null) {
        builder.helmet(2, helmet);
    }

    // 等级 2 额外掉落：铁锭（100%）
    StructureEntityReplacementProfile.EntityDrop drop =
            tryResolveDrop("minecraft:ingotIron", 0, 1, 1, 1.0F);
    if (drop != null) {
        builder.drop(2, drop);
    }

    return builder.build();
}

private static ItemStack tryResolveStack(String itemName, int count, int meta) {
    try {
        // 名称解析失败时抛 IllegalArgumentException，这里吞掉并返回 null。
        return StructureEntityReplacementApi.stack(itemName, count, meta);
    } catch (IllegalArgumentException ignored) {
        return null;
    }
}

private static StructureEntityReplacementProfile.EntityDrop tryResolveDrop(
        String itemName, int meta, int minCount, int maxCount, float chance) {
    try {
        return StructureEntityReplacementApi.drop(itemName, meta, minCount, maxCount, chance);
    } catch (IllegalArgumentException ignored) {
        return null;
    }
}

// =========================
// 3) 结构注册
// =========================
StructureWorldGenLibConfig config = StructureWorldGenLibApi.builder("/assets/worldgenlib/structures/test10.schematic")
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
        .distanceScope(StructureWorldGenLibConfig.DistanceScope.ALL)
        .lootTableEnabled(true)
        .lootProfile(TEST10_LOOT_PROFILE)
        .entityReplacementEnabled(true)
        // 关键：传 supplier，在“实际生成时”才取 profile，避免注册时机问题。
        .entityReplacementProfileSupplier("test10_entity", Test10ApiStructureRegistration::getEntityReplacementProfile)
        .build();

StructureWorldGenLibApi.register(event, config);
```

注册结果查询便捷方法：

```java
StructureWorldGenLibApi.listRegisteredStructures(10);
StructureWorldGenLibApi.listRegisteredStructures(10, 0); // 维度 0（主世界）
StructureWorldGenLibApi.listRegisteredStructureNames(10);
StructureWorldGenLibApi.listRegisteredStructureNames(10, 0);
```

## 可选文件 API

- `com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibConfigFileApi`

```java
StructureWorldGenLibConfigFileApi.registerFromDefaultConfig(event);
StructureWorldGenLibConfigFileApi.registerFromFile(event, new File("config/my-structures.jsonc"));

StructureWorldGenLibConfigFileApi.listConfiguredStructures(10);
StructureWorldGenLibConfigFileApi.listConfiguredStructures(10, 0); // 维度 0（主世界）
StructureWorldGenLibConfigFileApi.listConfiguredStructureNames(10);
StructureWorldGenLibConfigFileApi.listConfiguredStructureNames(10, 0);
```
