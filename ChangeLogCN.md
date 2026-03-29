V1.3.0
- 结构查询链路重构：`StructureGenerationDataStore` 新增“精确匹配索引 + 空间索引 + bigram 模糊索引”三层检索，并加入候选上限与 Top-K 合并策略，提升 `/wgs` 与藏宝图的自定义结构查询性能。
- 原版结构检索增强：`VanillaStructureSearchService` 统一“已记录结构数据 + 未加载区块预测”查询流程，并对大型结构候选进行空间合并去重；新增按世界缓存 `MapGenStructureData`。
- 玩家配置加载器优化：`StructureWorldGenLibPlayerConfigLoader` 增加根配置缓存（文件时间戳+长度）、`itemId` 解析缓存、维度/`distanceScope` 解析缓存、路径解析与资源可用性缓存，并保留旧键兼容。
- 结构指南针交互优化：`GuiStructureCompassSearch` 新增按维度缓存自定义结构按钮、按配置文件元信息自动失效缓存、模组显示名 LRU 缓存；`StructureCompassCommandHandler` 的查询与列表别名统一归一化。
- 新增并全量接入 `StringNormalization`（`trimToNull` / `trimLowerToNull` / `isBlank` / `normalizePath` / `extractModIdFromAssetsPath`），统一配置、API、GUI、结构与藏宝图的字符串/路径处理。
- `StructureItemIdResolver` 升级为“原子别名缓存 + miss 节流刷新”机制，减少重复构建别名表与反射扫描开销。
- `StructureWorldGenLibConfigFileApi` 增加配置结构列表缓存（按配置路径、mtime、length、维度、条目上限），降低 GUI 与外部调用重复读取成本。
- 藏宝图体系强化：`TreasureMapDefinition` / `TreasureMapRegistry` / `ItemTreasureMap` 对 mapId、查询词、显示名做统一校验与归一化，保留“一次绑定坐标”行为并继续支持 `TreasureMapApi` 注册。
- 战利品与实体替换 API 统一接入同一套物品名解析与字符串归一化（`StructureLootApi` / `StructureEntityReplacementApi`），提升跨模组配置可用性。
- 稳定性修正：`GuiStructureCompassSearch#resolveModDisplayName` 将 `catch (Throwable)` 收敛为 `catch (RuntimeException | LinkageError)`，避免吞掉不可恢复错误。
- 文档同步：中英文 README 的 `1.3.0` 说明与链接同步更新，工程版本提升到 `1.3.0`。

V1.2.0
- 新增纯 API 示例注册文件：`com.github.hahahha.WorldGenLib.world.structure.example.Test10ApiStructureRegistration`。
- 使用 `test10.schematic` 作为结构注册示例，结构参数以显式默认值方式填写，便于直接对照修改。
- 示例中显式注册并绑定 `test10_loot` 战利品 Profile（`lootProfile(...)`），不再仅依赖默认战利品表。
- 结构注册入口调整为 API-only 示例，不再与 `config/WorldGenLib-structures.jsonc` 的文件注册混用。
- 玩家配置战利品写法增强：支持推荐写法 `loot.enabled` / `loot.profiles` 与结构级 `loot.enabled` / `loot.profile`，并兼容旧键。
- 中英文 README 的 API 示例同步到 `test10.schematic` 与显式战利品配置写法。

V1.1.0
- 新增玩家配置入口：`config/WorldGenLib-structures.jsonc`（JSONC，可注释），普通玩家可直接配置结构生成。
- 新增 `schematicsDir` 基础目录配置，支持 classpath / 绝对路径 / 相对路径三种 `schematicPath`。
- 新增结构条目命名字段 `name`，用于结构指南针显示与搜索。
- 新增群系过滤参数：`biomeMode`、`biomeIds`、`biomeNames`。
- 新增结构间距参数：`minDistance`、`distanceScope`（`all` / `same_name`）。
- 新增结构生成记录（维度、坐标、名称、尺寸），用于查询与间距判定。
- 新增“结构指南针”（创造模式）：支持 GUI 选择与输入搜索，返回附近结构坐标。
- 修复结构指南针材质与本地化显示问题，保留并兼容原有开发者 API 入口。

V1.0.0
- 内置 `.schematic` 结构生成能力，并提供公开 API。
- 其他模组可通过“结构路径 + 参数”完成结构注册与生成。
