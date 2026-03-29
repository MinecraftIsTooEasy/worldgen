package com.github.hahahha.WorldGenLib.client.gui;

import com.github.hahahha.WorldGenLib.util.I18n;
import com.github.hahahha.WorldGenLib.util.LruCache;
import com.github.hahahha.WorldGenLib.util.StringNormalization;
import com.github.hahahha.WorldGenLib.world.structure.StructureCompassCommandHandler;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibApi;
import com.github.hahahha.WorldGenLib.world.structure.api.StructureWorldGenLibConfigFileApi;
import com.github.hahahha.WorldGenLib.world.structure.config.StructureWorldGenLibPlayerConfigLoader;
import com.github.hahahha.WorldGenLib.world.structure.vanilla.VanillaStructureSearchService;
import com.github.hahahha.WorldGenLib.world.structure.vanilla.VanillaStructureSearchService.VanillaStructureType;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.GuiButton;
import net.minecraft.GuiScreen;
import net.minecraft.GuiTextField;
import net.xiaoyu233.fml.FishModLoader;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiStructureCompassSearch extends GuiScreen {
    private static final int BUTTON_CUSTOM_SEARCH = 1;
    private static final int BUTTON_LIST = 2;
    private static final int BUTTON_CANCEL = 3;
    private static final int BUTTON_VANILLA_PRESET_BASE = 100;
    private static final int BUTTON_CUSTOM_PRESET_BASE = 300;

    private static final int MAX_CUSTOM_BUTTONS = 80;
    private static final int SCROLL_COLUMNS = 2;
    private static final int SCROLL_BUTTON_WIDTH = 150;
    private static final int SCROLL_ROW_HEIGHT = 22;
    private static final int SCROLL_LABEL_TO_BUTTON_GAP = 10;
    private static final int SCROLL_GROUP_GAP = 12;
    private static final int SCROLL_STEP = 22;
    private static final int SCROLL_MIN_VIEWPORT_HEIGHT = 44;
    private static final int FOOTER_RESERVED_HEIGHT = 82;
    private static final int MOD_DISPLAY_NAME_CACHE_MAX_SIZE = 512;
    private static final File DEFAULT_CONFIG_FILE =
            new File("config", StructureWorldGenLibPlayerConfigLoader.DEFAULT_FILE_NAME);
    private static final Object CUSTOM_OPTIONS_CACHE_LOCK = new Object();
    private static long cachedCustomOptionsLastModified = Long.MIN_VALUE;
    private static long cachedCustomOptionsLength = Long.MIN_VALUE;
    private static final Map<Integer, List<CustomStructureOption>> cachedConfigOptionsByDimension =
            new HashMap<Integer, List<CustomStructureOption>>(8);
    private static final LruCache<String, String> MOD_DISPLAY_NAME_CACHE =
            new LruCache<String, String>(MOD_DISPLAY_NAME_CACHE_MAX_SIZE);

    private final List<PresetOption> vanillaPresetOptions = new ArrayList<PresetOption>(16);
    private final List<PresetOption> customPresetOptions = new ArrayList<PresetOption>(MAX_CUSTOM_BUTTONS);
    private final List<ScrollSectionLabel> scrollSectionLabels = new ArrayList<ScrollSectionLabel>(16);
    private final List<ScrollableButton> scrollableButtons = new ArrayList<ScrollableButton>(MAX_CUSTOM_BUTTONS + 32);

    private GuiTextField inputField;
    private int currentDimensionId = Integer.MIN_VALUE;
    private int listSectionY;
    private int footerHintY;

    private int panelLeft;
    private int panelRight;
    private int viewportTop;
    private int viewportBottom;
    private int contentHeight;
    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;
    private int scrollbarDragYOffset;
    private String titleText;
    private String currentDimensionText;
    private String queryableSectionText;
    private String footerHintText;

    private static String t(String key, String fallback) {
        return I18n.tr(key, fallback);
    }

    private static String tf(String key, String fallbackFormat, Object... args) {
        return I18n.trf(key, fallbackFormat, args);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.vanillaPresetOptions.clear();
        this.customPresetOptions.clear();
        this.scrollSectionLabels.clear();
        this.scrollableButtons.clear();

        this.draggingScrollbar = false;
        this.scrollOffset = 0;
        this.maxScroll = 0;
        this.contentHeight = 0;

        this.currentDimensionId = resolveCurrentDimensionId();
        this.titleText = t("WorldGenLib.gui.structure_compass.title", "Structure Compass");
        this.currentDimensionText = tf(
                "WorldGenLib.gui.structure_compass.current_dimension",
                "Current Dimension: %s",
                getDimensionLabel(this.currentDimensionId));
        this.queryableSectionText =
                t("WorldGenLib.gui.structure_compass.section.queryable", "Queryable Structures:");
        this.footerHintText = t(
                "WorldGenLib.gui.structure_compass.hint",
                "If no button appears, input a structure name below.");
        List<VanillaStructureType> vanillaTypes = this.currentDimensionId == Integer.MIN_VALUE
                ? VanillaStructureSearchService.listTypes()
                : VanillaStructureSearchService.listTypesForDimension(this.currentDimensionId);
        for (VanillaStructureType type : vanillaTypes) {
            this.vanillaPresetOptions.add(new PresetOption(type.displayLabel(), type.id()));
        }

        Integer currentDimension = this.currentDimensionId == Integer.MIN_VALUE
                ? null
                : this.currentDimensionId;
        List<CustomStructureOption> customOptions = collectCustomStructureOptions(currentDimension);

        int startX = this.width / 2 - 155;
        this.panelLeft = startX;
        this.panelRight = startX + SCROLL_COLUMNS * 160;

        this.listSectionY = 36;
        this.viewportTop = this.listSectionY + 14;
        int viewportHeight = Math.max(SCROLL_MIN_VIEWPORT_HEIGHT, this.height - this.viewportTop - FOOTER_RESERVED_HEIGHT);
        this.viewportBottom = this.viewportTop + viewportHeight;

        int inputY = this.viewportBottom + 8;
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
                t("WorldGenLib.gui.structure_compass.button.search", "Search Input")));
        this.buttonList.add(new GuiButton(
                BUTTON_LIST,
                this.width / 2 - 50,
                actionY,
                100,
                20,
                t("WorldGenLib.gui.structure_compass.button.list", "Queryable List")));
        this.buttonList.add(new GuiButton(
                BUTTON_CANCEL,
                this.width / 2 + 55,
                actionY,
                100,
                20,
                t("WorldGenLib.gui.structure_compass.button.cancel", "Cancel")));
        this.footerHintY = actionY + 28;

        buildScrollableContent(customOptions, startX, SCROLL_COLUMNS);
        updateScrollLayout();
    }

    @SuppressWarnings("unchecked")
    private void buildScrollableContent(List<CustomStructureOption> customOptions, int startX, int columns) {
        int contentCursor = 0;

        if (!this.vanillaPresetOptions.isEmpty()) {
            this.scrollSectionLabels.add(new ScrollSectionLabel(
                    t("WorldGenLib.gui.structure_compass.section.vanilla", "Vanilla Structures:"),
                    contentCursor,
                    0xFFE0A0));
            int buttonsTop = contentCursor + SCROLL_LABEL_TO_BUTTON_GAP;
            for (int i = 0; i < this.vanillaPresetOptions.size(); ++i) {
                int row = i / columns;
                int col = i % columns;
                int x = startX + col * 160;
                int y = buttonsTop + row * SCROLL_ROW_HEIGHT;
                PresetOption option = this.vanillaPresetOptions.get(i);
                GuiButton button = new GuiButton(
                        BUTTON_VANILLA_PRESET_BASE + i,
                        x,
                        -1000,
                        SCROLL_BUTTON_WIDTH,
                        20,
                        option.label);
                this.buttonList.add(button);
                this.scrollableButtons.add(new ScrollableButton(button, x, y));
            }
            int rows = (this.vanillaPresetOptions.size() + columns - 1) / columns;
            contentCursor = buttonsTop + rows * SCROLL_ROW_HEIGHT + SCROLL_GROUP_GAP;
        }

        LinkedHashMap<String, List<PresetOption>> groupedCustom =
                new LinkedHashMap<String, List<PresetOption>>(Math.max(4, customOptions.size()));
        for (CustomStructureOption option : customOptions) {
            if (option == null || option.structureName == null) {
                continue;
            }
            String trimmed = StringNormalization.trimToNull(option.structureName);
            if (trimmed == null) {
                continue;
            }
            String sourceKey = normalizeSourceMod(option.sourceMod);
            String groupKey = sourceKey == null ? "" : sourceKey;
            List<PresetOption> list =
                    groupedCustom.computeIfAbsent(groupKey, ignored -> new ArrayList<PresetOption>(8));
            list.add(new PresetOption(trimmed, trimmed));
        }

        if (!groupedCustom.isEmpty()) {
            this.scrollSectionLabels.add(new ScrollSectionLabel(
                    t("WorldGenLib.gui.structure_compass.section.custom", "Custom Structures:"),
                    contentCursor,
                    0xFFE0A0));
            contentCursor += SCROLL_LABEL_TO_BUTTON_GAP;

            for (Map.Entry<String, List<PresetOption>> entry : groupedCustom.entrySet()) {
                List<PresetOption> list = entry.getValue();
                if (list == null || list.isEmpty()) {
                    continue;
                }

                String groupLabel = entry.getKey().isEmpty()
                        ? t("WorldGenLib.gui.structure_compass.custom_source_unknown", "Config")
                        : formatModSourceLabel(entry.getKey());
                this.scrollSectionLabels.add(new ScrollSectionLabel(groupLabel, contentCursor, 0xB0D8FF));

                int buttonsTop = contentCursor + SCROLL_LABEL_TO_BUTTON_GAP;
                for (int i = 0; i < list.size(); ++i) {
                    int row = i / columns;
                    int col = i % columns;
                    int x = startX + col * 160;
                    int y = buttonsTop + row * SCROLL_ROW_HEIGHT;
                    PresetOption option = list.get(i);
                    int index = this.customPresetOptions.size();
                    this.customPresetOptions.add(option);

                    GuiButton button = new GuiButton(
                            BUTTON_CUSTOM_PRESET_BASE + index,
                            x,
                            -1000,
                            SCROLL_BUTTON_WIDTH,
                            20,
                            option.label);
                    this.buttonList.add(button);
                    this.scrollableButtons.add(new ScrollableButton(button, x, y));
                }

                int rows = (list.size() + columns - 1) / columns;
                contentCursor = buttonsTop + rows * SCROLL_ROW_HEIGHT + SCROLL_GROUP_GAP;
            }
        }

        this.contentHeight = Math.max(0, contentCursor);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        this.draggingScrollbar = false;
    }

    @Override
    public void updateScreen() {
        if (this.inputField != null) {
            this.inputField.updateCursorCounter();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || this.maxScroll <= 0) {
            return;
        }

        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (!isMouseInsideViewport(mouseX, mouseY)) {
            return;
        }

        int delta = wheel < 0 ? SCROLL_STEP : -SCROLL_STEP;
        setScrollOffset(this.scrollOffset + delta);
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
        if (mouseButton == 0 && isMouseOnScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            this.scrollbarDragYOffset = mouseY - getScrollbarThumbTop();
            setScrollFromScrollbar(mouseY - this.scrollbarDragYOffset);
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.inputField != null) {
            this.inputField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.draggingScrollbar && clickedMouseButton == 0) {
            setScrollFromScrollbar(mouseY - this.scrollbarDragYOffset);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            this.draggingScrollbar = false;
        }
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
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
                this.titleText == null
                        ? t("WorldGenLib.gui.structure_compass.title", "Structure Compass")
                        : this.titleText,
                this.width / 2,
                10,
                0xFFFFFF);
        this.drawCenteredString(
                this.fontRenderer,
                this.currentDimensionText == null
                        ? tf(
                        "WorldGenLib.gui.structure_compass.current_dimension",
                        "Current Dimension: %s",
                        getDimensionLabel(this.currentDimensionId))
                        : this.currentDimensionText,
                this.width / 2,
                24,
                0xA0A0A0);
        this.drawString(
                this.fontRenderer,
                this.queryableSectionText == null
                        ? t("WorldGenLib.gui.structure_compass.section.queryable", "Queryable Structures:")
                        : this.queryableSectionText,
                this.width / 2 - 155,
                this.listSectionY,
                0xFFE0A0);

        drawRect(this.panelLeft - 1, this.viewportTop - 1, this.panelRight + 1, this.viewportBottom + 1, 0x70404040);
        for (ScrollSectionLabel label : this.scrollSectionLabels) {
            int drawY = this.viewportTop + label.contentY - this.scrollOffset;
            if (drawY < this.viewportTop || drawY + this.fontRenderer.FONT_HEIGHT > this.viewportBottom) {
                continue;
            }
            this.drawString(this.fontRenderer, label.text, this.panelLeft, drawY, label.color);
        }
        drawScrollbar();

        this.drawCenteredString(
                this.fontRenderer,
                this.footerHintText == null
                        ? t(
                        "WorldGenLib.gui.structure_compass.hint",
                        "If no button appears, input a structure name below.")
                        : this.footerHintText,
                this.width / 2,
                this.footerHintY,
                0xA0A0A0);
        if (this.inputField != null) {
            this.inputField.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScrollbar() {
        int left = getScrollbarLeft();
        int right = getScrollbarRight();
        drawRect(left, this.viewportTop, right, this.viewportBottom, 0x70404040);

        int thumbTop = getScrollbarThumbTop();
        int thumbBottom = thumbTop + getScrollbarThumbHeight();
        drawRect(left + 1, thumbTop, right - 1, thumbBottom, this.maxScroll > 0 ? 0xC0B0D8FF : 0x90505050);
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
        String normalizedQuery = StringNormalization.trimToNull(query);
        if (normalizedQuery == null) {
            normalizedQuery = "";
        }
        if (normalizedQuery.isEmpty()) {
            if (this.mc != null && this.mc.thePlayer != null) {
                this.mc.thePlayer.addChatMessage(t(
                        "WorldGenLib.gui.structure_compass.input_required",
                        "[Structure Compass] Please input a structure name."));
            }
            return;
        }
        sendQuery(normalizedQuery);
    }

    private void sendQuery(String query) {
        if (this.mc == null || this.mc.thePlayer == null || query == null) {
            return;
        }
        if (query.isEmpty()) {
            return;
        }
        this.mc.displayGuiScreen(null);
        this.mc.thePlayer.sendChatMessage("/" + StructureCompassCommandHandler.commandLiteral() + " " + query);
    }

    private int resolveCurrentDimensionId() {
        if (this.mc != null && this.mc.theWorld != null) {
            return this.mc.theWorld.getDimensionId();
        }
        return Integer.MIN_VALUE;
    }

    private static String getDimensionLabel(int dimensionId) {
        if (dimensionId == -1) {
            return t("WorldGenLib.dimension.nether", "Nether");
        }
        if (dimensionId == 0) {
            return t("WorldGenLib.dimension.overworld", "Overworld");
        }
        if (dimensionId == 1) {
            return t("WorldGenLib.dimension.end", "The End");
        }
        return tf("WorldGenLib.dimension.unknown", "Unknown(%d)", dimensionId);
    }

    private static String formatModSourceLabel(String modId) {
        String displayName = resolveModDisplayName(modId);
        if (displayName == null || displayName.isEmpty()) {
            displayName = modId;
        }
        return tf("WorldGenLib.gui.structure_compass.custom_source_mod_prefix", "MOD：%s", displayName);
    }

    private static String resolveModDisplayName(String modId) {
        if (modId == null) {
            return null;
        }
        String cached = MOD_DISPLAY_NAME_CACHE.get(modId);
        if (cached != null) {
            return cached;
        }

        String resolved;
        try {
            resolved = FishModLoader.getModContainer(modId)
                    .map(container -> container.getMetadata().getName())
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .orElse(modId);
        } catch (RuntimeException | LinkageError ignored) {
            resolved = modId;
        }
        String previous = MOD_DISPLAY_NAME_CACHE.putIfAbsent(modId, resolved);
        return previous != null ? previous : resolved;
    }

    private static String normalizeSourceMod(String value) {
        return StringNormalization.trimLowerToNull(value);
    }

    private static List<CustomStructureOption> collectCustomStructureOptions(Integer dimensionId) {
        LinkedHashMap<CustomStructureIdentityKey, CustomStructureOption> merged =
                new LinkedHashMap<CustomStructureIdentityKey, CustomStructureOption>(MAX_CUSTOM_BUTTONS);

        List<StructureWorldGenLibApi.RegisteredStructureOption> apiOptions =
                StructureWorldGenLibApi.listRegisteredStructures(MAX_CUSTOM_BUTTONS, dimensionId);
        for (StructureWorldGenLibApi.RegisteredStructureOption option : apiOptions) {
            if (option == null) {
                continue;
            }
            addCustomOption(merged, option.structureName(), option.sourceMod());
            if (merged.size() >= MAX_CUSTOM_BUTTONS) {
                return new ArrayList<CustomStructureOption>(merged.values());
            }
        }

        List<CustomStructureOption> configOptions = cachedConfiguredStructureOptions(dimensionId);
        for (CustomStructureOption option : configOptions) {
            if (option == null) {
                continue;
            }
            addCustomOption(merged, option.structureName, option.sourceMod);
            if (merged.size() >= MAX_CUSTOM_BUTTONS) {
                break;
            }
        }

        if (merged.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<CustomStructureOption>(merged.values());
    }

    private static List<CustomStructureOption> cachedConfiguredStructureOptions(Integer dimensionId) {
        int dimensionKey = dimensionId == null ? Integer.MIN_VALUE : dimensionId.intValue();
        Integer dimensionKeyBoxed = dimensionKey;
        boolean configExists = DEFAULT_CONFIG_FILE.isFile();
        long lastModified = configExists ? DEFAULT_CONFIG_FILE.lastModified() : -1L;
        long length = configExists ? DEFAULT_CONFIG_FILE.length() : -1L;

        synchronized (CUSTOM_OPTIONS_CACHE_LOCK) {
            if (lastModified != cachedCustomOptionsLastModified || length != cachedCustomOptionsLength) {
                cachedCustomOptionsLastModified = lastModified;
                cachedCustomOptionsLength = length;
                cachedConfigOptionsByDimension.clear();
            }

            List<CustomStructureOption> cached = cachedConfigOptionsByDimension.get(dimensionKeyBoxed);
            if (cached != null) {
                return cached;
            }
        }

        List<StructureWorldGenLibConfigFileApi.ConfiguredStructureOption> options =
                StructureWorldGenLibConfigFileApi.listConfiguredStructures(MAX_CUSTOM_BUTTONS, dimensionId);
        if (options == null || options.isEmpty()) {
            synchronized (CUSTOM_OPTIONS_CACHE_LOCK) {
                List<CustomStructureOption> empty = Collections.emptyList();
                cachedConfigOptionsByDimension.put(dimensionKeyBoxed, empty);
                return empty;
            }
        }

        List<CustomStructureOption> converted = new ArrayList<CustomStructureOption>(options.size());
        for (StructureWorldGenLibConfigFileApi.ConfiguredStructureOption option : options) {
            if (option == null) {
                continue;
            }
            String name = option.structureName();
            if (name == null) {
                continue;
            }
            String trimmedName = StringNormalization.trimToNull(name);
            if (trimmedName == null) {
                continue;
            }
            converted.add(new CustomStructureOption(trimmedName, normalizeSourceMod(option.sourceMod())));
            if (converted.size() >= MAX_CUSTOM_BUTTONS) {
                break;
            }
        }

        List<CustomStructureOption> finalOptions = converted.isEmpty()
                ? Collections.<CustomStructureOption>emptyList()
                : Collections.unmodifiableList(converted);
        synchronized (CUSTOM_OPTIONS_CACHE_LOCK) {
            cachedConfigOptionsByDimension.put(dimensionKeyBoxed, finalOptions);
        }
        return finalOptions;
    }

    private static void addCustomOption(
            Map<CustomStructureIdentityKey, CustomStructureOption> merged,
            String structureName,
            String sourceMod) {
        if (merged == null || structureName == null) {
            return;
        }
        String trimmedName = StringNormalization.trimToNull(structureName);
        if (trimmedName == null) {
            return;
        }
        String normalizedSource = normalizeSourceMod(sourceMod);
        CustomStructureIdentityKey uniqueKey = new CustomStructureIdentityKey(normalizedSource, trimmedName);
        merged.putIfAbsent(uniqueKey, new CustomStructureOption(trimmedName, normalizedSource));
    }

    private void updateScrollLayout() {
        int viewportHeight = Math.max(0, this.viewportBottom - this.viewportTop);
        this.maxScroll = Math.max(0, this.contentHeight - viewportHeight);
        this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll);

        for (ScrollableButton entry : this.scrollableButtons) {
            GuiButton button = entry.button;
            int y = this.viewportTop + entry.contentY - this.scrollOffset;
            boolean visible = y >= this.viewportTop && y + 20 <= this.viewportBottom;
            button.xPosition = entry.baseX;
            button.yPosition = visible ? y : -1000;
            button.enabled = visible;
        }
    }

    private void setScrollOffset(int offset) {
        this.scrollOffset = clamp(offset, 0, this.maxScroll);
        updateScrollLayout();
    }

    private void setScrollFromScrollbar(int thumbTop) {
        if (this.maxScroll <= 0) {
            setScrollOffset(0);
            return;
        }

        int minTop = this.viewportTop;
        int maxTop = this.viewportBottom - getScrollbarThumbHeight();
        int clampedTop = clamp(thumbTop, minTop, maxTop);
        int travel = Math.max(1, maxTop - minTop);
        int offset = (int) Math.round((double) (clampedTop - minTop) * (double) this.maxScroll / (double) travel);
        setScrollOffset(offset);
    }

    private boolean isMouseInsideViewport(int mouseX, int mouseY) {
        return mouseX >= this.panelLeft
                && mouseX <= this.panelRight
                && mouseY >= this.viewportTop
                && mouseY <= this.viewportBottom;
    }

    private boolean isMouseOnScrollbar(int mouseX, int mouseY) {
        return this.maxScroll > 0
                && mouseX >= getScrollbarLeft()
                && mouseX <= getScrollbarRight()
                && mouseY >= this.viewportTop
                && mouseY <= this.viewportBottom;
    }

    private int getScrollbarLeft() {
        return this.panelRight + 4;
    }

    private int getScrollbarRight() {
        return getScrollbarLeft() + 6;
    }

    private int getScrollbarThumbHeight() {
        int viewportHeight = Math.max(1, this.viewportBottom - this.viewportTop);
        if (this.maxScroll <= 0 || this.contentHeight <= 0) {
            return viewportHeight;
        }
        int scaled = (viewportHeight * viewportHeight) / Math.max(1, this.contentHeight);
        return clamp(scaled, 12, viewportHeight);
    }

    private int getScrollbarThumbTop() {
        if (this.maxScroll <= 0) {
            return this.viewportTop;
        }
        int minTop = this.viewportTop;
        int maxTop = this.viewportBottom - getScrollbarThumbHeight();
        int travel = Math.max(1, maxTop - minTop);
        return minTop + (int) Math.round((double) this.scrollOffset * (double) travel / (double) this.maxScroll);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static final class PresetOption {
        private final String label;
        private final String query;

        private PresetOption(String label, String query) {
            this.label = label;
            this.query = query;
        }
    }

    private static final class CustomStructureOption {
        private final String structureName;
        private final String sourceMod;

        private CustomStructureOption(String structureName, String sourceMod) {
            this.structureName = structureName;
            this.sourceMod = sourceMod;
        }
    }

    private static final class CustomStructureIdentityKey {
        private final String sourceMod;
        private final String normalizedName;

        private CustomStructureIdentityKey(String sourceMod, String structureName) {
            this.sourceMod = sourceMod == null ? "" : sourceMod;
            String normalized = StringNormalization.trimLowerToNull(structureName);
            this.normalizedName = normalized == null ? "" : normalized;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CustomStructureIdentityKey)) {
                return false;
            }
            CustomStructureIdentityKey other = (CustomStructureIdentityKey) obj;
            return this.sourceMod.equals(other.sourceMod)
                    && this.normalizedName.equals(other.normalizedName);
        }

        @Override
        public int hashCode() {
            int result = this.sourceMod.hashCode();
            result = 31 * result + this.normalizedName.hashCode();
            return result;
        }
    }

    private static final class ScrollSectionLabel {
        private final String text;
        private final int contentY;
        private final int color;

        private ScrollSectionLabel(String text, int contentY, int color) {
            this.text = text;
            this.contentY = contentY;
            this.color = color;
        }
    }

    private static final class ScrollableButton {
        private final GuiButton button;
        private final int baseX;
        private final int contentY;

        private ScrollableButton(GuiButton button, int baseX, int contentY) {
            this.button = button;
            this.baseX = baseX;
            this.contentY = contentY;
        }
    }
}
