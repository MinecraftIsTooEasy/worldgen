# WorldGen 结构世界生成说明（中文）

英文版：`README_WORLDGEN_API.md`

本模组提供两个互不影响的入口：

1. 玩家配置入口：`config/worldgen-structures.jsonc`
2. 开发者 API 入口：`StructureWorldgenApi` / `StructureWorldgenConfig`

## 玩家配置入口

首次启动会自动创建：

- `config/worldgen-structures.jsonc`
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
  "structures": []
}
```

### 根级字段

- `enabled`：总开关。
- `schematicsDir`：相对 `schematicPath` 的基础目录。
- `structures`：结构条目数组。

### 结构条目字段

- `enabled`：单条开关。
- `name`（可选）：结构指南针显示/搜索用名称。
- `schematicPath`：结构文件路径。
- `dimension`：`overworld` / `the_nether` / `the_end`。
- `weight`：注册权重，`>= 1`。
- `chance`：概率分母，`>= 1`（例如 `40` 表示约 `1/40`）。
- `attempts`：每次装饰阶段尝试次数，`>= 1`。
- `surface`：`true` 用地表高度，`false` 用 `minY..maxY` 随机高度。
- `minY`：最小 Y，`0..255`。
- `maxY`：最大 Y，`0..255` 且 `>= minY`。
- `yOffset`：最终 Y 偏移。
- `centerOnAnchor`：是否以锚点居中放置结构。
- `minDistance`（可选）：与已生成结构包围盒的最小水平距离（方块）。默认 `0`（关闭）。
- `distanceScope`（可选）：间距作用范围，`all`（默认）或 `same_name`。
- `biomeMode`（可选）：`whitelist` 或 `blacklist`。
- `biomeIds`（可选）：群系 ID 列表，例如 `[4, 21]`。
- `biomeNames`（可选）：群系名列表，例如 `["Forest", "Taiga"]`。

`biomeIds` 和 `biomeNames` 可以同时使用。

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

## 当前生成行为

- 放置结构前会先把整个结构体积（`width * height * length`）清空为空气。
- 然后再放置 `.schematic` 里的非空气方块，并恢复方块实体。
- 成功生成后会记录结构路径/名称/维度/坐标/尺寸，用于后续搜索与间距判定。
- 间距判定只在同维度内进行，按 XZ 平面的包围盒距离计算。
- 如果实际距离 `< minDistance`，本次生成会跳过。

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

## 开发者 API（兼容保留）

- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi`
- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig`

简写示例：

```java
StructureWorldgenApi.register(event, "/assets/examplemod/structures/ruin_a.schematic");
```

包含新参数的 Builder 示例：

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

## 可选文件 API

- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfigFileApi`

```java
StructureWorldgenConfigFileApi.registerFromDefaultConfig(event);
StructureWorldgenConfigFileApi.registerFromFile(event, new File("config/my-structures.jsonc"));
```
