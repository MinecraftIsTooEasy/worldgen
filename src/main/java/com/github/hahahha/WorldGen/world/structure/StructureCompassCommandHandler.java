package com.github.hahahha.WorldGen.world.structure;

import com.github.hahahha.WorldGen.util.I18n;
import com.github.hahahha.WorldGen.world.structure.data.StructureGenerationDataStore;
import com.github.hahahha.WorldGen.world.structure.vanilla.VanillaStructureSearchService;
import com.github.hahahha.WorldGen.world.structure.vanilla.VanillaStructureSearchService.VanillaStructureResult;
import com.github.hahahha.WorldGen.world.structure.vanilla.VanillaStructureSearchService.VanillaStructureType;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatMessageComponent;
import net.minecraft.EntityPlayer;
import net.minecraft.ICommandSender;
import net.minecraft.World;
import net.xiaoyu233.fml.reload.event.HandleChatCommandEvent;

public final class StructureCompassCommandHandler {
    private static final String COMMAND = "wgs";
    private static final int SEARCH_RADIUS = 20000;
    private static final int MAX_RESULTS = 5;
    private static final int MAX_LISTED_CUSTOM_NAMES = 20;

    private StructureCompassCommandHandler() {
    }

    private static String t(String key, String fallback) {
        return I18n.tr(key, fallback);
    }

    private static String tf(String key, String fallbackFormat, Object... args) {
        return I18n.trf(key, fallbackFormat, args);
    }

    public static void handleChatCommand(HandleChatCommandEvent event) {
        if (event == null) {
            return;
        }

        String rawCommand = event.getCommand();
        if (rawCommand == null) {
            return;
        }

        String commandLine = rawCommand.trim();
        if (commandLine.startsWith("/")) {
            commandLine = commandLine.substring(1).trim();
        }
        if (commandLine.isEmpty()) {
            return;
        }

        String normalized = commandLine.toLowerCase(Locale.ROOT);
        if (!normalized.equals(COMMAND) && !normalized.startsWith(COMMAND + " ")) {
            return;
        }

        event.setExecuteSuccess(true);

        EntityPlayer player = event.getPlayer();
        ICommandSender sender = event.getListener();
        if (player == null) {
            sendMessage(sender, t(
                    "worldgen.command.structure_compass.only_player",
                    "This command can only be executed by players."));
            return;
        }

        World world = event.getWorld() != null ? event.getWorld() : player.worldObj;
        if (world == null) {
            sendMessage(player, t(
                    "worldgen.command.structure_compass.world_unavailable",
                    "Current world is unavailable, cannot search structures."), true);
            return;
        }

        String query = commandLine.length() > COMMAND.length()
                ? commandLine.substring(COMMAND.length()).trim()
                : "";
        if (query.isEmpty() || isListQuery(query)) {
            sendQueryableList(player, world);
            return;
        }

        VanillaStructureType vanillaType = VanillaStructureSearchService.matchType(query);
        if (vanillaType != null) {
            handleVanillaStructureSearch(player, world, vanillaType);
            return;
        }

        handleCustomStructureSearch(player, world, query);
    }

    private static void handleVanillaStructureSearch(EntityPlayer player, World world, VanillaStructureType structureType) {
        if (!structureType.supportsDimension(world.getDimensionId())) {
            sendMessage(
                    player,
                    tf(
                            "worldgen.command.structure_compass.vanilla.unsupported_dimension",
                            "Vanilla structure %s does not generate in current dimension. Supported dimensions: %s",
                            structureType.displayLabel(),
                            structureType.supportedDimensionLabel()),
                    true);
            return;
        }

        List<VanillaStructureResult> results = VanillaStructureSearchService.findNearby(
                world,
                structureType,
                player.posX,
                player.posZ,
                SEARCH_RADIUS,
                MAX_RESULTS);

        if (results.isEmpty()) {
            sendMessage(
                    player,
                    tf(
                            "worldgen.command.structure_compass.vanilla.not_found",
                            "No vanilla structure found within %d blocks: %s",
                            SEARCH_RADIUS,
                            structureType.displayLabel()),
                    true);
            return;
        }

        sendMessage(
                player,
                tf(
                        "worldgen.command.structure_compass.vanilla.found",
                        "Found %d vanilla structures (<= %d blocks): %s",
                        results.size(),
                        SEARCH_RADIUS,
                        structureType.displayLabel()),
                true);
        for (int i = 0; i < results.size(); ++i) {
            VanillaStructureResult result = results.get(i);
            int distance = (int) Math.round(Math.sqrt(result.distanceSq()));
            String line = tf(
                    "worldgen.command.structure_compass.result_line",
                    "%d) %s  Pos: (%d, %d, %d)  Dist: %d",
                    i + 1,
                    result.structureType().displayLabel(),
                    result.x(),
                    result.y(),
                    result.z(),
                    distance);
            sendMessage(player, line);
        }
    }

