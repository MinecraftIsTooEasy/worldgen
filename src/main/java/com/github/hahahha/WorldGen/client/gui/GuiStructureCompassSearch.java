package com.github.hahahha.WorldGen.client.gui;

import com.github.hahahha.WorldGen.util.I18n;
import com.github.hahahha.WorldGen.world.structure.config.StructureWorldgenPlayerConfigLoader;
import com.github.hahahha.WorldGen.world.structure.vanilla.VanillaStructureSearchService;
import com.github.hahahha.WorldGen.world.structure.vanilla.VanillaStructureSearchService.VanillaStructureType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.GuiButton;
import net.minecraft.GuiScreen;
import net.minecraft.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiStructureCompassSearch extends GuiScreen {
    private static final int BUTTON_CUSTOM_SEARCH = 1;
    private static final int BUTTON_LIST = 2;
    private static final int BUTTON_CANCEL = 3;
    private static final int BUTTON_VANILLA_PRESET_BASE = 100;
    private static final int BUTTON_CUSTOM_PRESET_BASE = 300;
    private static final int MAX_CUSTOM_BUTTONS = 10;

    private final List<PresetOption> vanillaPresetOptions = new ArrayList<PresetOption>();
    private final List<PresetOption> customPresetOptions = new ArrayList<PresetOption>();
    private GuiTextField inputField;
    private int currentDimensionId = Integer.MIN_VALUE;
    private int vanillaSectionY;
    private int customSectionY;
    private int footerHintY;

    private static String t(String key, String fallback) {
        return I18n.tr(key, fallback);
    }

    private static String tf(String key, String fallbackFormat, Object... args) {
        return I18n.trf(key, fallbackFormat, args);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.vanillaPresetOptions.clear();
        this.customPresetOptions.clear();

        this.currentDimensionId = resolveCurrentDimensionId();
        List<VanillaStructureType> vanillaTypes = this.currentDimensionId == Integer.MIN_VALUE
                ? VanillaStructureSearchService.listTypes()
                : VanillaStructureSearchService.listTypesForDimension(this.currentDimensionId);
        for (VanillaStructureType type : vanillaTypes) {
            this.vanillaPresetOptions.add(new PresetOption(type.displayLabel(), type.id()));
        }

        Integer currentDimension = this.currentDimensionId == Integer.MIN_VALUE
                ? null
                : Integer.valueOf(this.currentDimensionId);
        List<String> configuredNames = StructureWorldgenPlayerConfigLoader
                .listConfiguredStructureNames(MAX_CUSTOM_BUTTONS, currentDimension);
        for (String name : configuredNames) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            this.customPresetOptions.add(new PresetOption(
                    tf("worldgen.gui.structure_compass.custom_prefix", "[Custom] %s", trimmed),
                    trimmed));
        }

        int startX = this.width / 2 - 155;
        int startY = Math.max(30, this.height / 2 - 100);
        int buttonWidth = 150;
        int rowHeight = 22;
        int columns = 2;

        this.vanillaSectionY = startY - 11;
        for (int i = 0; i < this.vanillaPresetOptions.size(); ++i) {
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * 160;
            int y = startY + row * rowHeight;
            this.buttonList.add(new GuiButton(
                    BUTTON_VANILLA_PRESET_BASE + i,
                    x,
                    y,
                    buttonWidth,
                    20,
                    this.vanillaPresetOptions.get(i).label));
        }
        int vanillaRows = (this.vanillaPresetOptions.size() + columns - 1) / columns;
        int afterVanillaY = startY + vanillaRows * rowHeight;

        this.customSectionY = afterVanillaY + 4;
        for (int i = 0; i < this.customPresetOptions.size(); ++i) {
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * 160;
            int y = afterVanillaY + 16 + row * rowHeight;
            this.buttonList.add(new GuiButton(
                    BUTTON_CUSTOM_PRESET_BASE + i,
                    x,
                    y,
                    buttonWidth,
                    20,
                    this.customPresetOptions.get(i).label));
        }
        int customRows = (this.customPresetOptions.size() + columns - 1) / columns;

        int inputY = afterVanillaY + 16 + customRows * rowHeight + 8;
        int inputWidth = 310;
        int inputHeight = 20;
        int inputX = this.width / 2 - inputWidth / 2;
        this.inputField = new GuiTextField(this.fontRenderer, inputX, inputY, inputWidth, inputHeight);
        this.inputField.setMaxStringLength(128);
        this.inputField.setFocused(true);
        this.inputField.setCanLoseFocus(false);

        int actionY = inputY + 26;
        this.buttonList.add(new GuiButton(
                BUTTON_CUSTOM_SEARCH,
                this.width / 2 - 155,
                actionY,
                100,
                20,
                t("worldgen.gui.structure_compass.button.search", "Search Input")));
        this.buttonList.add(new GuiButton(
                BUTTON_LIST,
                this.width / 2 - 50,
                actionY,
                100,
                20,
                t("worldgen.gui.structure_compass.button.list", "Queryable List")));
        this.buttonList.add(new GuiButton(
                BUTTON_CANCEL,
                this.width / 2 + 55,
                actionY,
                100,
                20,
                t("worldgen.gui.structure_compass.button.cancel", "Cancel")));
        this.footerHintY = actionY + 28;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (this.inputField != null) {
            this.inputField.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submitCustomSearch();
            return;
        }

        if (this.inputField != null && this.inputField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.inputField != null) {
            this.inputField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }

        if (button.id >= BUTTON_CUSTOM_PRESET_BASE) {
            int index = button.id - BUTTON_CUSTOM_PRESET_BASE;
            if (index >= 0 && index < this.customPresetOptions.size()) {
                sendQuery(this.customPresetOptions.get(index).query);
            }
            return;
        }

        if (button.id >= BUTTON_VANILLA_PRESET_BASE) {
            int index = button.id - BUTTON_VANILLA_PRESET_BASE;
            if (index >= 0 && index < this.vanillaPresetOptions.size()) {
                sendQuery(this.vanillaPresetOptions.get(index).query);
            }
            return;
        }

        if (button.id == BUTTON_CUSTOM_SEARCH) {
            submitCustomSearch();
            return;
        }
        if (button.id == BUTTON_LIST) {
            sendQuery("list");
            return;
        }
        if (button.id == BUTTON_CANCEL) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(
                this.fontRenderer,
                t("worldgen.gui.structure_compass.title", "Structure Compass"),
                this.width / 2,
                10,
                0xFFFFFF);
        this.drawCenteredString(
                this.fontRenderer,
                tf(
                        "worldgen.gui.structure_compass.current_dimension",
                        "Current Dimension: %s",
                        getDimensionLabel(this.currentDimensionId)),
                this.width / 2,
                24,
                0xA0A0A0);
        this.drawString(
                this.fontRenderer,
                t("worldgen.gui.structure_compass.section.vanilla", "Vanilla Structures:"),
                this.width / 2 - 155,
                this.vanillaSectionY,
                0xFFE0A0);
        this.drawString(
                this.fontRenderer,
                t("worldgen.gui.structure_compass.section.custom", "Custom Structures:"),
                this.width / 2 - 155,
                this.customSectionY,
                0xFFE0A0);
        this.drawCenteredString(
                this.fontRenderer,
                t(
                        "worldgen.gui.structure_compass.hint",
                        "If no button appears, input a structure name below."),
                this.width / 2,
                this.footerHintY,
                0xA0A0A0);
        if (this.inputField != null) {
            this.inputField.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void submitCustomSearch() {
        if (this.inputField == null) {
            return;
        }
        String query = this.inputField.getText();
        if (query != null) {
            query = query.trim();
        }
        if (query == null || query.isEmpty()) {
            if (this.mc != null && this.mc.thePlayer != null) {
                this.mc.thePlayer.addChatMessage(t(
                        "worldgen.gui.structure_compass.input_required",
                        "[Structure Compass] Please input a structure name."));
            }
            return;
        }
        sendQuery(query);
    }

    private void sendQuery(String query) {
        if (this.mc == null || this.mc.thePlayer == null || query == null || query.trim().isEmpty()) {
            return;
        }
        this.mc.displayGuiScreen(null);
        this.mc.thePlayer.sendChatMessage("/wgs " + query.trim());
    }

    private int resolveCurrentDimensionId() {
        if (this.mc != null && this.mc.theWorld != null) {
            return this.mc.theWorld.getDimensionId();
        }
        return Integer.MIN_VALUE;
    }

    private static String getDimensionLabel(int dimensionId) {
        if (dimensionId == -1) {
            return t("worldgen.dimension.nether", "Nether");
        }
        if (dimensionId == 0) {
            return t("worldgen.dimension.overworld", "Overworld");
        }
        if (dimensionId == 1) {
            return t("worldgen.dimension.end", "The End");
        }
        return tf("worldgen.dimension.unknown", "Unknown(%d)", dimensionId);
    }

    private static final class PresetOption {
        private final String label;
        private final String query;

        private PresetOption(String label, String query) {
            this.label = label;
            this.query = query;
        }
    }
}

