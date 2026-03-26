# 结构生成 API 使用说明（WorldGen 模组）

这个模组已经内置了 `.schematic` 结构生成能力，并提供了公开 API。  
其他模组只需要传“遗迹文件路径 + 几个参数”，就可以注册世界结构生成。

## 1. 你要调用的 API

- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi`
- `com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig`

## 2. 遗迹文件路径支持两种写法

- 资源路径（推荐）  
  `/assets/<你的modid>/structures/<文件名>.schematic`
- 本地文件路径（调试方便）  
  `F:/your/path/<文件名>.schematic`

## 3. 最简注册示例（只填路径）

```java
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi;

StructureWorldgenApi.register(event, "/assets/examplemod/structures/ruin_a.schematic");
```

默认参数：

- 维度：`OVERWORLD`
- 权重：`1`
- 概率：`1/40`
- 尝试次数：`1`
- 高度策略：地表（`surface(true)`）
- Y 范围：`0..255`
- Y 偏移：`0`
- 对齐方式：结构中心对齐锚点（`centerOnAnchor(true)`）

## 4. 完整注册示例（自定义参数）

```java
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenApi;
import com.github.hahahha.WorldGen.world.structure.api.StructureWorldgenConfig;
import moddedmite.rustedironcore.api.world.Dimension;

StructureWorldgenApi.register(
    event,
    StructureWorldgenConfig.builder("/assets/examplemod/structures/ruin_a.schematic")
        .dimension(Dimension.OVERWORLD)
        .weight(2)
        .chance(30)          // 约 1/30
        .attempts(2)
        .surface(false)      // 关闭地表策略，使用 yRange 随机高度
        .yRange(20, 80)
        .yOffset(0)
        .centerOnAnchor(true)
        .build()
);
```

## 5. 参数说明

- `dimension(Dimension.xxx)`：生成维度
- `weight(int)`：注册权重
- `chance(int)`：概率分母，N 表示约 `1/N`
- `attempts(int)`：每次装饰阶段尝试次数
- `surface(boolean)`：
  - `true` 用地表高度
  - `false` 用 `yRange(min,max)` 随机高度
- `yRange(minY, maxY)`：非地表模式高度范围（`0..255`）
- `yOffset(int)`：在最终高度上再加偏移
- `centerOnAnchor(boolean)`：
  - `true` 锚点在结构中心
  - `false` 锚点在结构角点
- `biomeFilter(predicate)`：按生物群系过滤

## 6. 在模组初始化中接入

你自己的模组里，一般在 `BiomeDecoration` 注册事件中调用上述 API：

```java
Handlers.BiomeDecoration.registerPre(YourWorldgenRegistration::registerStructures);
```

然后在 `registerStructures(BiomeDecorationRegisterEvent event)` 里执行 `StructureWorldgenApi.register(...)` 即可。
