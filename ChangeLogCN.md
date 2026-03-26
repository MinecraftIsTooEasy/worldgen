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
