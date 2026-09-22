package com.example.smeltingoverhauled;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SmeltingConfigScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("smeltingoverhauled.json");

    public static boolean savedModEnabled = true;
    public static List<SmeltingOverhauled.FuelType> savedConfig = new ArrayList<>(Arrays.asList(SmeltingOverhauled.FuelType.values()));
    public static List<SmeltingOverhauled.FuelType> savedActiveFuels = new ArrayList<>(Arrays.asList(SmeltingOverhauled.FuelType.values()));

    static {
        loadConfigFile();
    }

    private boolean draftModEnabled;
    private final List<SmeltingOverhauled.FuelType> draftConfig;
    private final List<SmeltingOverhauled.FuelType> draftActiveFuels;

    private EditBox searchBox;
    private String searchQuery = "";

    // Smooth scrolling fields
    private float smoothScroll = 0f;
    private float targetScroll = 0f;
    private boolean isScrollDragging = false;

    // Hotkey Rebinding
    private boolean isRebindingKey = false;
    private Button rebindKeyButton;

    // Drag and Drop
    private int draggedFuelIndex = -1;
    private double currentMouseY = 0;
    private double currentMouseX = 0;
    private float dragHoldTime = 0f;

    private final List<AbstractWidget> dynamicRowWidgets = new ArrayList<>();
    private ScrollbarWidget scrollbarWidget;
    private RowOverlayWidget rowOverlayWidget;

    private static final int ROW_HEIGHT = 24;
    private static final int LIST_START_Y = 56;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int PAGE_STEP = 6;
    private static final int ROW_LEFT = 12;
    private static final int ROW_RIGHT = 250;

    public SmeltingConfigScreen() {
        super(Component.literal("Smelting Overhauled Config"));
        this.draftModEnabled = savedModEnabled;
        this.draftConfig = new ArrayList<>(savedConfig);
        this.draftActiveFuels = new ArrayList<>(savedActiveFuels);
    }

    public static class ModConfigFile {
        public boolean modEnabled = true;
        public List<String> fuelOrder = new ArrayList<>();
        public List<String> activeFuels = new ArrayList<>();
    }

    public static void loadConfigFile() {
        File file = CONFIG_PATH.toFile();
        if (!file.exists()) {
            saveConfigFile();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            ModConfigFile data = GSON.fromJson(reader, ModConfigFile.class);
            if (data != null) {
                savedModEnabled = data.modEnabled;

                if (data.fuelOrder != null && !data.fuelOrder.isEmpty()) {
                    List<SmeltingOverhauled.FuelType> loadedOrder = new ArrayList<>();
                    for (String name : data.fuelOrder) {
                        try {
                            loadedOrder.add(SmeltingOverhauled.FuelType.valueOf(name));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    for (SmeltingOverhauled.FuelType f : SmeltingOverhauled.FuelType.values()) {
                        if (!loadedOrder.contains(f)) {
                            loadedOrder.add(f);
                        }
                    }
                    savedConfig = loadedOrder;
                }

                if (data.activeFuels != null) {
                    List<SmeltingOverhauled.FuelType> loadedActive = new ArrayList<>();
                    for (String name : data.activeFuels) {
                        try {
                            loadedActive.add(SmeltingOverhauled.FuelType.valueOf(name));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    savedActiveFuels = loadedActive;
                }
            }
        } catch (Exception ignored) {}
    }

    public static void saveConfigFile() {
        try {
            File file = CONFIG_PATH.toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            ModConfigFile data = new ModConfigFile();
            data.modEnabled = savedModEnabled;

            for (SmeltingOverhauled.FuelType f : savedConfig) {
                data.fuelOrder.add(f.name());
                if (savedActiveFuels.contains(f)) {
                    data.activeFuels.add(f.name());
                }
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception ignored) {}
    }

    private static class ItemIconWidget extends AbstractWidget {
        private final ItemStack stack;

        public ItemIconWidget(int x, int y, ItemStack stack) {
            super(x, y, 16, 16, Component.empty());
            this.stack = stack;
            this.active = false;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.item(this.stack, this.getX(), this.getY());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
    }

    private class RowOverlayWidget extends AbstractWidget {
        public RowOverlayWidget(int width, int height) {
            super(0, 0, width, height, Component.empty());
            this.active = false;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            currentMouseX = mouseX;
            currentMouseY = mouseY;

            List<SmeltingOverhauled.FuelType> filtered = getFilteredFuels();
            int listBottomY = height - 36;

            // Hover Highlight
            if (draggedFuelIndex == -1 && mouseX >= ROW_LEFT && mouseX <= ROW_RIGHT && mouseY >= LIST_START_Y && mouseY < listBottomY) {
                int hoveredIndex = (int) ((mouseY - LIST_START_Y + smoothScroll) / ROW_HEIGHT);
                if (hoveredIndex >= 0 && hoveredIndex < filtered.size()) {
                    int rowY = Math.round(LIST_START_Y + (hoveredIndex * ROW_HEIGHT) - smoothScroll);
                    if (rowY >= LIST_START_Y && rowY + ROW_HEIGHT <= listBottomY + 4) {
                        graphics.fill(ROW_LEFT, rowY, ROW_RIGHT, rowY + ROW_HEIGHT - 2, 0x33FFFFFF);
                    }
                }
            }

            // Drag & Drop
            if (draggedFuelIndex != -1 && draggedFuelIndex < draftConfig.size()) {
                SmeltingOverhauled.FuelType dragged = draftConfig.get(draggedFuelIndex);

                int filterSourcePos = filtered.indexOf(dragged);
                if (filterSourcePos != -1) {
                    int origY = Math.round(LIST_START_Y + (filterSourcePos * ROW_HEIGHT) - smoothScroll);
                    if (origY >= LIST_START_Y && origY + ROW_HEIGHT <= listBottomY + 4) {
                        graphics.fill(ROW_LEFT, origY, ROW_RIGHT + 110, origY + ROW_HEIGHT - 2, 0x22000000);
                    }
                }

                int targetIndex = getTargetIndex(mouseY);
                int lineY = Math.round(LIST_START_Y + (targetIndex * ROW_HEIGHT) - smoothScroll);
                if (lineY >= LIST_START_Y && lineY <= listBottomY) {
                    graphics.fill(ROW_LEFT, lineY - 1, ROW_RIGHT + 110, lineY + 1, 0xFFFFFFFF);
                }

                graphics.fill(mouseX - 2, mouseY - 10, mouseX + 160, mouseY + 10, 0xCC181818);
                graphics.item(new ItemStack(dragged.item), mouseX, mouseY - 8);
                graphics.text(font, Component.literal("§e" + dragged.name().replace('_', ' ')), mouseX + 20, mouseY - 4, 0xFFFFFFFF);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
    }

    @Override
    public void tick() {
        super.tick();

        if (this.draggedFuelIndex != -1) {
            int listBottomY = this.height - 36;
            float maxScrollPixels = getMaxScrollPixels();

            boolean isNearTop = this.currentMouseY < LIST_START_Y + 14 && this.targetScroll > 0;
            boolean isNearBottom = this.currentMouseY > listBottomY - 14 && this.targetScroll < maxScrollPixels;

            if (isNearTop || isNearBottom) {
                this.dragHoldTime = Math.min(20f, this.dragHoldTime + 1f);
                float speedMultiplier = 1.0f + (this.dragHoldTime / 20f);
                float baseSpeed = 4.5f * speedMultiplier;

                if (isNearTop) {
                    this.targetScroll = Math.max(0, this.targetScroll - baseSpeed);
                } else {
                    this.targetScroll = Math.min(maxScrollPixels, this.targetScroll + baseSpeed);
                }
            } else {
                this.dragHoldTime = 0f;
            }
        } else {
            this.dragHoldTime = 0f;
        }

        if (Math.abs(this.smoothScroll - this.targetScroll) > 0.01f) {
            this.smoothScroll = Mth.lerp(0.35f, this.smoothScroll, this.targetScroll);
            updateWidgetPositions();
        } else {
            this.smoothScroll = this.targetScroll;
        }
    }

    private int getTargetIndex(double mouseY) {
        float absoluteY = (float) (mouseY - LIST_START_Y) + this.smoothScroll;
        int index = Math.round(absoluteY / ROW_HEIGHT);
        return Mth.clamp(index, 0, getFilteredFuels().size());
    }

    private float getMaxScrollPixels() {
        int totalHeight = getFilteredFuels().size() * ROW_HEIGHT;
        int visibleHeight = getVisibleCount() * ROW_HEIGHT;
        return Math.max(0, totalHeight - visibleHeight);
    }

    private class ScrollbarWidget extends AbstractWidget {
        public ScrollbarWidget(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            float maxScroll = getMaxScrollPixels();
            if (maxScroll <= 0) return;

            int trackTop = this.getY();
            int trackBottom = this.getY() + this.getHeight();
            int trackHeight = trackBottom - trackTop;

            int visibleHeight = getVisibleCount() * ROW_HEIGHT;
            int totalHeight = getFilteredFuels().size() * ROW_HEIGHT;
            int thumbHeight = Math.max(16, (int) ((float) visibleHeight / totalHeight * trackHeight));
            int maxThumbTravel = trackHeight - thumbHeight;
            int thumbY = trackTop + (int) ((smoothScroll / maxScroll) * maxThumbTravel);

            int trackX = this.getX() + (this.getWidth() - 4) / 2;
            graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0x44000000);

            boolean isHovered = mouseX >= this.getX() - 2 && mouseX <= this.getX() + this.getWidth() + 2
                    && mouseY >= trackTop && mouseY <= trackBottom;

            int thumbWidth = (isHovered || isScrollDragging) ? 8 : 4;
            int thumbX = this.getX() + (this.getWidth() - thumbWidth) / 2;
            int thumbColor = (isHovered || isScrollDragging) ? 0xFFFFFFFF : 0xFFB0B0B0;

            graphics.fill(thumbX, thumbY, thumbX + thumbWidth, thumbY + thumbHeight, thumbColor);

            int downBtnY = trackBottom + 2;
            int downBtnX = this.getX() - 2;
            int arrowX = downBtnX + (16 - font.width("▼")) / 2;
            graphics.text(font, Component.literal("▼"), arrowX, downBtnY + 3, 0xFFFFFFFF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
    }

    @Override
    protected void init() {
        this.dynamicRowWidgets.clear();

        // Header Title
        this.addRenderableWidget(new StringWidget(
                16, 8, 200, 16,
                Component.literal("§lSmelting Overhauled"),
                this.font
        ));

        // Master Mod Toggle Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Mod Status: " + (draftModEnabled ? "§aENABLED" : "§cDISABLED")),
                btn -> {
                    draftModEnabled = !draftModEnabled;
                    btn.setMessage(Component.literal("Mod Status: " + (draftModEnabled ? "§aENABLED" : "§cDISABLED")));
                }
        ).bounds(16, 26, 175, 18).build());

        // Search Box
        int searchWidth = 140;
        this.searchBox = new EditBox(this.font, this.width - searchWidth - 16, 14, searchWidth, 18, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("§8Search fuels..."));
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text.toLowerCase();
            this.targetScroll = 0;
            this.smoothScroll = 0;
            refreshFuelRows();
        });
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        // Scroll Controls
        int visibleCount = getVisibleCount();
        int totalListSpan = (visibleCount * ROW_HEIGHT) - (ROW_HEIGHT - 18);
        int scrollbarX = this.width - 18;

        this.addRenderableWidget(Button.builder(Component.literal("▲"), btn -> {
            this.targetScroll = Math.max(0, this.targetScroll - (PAGE_STEP * ROW_HEIGHT));
        }).bounds(scrollbarX - 2, LIST_START_Y - 14, 16, 12).build());

        this.addRenderableWidget(Button.builder(Component.empty(), btn -> {
            this.targetScroll = Math.min(getMaxScrollPixels(), this.targetScroll + (PAGE_STEP * ROW_HEIGHT));
        }).bounds(scrollbarX - 2, LIST_START_Y + totalListSpan + 2, 16, 12).build());

        this.scrollbarWidget = new ScrollbarWidget(scrollbarX, LIST_START_Y, SCROLLBAR_WIDTH, totalListSpan);
        this.addRenderableWidget(this.scrollbarWidget);

        // Fuel Row Widgets
        buildFuelRows();

        // Overlay
        this.rowOverlayWidget = new RowOverlayWidget(this.width, this.height);
        this.addRenderableWidget(this.rowOverlayWidget);

        // Bottom Action Buttons (Added last to guarantee top-level click priority)
        this.rebindKeyButton = Button.builder(
                getHotkeyButtonText(),
                btn -> {
                    this.isRebindingKey = !this.isRebindingKey;
                    btn.setMessage(getHotkeyButtonText());
                }
        ).bounds(16, this.height - 28, 155, 20).build();
        this.addRenderableWidget(this.rebindKeyButton);

        this.addRenderableWidget(Button.builder(Component.literal("Save & Exit"), btn -> saveAndClose())
                .bounds(this.width - 110, this.height - 28, 96, 20).build());
    }

    private Component getHotkeyButtonText() {
        if (this.isRebindingKey) {
            return Component.literal("§e> Press a key <");
        }
        String keyName = "H";
        if (SmeltingClient.openConfigKey != null) {
            keyName = SmeltingClient.openConfigKey.getTranslatedKeyMessage().getString();
        }
        return Component.literal("Config Menu: §b[" + keyName + "]");
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isRebindingKey) {
            int keyCode = event.key();

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.isRebindingKey = false;
                this.rebindKeyButton.setMessage(getHotkeyButtonText());
                return true;
            }

            if (SmeltingClient.openConfigKey != null && this.minecraft != null) {
                InputConstants.Key boundKey = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
                SmeltingClient.openConfigKey.setKey(boundKey);
                refreshKeyMappingLookup();
                this.minecraft.options.save();
            }

            this.isRebindingKey = false;
            this.rebindKeyButton.setMessage(getHotkeyButtonText());
            return true;
        }

        return super.keyPressed(event);
    }

    private static void refreshKeyMappingLookup() {
        try {
            Method resetMethod = KeyMapping.class.getDeclaredMethod("resetMapping");
            resetMethod.setAccessible(true);
            resetMethod.invoke(null);
        } catch (Exception ignored) {
            try {
                for (Method m : KeyMapping.class.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && Modifier.isStatic(m.getModifiers()) && m.getName().toLowerCase().contains("reset")) {
                        m.setAccessible(true);
                        m.invoke(null);
                        break;
                    }
                }
            } catch (Exception ignoredFallback) {}
        }
    }

    private int getVisibleCount() {
        int listBottomY = this.height - 36;
        return Math.max(1, (listBottomY - LIST_START_Y) / ROW_HEIGHT);
    }

    private List<SmeltingOverhauled.FuelType> getFilteredFuels() {
        String cleanFilter = searchQuery.trim();
        return draftConfig.stream()
                .filter(fuel -> cleanFilter.isEmpty() || fuel.name().replace('_', ' ').toLowerCase().contains(cleanFilter))
                .collect(Collectors.toList());
    }

    private void refreshFuelRows() {
        for (AbstractWidget widget : this.dynamicRowWidgets) {
            this.removeWidget(widget);
        }
        this.dynamicRowWidgets.clear();
        buildFuelRows();
    }

    private void addRowWidget(AbstractWidget widget) {
        this.dynamicRowWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    private void buildFuelRows() {
        List<SmeltingOverhauled.FuelType> filtered = getFilteredFuels();
        int listBottomY = this.height - 36;

        for (int i = 0; i < filtered.size(); i++) {
            SmeltingOverhauled.FuelType fuel = filtered.get(i);
            boolean isEnabled = draftActiveFuels.contains(fuel);
            int rowY = Math.round(LIST_START_Y + (i * ROW_HEIGHT) - smoothScroll);
            boolean isVisible = rowY >= LIST_START_Y - 2 && (rowY + ROW_HEIGHT - 2) <= listBottomY;

            // Icon
            ItemIconWidget icon = new ItemIconWidget(16, rowY + 1, new ItemStack(fuel.item));
            icon.visible = isVisible;
            icon.active = isVisible;
            this.addRowWidget(icon);

            // Label
            String yieldDisplay = (fuel.units % 4 == 0) ? String.valueOf(fuel.units / 4) : String.valueOf(fuel.getSmeltYield());
            String label = (draftConfig.indexOf(fuel) + 1) + ". " + fuel.name().replace('_', ' ') + " (" + yieldDisplay + "x)";
            StringWidget text = new StringWidget(38, rowY + 1, 210, 16, Component.literal(label), this.font);
            text.visible = isVisible;
            text.active = isVisible;
            this.addRowWidget(text);

            // ON/OFF
            Button toggleBtn = Button.builder(
                    Component.literal(isEnabled ? "§aON" : "§cOFF"),
                    btn -> {
                        if (draftActiveFuels.contains(fuel)) {
                            draftActiveFuels.remove(fuel);
                            btn.setMessage(Component.literal("§cOFF"));
                        } else {
                            draftActiveFuels.add(fuel);
                            btn.setMessage(Component.literal("§aON"));
                        }
                    }
            ).bounds(254, rowY, 36, 18).build();
            toggleBtn.visible = isVisible;
            toggleBtn.active = isVisible;
            this.addRowWidget(toggleBtn);

            // Move Up (▲)
            int actualIndex = draftConfig.indexOf(fuel);
            Button upBtn = Button.builder(Component.literal("▲"), btn -> {
                if (actualIndex > 0) {
                    SmeltingOverhauled.FuelType prev = draftConfig.get(actualIndex - 1);
                    draftConfig.set(actualIndex - 1, fuel);
                    draftConfig.set(actualIndex, prev);
                    refreshFuelRows();
                }
            }).bounds(294, rowY, 18, 18).build();
            upBtn.visible = isVisible && actualIndex > 0;
            upBtn.active = isVisible && actualIndex > 0;
            this.addRowWidget(upBtn);

            // Move Down (▼)
            Button downBtn = Button.builder(Component.literal("▼"), btn -> {
                if (actualIndex < draftConfig.size() - 1) {
                    SmeltingOverhauled.FuelType next = draftConfig.get(actualIndex + 1);
                    draftConfig.set(actualIndex + 1, fuel);
                    draftConfig.set(actualIndex, next);
                    refreshFuelRows();
                }
            }).bounds(314, rowY, 18, 18).build();
            downBtn.visible = isVisible && actualIndex < draftConfig.size() - 1;
            downBtn.active = isVisible && actualIndex < draftConfig.size() - 1;
            this.addRowWidget(downBtn);
        }
    }

    private void updateWidgetPositions() {
        int listBottomY = this.height - 36;
        int totalRows = dynamicRowWidgets.size() / 5;

        for (int i = 0; i < totalRows; i++) {
            int rowY = Math.round(LIST_START_Y + (i * ROW_HEIGHT) - smoothScroll);
            boolean isVisible = rowY >= LIST_START_Y - 2 && (rowY + ROW_HEIGHT - 2) <= listBottomY;

            int baseIdx = i * 5;
            for (int offset = 0; offset < 5; offset++) {
                AbstractWidget w = dynamicRowWidgets.get(baseIdx + offset);
                w.setY(rowY + (offset == 0 || offset == 1 ? 1 : 0));
                w.visible = isVisible;
                w.active = isVisible;
            }
        }
    }

    private void updateScrollFromMouse(double mouseY) {
        if (this.scrollbarWidget == null) return;
        int trackTop = this.scrollbarWidget.getY();
        int trackHeight = this.scrollbarWidget.getHeight();

        int visibleHeight = getVisibleCount() * ROW_HEIGHT;
        int totalHeight = getFilteredFuels().size() * ROW_HEIGHT;
        int thumbHeight = Math.max(16, (int) ((float) visibleHeight / totalHeight * trackHeight));
        int maxThumbTravel = trackHeight - thumbHeight;

        if (maxThumbTravel > 0) {
            float progress = (float) (mouseY - trackTop - (thumbHeight / 2.0)) / maxThumbTravel;
            float maxScroll = getMaxScrollPixels();
            this.targetScroll = Mth.clamp(progress * maxScroll, 0, maxScroll);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int listBottomY = this.height - 36;

        if (this.scrollbarWidget != null && getMaxScrollPixels() > 0) {
            int sx = this.scrollbarWidget.getX();
            int sy = this.scrollbarWidget.getY();
            int sw = this.scrollbarWidget.getWidth();
            int sh = this.scrollbarWidget.getHeight();

            if (mx >= sx - 4 && mx <= sx + sw + 4 && my >= sy && my <= sy + sh) {
                this.isScrollDragging = true;
                updateScrollFromMouse(my);
                return true;
            }
        }

        if (event.button() == 0 && mx >= ROW_LEFT && mx <= ROW_RIGHT && my >= LIST_START_Y && my < listBottomY) {
            int clickedIndex = (int) ((my - LIST_START_Y + smoothScroll) / ROW_HEIGHT);
            List<SmeltingOverhauled.FuelType> filtered = getFilteredFuels();
            if (clickedIndex >= 0 && clickedIndex < filtered.size()) {
                this.draggedFuelIndex = this.draftConfig.indexOf(filtered.get(clickedIndex));
                this.dragHoldTime = 0f;
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.isScrollDragging) {
            updateScrollFromMouse(event.y());
            return true;
        }
        if (this.draggedFuelIndex != -1) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.isScrollDragging = false;
        this.dragHoldTime = 0f;

        if (this.draggedFuelIndex != -1) {
            List<SmeltingOverhauled.FuelType> filtered = getFilteredFuels();
            int targetIndex = getTargetIndex(event.y());
            int targetFilterIndex = Mth.clamp(targetIndex, 0, filtered.size());

            SmeltingOverhauled.FuelType draggedFuel = draftConfig.remove(this.draggedFuelIndex);

            if (targetFilterIndex >= filtered.size()) {
                draftConfig.add(draggedFuel);
            } else {
                SmeltingOverhauled.FuelType targetFuel = filtered.get(targetFilterIndex);
                int targetActualIndex = draftConfig.indexOf(targetFuel);
                if (targetActualIndex != -1) {
                    draftConfig.add(targetActualIndex, draggedFuel);
                } else {
                    draftConfig.add(draggedFuel);
                }
            }

            this.draggedFuelIndex = -1;
            refreshFuelRows();
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float maxScroll = getMaxScrollPixels();
        if (maxScroll > 0) {
            this.targetScroll = Mth.clamp(this.targetScroll - ((float) Math.signum(scrollY) * ROW_HEIGHT * 1.5f), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void saveAndClose() {
        savedModEnabled = this.draftModEnabled;
        savedConfig.clear();
        savedConfig.addAll(this.draftConfig);
        savedActiveFuels.clear();
        savedActiveFuels.addAll(this.draftActiveFuels);

        saveConfigFile();

        if (this.minecraft != null && this.minecraft.player != null) {
            List<String> fuelNames = new ArrayList<>();
            for (SmeltingOverhauled.FuelType f : savedConfig) {
                if (savedActiveFuels.contains(f)) {
                    fuelNames.add(f.name());
                }
            }
            String joined = fuelNames.isEmpty() ? "NONE" : String.join(";", fuelNames);
            this.minecraft.player.connection.sendCommand("smeltingsync " + savedModEnabled + " " + joined);
        }

        this.onClose();
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}