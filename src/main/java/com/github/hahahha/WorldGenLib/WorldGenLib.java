package com.github.hahahha.WorldGenLib;

import com.github.hahahha.WorldGenLib.world.structure.StructureWorldGenLibRegistration;
import com.github.hahahha.WorldGenLib.world.structure.StructureEntityReplacementRuntime;
import moddedmite.rustedironcore.api.event.Handlers;
import net.fabricmc.api.ModInitializer;
import net.xiaoyu233.fml.ModResourceManager;
import net.xiaoyu233.fml.reload.event.MITEEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldGenLib implements ModInitializer {
    public static final String MOD_ID = "worldgenlib";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModResourceManager.addResourcePackDomain(MOD_ID);

        // FML events: item registration and chat command hook.
        MITEEvents.MITE_EVENT_BUS.register(new EventListen());
        // Structure WorldGenLib registration entry.
        Handlers.BiomeDecoration.registerPre(StructureWorldGenLibRegistration::register);
        Handlers.EntityEvent.register(StructureEntityReplacementRuntime.INSTANCE);

        LOGGER.info("WorldGenLib API initialized");
    }
}
