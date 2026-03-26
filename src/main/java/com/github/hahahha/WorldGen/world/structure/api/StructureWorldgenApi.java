package com.github.hahahha.WorldGen.world.structure.api;

import com.github.hahahha.WorldGen.WorldGen;
import com.github.hahahha.WorldGen.world.structure.SchematicStructureGenerator;
import java.util.Objects;
import java.util.Random;
import moddedmite.rustedironcore.api.event.events.BiomeDecorationRegisterEvent;
import moddedmite.rustedironcore.api.event.handler.BiomeDecorationHandler;
import moddedmite.rustedironcore.api.world.Dimension;

/**
 * 对外公开的结构注册 API。
 * 其他模组只需要传路径和参数，即可把 .schematic 注册到世界生成流程中。
 */
public final class StructureWorldgenApi {
    private StructureWorldgenApi() {
    }

    public static StructureWorldgenConfig.Builder builder(String schematicPath) {
        return StructureWorldgenConfig.builder(schematicPath);
    }

    /**
     * 最简写法：只给路径，其余走默认参数。
     */
    public static void register(BiomeDecorationRegisterEvent event, String schematicPath) {
        register(event, builder(schematicPath).build());
    }

    /**
     * 完整注册：通过配置对象控制维度、概率、尝试次数和高度策略。
     */
    public static void register(BiomeDecorationRegisterEvent event, StructureWorldgenConfig config) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(config, "config");

        SchematicStructureGenerator generator = new SchematicStructureGenerator(config);
        BiomeDecorationHandler.SettingBuilder settingBuilder = event
                .register(config.dimension(), generator, config.weight())
                .setAttempts(config.attempts())
                .setChance(config.chance());

        if (config.surface()) {
            settingBuilder.setSurface();
        } else {
            // 非地表模式：在 yRange 中随机取高度。
            settingBuilder.setHeightSupplier((context, x, z) ->
                    randomY(context.rand(), config.minY(), config.maxY()));
        }

        if (config.biomeFilter() != null) {
            settingBuilder.requiresBiome(config.biomeFilter());
        }

        WorldGen.LOGGER.info(
                "Registered structure worldgen path={} dim={} weight={} chance=1/{} attempts={} surface={} y={}..{} yOffset={} centerOnAnchor={}",
                config.schematicPath(),
                config.dimension(),
                config.weight(),
                config.chance(),
                config.attempts(),
                config.surface(),
                config.minY(),
                config.maxY(),
                config.yOffset(),
                config.centerOnAnchor());
    }

    public static void register(
            BiomeDecorationRegisterEvent event,
            String schematicPath,
            Dimension dimension,
            int weight,
            int chance) {
        register(
                event,
                builder(schematicPath)
                        .dimension(dimension)
                        .weight(weight)
                        .chance(chance)
                        .build());
    }

    private static int randomY(Random random, int minY, int maxY) {
        if (minY >= maxY) {
            return minY;
        }
        return minY + random.nextInt(maxY - minY + 1);
    }
}
