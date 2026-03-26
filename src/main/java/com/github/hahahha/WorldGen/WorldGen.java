package com.github.hahahha.WorldGen;

import com.github.hahahha.WorldGen.world.structure.StructureWorldgenRegistration;
import moddedmite.rustedironcore.api.event.Handlers;
import net.fabricmc.api.ModInitializer;
import net.xiaoyu233.fml.ModResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 模组入口：当前版本只保留结构世界生成功能。
 */
public class WorldGen implements ModInitializer {
    public static final String MOD_ID = "worldgen";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModResourceManager.addResourcePackDomain(MOD_ID);

        // 在生物群系装饰阶段注册结构生成。
        Handlers.BiomeDecoration.registerPre(StructureWorldgenRegistration::register);
        LOGGER.info("WorldGen API initialized");
    }
}