    private static void handleCustomStructureSearch(EntityPlayer player, World world, String query) {
        List<StructureGenerationDataStore.StructureSearchResult> results = StructureGenerationDataStore.findNearby(
                world,
                world.getDimensionId(),
                player.posX,
                player.posZ,
                query,
                SEARCH_RADIUS,
                MAX_RESULTS);

        if (results.isEmpty()) {
            sendMessage(
                    player,
                    tf(
                            "worldgen.command.structure_compass.custom.not_found",
                            "No structure found within %d blocks: %s",
                            SEARCH_RADIUS,
                            query),
                    true);
            int totalRecords = StructureGenerationDataStore.countAllRecords(world);
            int currentDimensionRecords = StructureGenerationDataStore.countRecordsInDimension(world, world.getDimensionId());
            if (totalRecords == 0) {
                sendMessage(player, t(
                        "worldgen.command.structure_compass.custom.no_records",
                        "No custom structure record exists in this save yet."));
                sendMessage(player, t(
                        "worldgen.command.structure_compass.custom.records_note",
                        "Records are written only when this mod successfully generates .schematic structures (no backfill)."));
            } else if (currentDimensionRecords == 0) {
                sendMessage(player, t(
                        "worldgen.command.structure_compass.custom.none_in_dimension",
                        "No custom structure record in current dimension. Switch dimension or generate structures first."));
            }
            sendMessage(player, t(
                    "worldgen.command.structure_compass.custom.tip_list",
                    "Type \"list\" to show queryable structures (including vanilla)."));
            return;
        }

        sendMessage(
                player,
                tf(
                        "worldgen.command.structure_compass.custom.found",
                        "Found %d custom structures (<= %d blocks): %s",
                        results.size(),
                        SEARCH_RADIUS,
                        query),
                true);
        for (int i = 0; i < results.size(); ++i) {
            StructureGenerationDataStore.StructureSearchResult result = results.get(i);
            StructureGenerationDataStore.StructureRecord record = result.record();
            int distance = (int) Math.round(Math.sqrt(result.distanceSq()));
            String line = tf(
                    "worldgen.command.structure_compass.result_line",
                    "%d) %s  Pos: (%d, %d, %d)  Dist: %d",
                    i + 1,
                    record.structureName(),
                    record.x(),
                    record.y(),
                    record.z(),
                    distance);
            sendMessage(player, line);
        }
    }

    private static void sendQueryableList(EntityPlayer player, World world) {
        List<VanillaStructureType> currentDimensionTypes =
                VanillaStructureSearchService.listTypesForDimension(world.getDimensionId());
        if (currentDimensionTypes.isEmpty()) {
            sendMessage(player, t(
                    "worldgen.command.structure_compass.list.current_vanilla.none",
                    "No queryable vanilla structure type in current dimension."), true);
        } else {
            sendMessage(player, t(
                    "worldgen.command.structure_compass.list.current_vanilla.title",
                    "Queryable vanilla structures in current dimension:"), true);
            for (VanillaStructureType type : currentDimensionTypes) {
                int known = VanillaStructureSearchService.countKnown(world, type);
                sendMessage(player, tf(
                        "worldgen.command.structure_compass.list.vanilla.item_known",
                        "- %s | Discovered: %d",
                        type.displayLabel(),
                        known));
            }
        }

        sendMessage(player, t(
                "worldgen.command.structure_compass.list.all_vanilla.title",
                "All vanilla structure types (version-fixed):"));
        for (VanillaStructureType type : VanillaStructureSearchService.listTypes()) {
            sendMessage(player, tf(
                    "worldgen.command.structure_compass.list.vanilla.item_dimension",
                    "- %s | Dimensions: %s",
                    type.displayLabel(),
                    type.supportedDimensionLabel()));
        }

        List<String> customNames =
                StructureGenerationDataStore.listStructureNames(world, world.getDimensionId(), MAX_LISTED_CUSTOM_NAMES);
        if (customNames.isEmpty()) {
            sendMessage(player, t(
                    "worldgen.command.structure_compass.list.current_custom.none",
                    "No recorded custom structure names in current dimension."));
        } else {
            sendMessage(player, t(
                    "worldgen.command.structure_compass.list.current_custom.title",
                    "Queryable custom structure names in current dimension:"));
            for (String name : customNames) {
                sendMessage(player, tf(
                        "worldgen.command.structure_compass.list.custom.item",
                        "- %s",
                        name));
            }
        }
    }

    private static boolean isListQuery(String query) {
        if (query == null) {
            return false;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        String listZh = t("worldgen.command.structure_compass.query.alias.list_zh", "list").toLowerCase(Locale.ROOT);
        String queryableZh = t("worldgen.command.structure_compass.query.alias.queryable_zh", "queryable").toLowerCase(Locale.ROOT);
        String helpZh = t("worldgen.command.structure_compass.query.alias.help_zh", "help").toLowerCase(Locale.ROOT);
        return normalized.equals("list")
                || normalized.equals("help")
                || normalized.equals("?")
                || normalized.equals(listZh)
                || normalized.equals(queryableZh)
                || normalized.equals(helpZh);
    }

    private static void sendMessage(ICommandSender sender, String message) {
        sendMessage(sender, message, false);
    }

    private static void sendMessage(ICommandSender sender, String message, boolean withPrefix) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        String finalMessage = withPrefix
                ? tf("worldgen.command.structure_compass.prefix", "[Structure Compass] %s", message)
                : message;
        sender.sendChatToPlayer(ChatMessageComponent.createFromText(finalMessage));
    }
}
