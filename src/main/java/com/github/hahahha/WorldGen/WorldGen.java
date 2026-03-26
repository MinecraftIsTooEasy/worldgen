package com.github.hahahha.WorldGen;

import com.github.hahahha.WorldGen.world.structure.StructureWorldgenRegistration;
import moddedmite.rustedironcore.api.event.Handlers;
import net.fabricmc.api.ModInitializer;
import net.xiaoyu233.fml.ModResourceManager;
import net.xiaoyu233.fml.reload.event.MITEEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldGen implements ModInitializer {
    public static final String MOD_ID = "worldgen";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModResourceManager.addResourcePackDomain(MOD_ID);

        // FML events: item registration and chat command hook.
        MITEEvents.MITE_EVENT_BUS.register(new EventListen());
        // Structure worldgen registration entry.
        Handlers.BiomeDecoration.registerPre(StructureWorldgenRegistration::register);

        LOGGER.info("WorldGen API initialized");
    }
}
