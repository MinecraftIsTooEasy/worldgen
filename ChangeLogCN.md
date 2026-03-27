V1.0.2
- 新增纯 API 示例注册文件：`com.github.hahahha.WorldGen.world.structure.example.Test10ApiStructureRegistration`。
- 使用 `test10.schematic` 进行结构注册示例，结构参数全部显式填写为默认值，便于直接对照修改。
- 示例中显式注册并绑定 `test10_loot` 战利品 Profile（`lootProfile(...)`），不再仅使用默认战利品表。
- 结构注册入口改为 API-only 示例，不再与 `config/worldgen-structures.jsonc` 的文件注册混用。
- 玩家配置战利品写法增强：支持推荐写法 `loot.enabled` / `loot.profiles` 与结构级 `loot.enabled` / `loot.profile`，并兼容旧键。
- 中英文 README 的 API 示例已同步到 `test10.schematic` 和显式战利品配置写法。

V1.0.1
- 新增玩家配置入口：`config/worldgen-structures.jsonc`（JSONC，可注释），普通玩家可直接配置结构生成。
- 新增 `schematicsDir` 基础目录配置，支持 classpath / 绝对路径 / 相对路径三种 `schematicPath`。
- 新增结构条目命名字段 `name`，可用于结构指南针显示与搜索。
- 新增群系过滤参数：`biomeMode`、`biomeIds`、`biomeNames`。
- 新增结构间距参数：`minDistance`、`distanceScope`（`all` / `same_name`），避免结构互相贴脸或重叠。
- 调整结构放置逻辑：生成前先清空结构长宽高立方体，再放置 schematic 非空气方块与方块实体。
- 新增结构生成记录（含维度、坐标、名称、尺寸），用于查询与间距判定。
- 新增“结构指南针”（创造模式）：右键打开 GUI，支持按钮选择和输入搜索，返回附近结构坐标。
- 结构指南针支持原版结构查询，并加入未加载区块预测搜索；大型结构结果做了合并去重。
- 修复结构指南针材质与汉化显示问题（使用指南针材质键，名称正常显示）。
- 保留开发者 API 入口，兼容原有 `StructureWorldgenApi` / `StructureWorldgenConfig` 调用方式。

V1.0.0
这个模组已经内置了 `.schematic` 结构生成能力，并提供了公开 API。  
其他模组只需要传“遗迹文件路径 + 几个参数”，就可以注册世界结构生成。
