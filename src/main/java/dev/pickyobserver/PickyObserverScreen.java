package dev.pickyobserver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Crafting-table style GUI (only the 3x3 grid) plus a block-only copy of the vanilla recipe book on
 * the left: category tabs (All, Building, Colored, Natural, Functional, Redstone), a search box and a
 * paged 5x4 grid. Click a block in the book to put it in the grid for free; click a grid slot to delete it.
 */
public class PickyObserverScreen extends AbstractContainerScreen<PickyObserverMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PickyObserverMod.MODID, "textures/gui/picky_observer.png");

    // Vanilla recipe book resources, so the panel looks exactly like the crafting table's.
    private static final ResourceLocation BOOK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");
    private static final ResourceLocation CELL_SPRITE =
            ResourceLocation.withDefaultNamespace("recipe_book/slot_craftable");
    private static final ResourceLocation TAB_SPRITE =
            ResourceLocation.withDefaultNamespace("recipe_book/tab");
    private static final ResourceLocation TAB_SELECTED_SPRITE =
            ResourceLocation.withDefaultNamespace("recipe_book/tab_selected");
    private static final WidgetSprites TOGGLE_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/button"),
            ResourceLocation.withDefaultNamespace("recipe_book/button_highlighted"));
    private static final WidgetSprites PAGE_FORWARD_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/page_forward"),
            ResourceLocation.withDefaultNamespace("recipe_book/page_forward_highlighted"));
    private static final WidgetSprites PAGE_BACKWARD_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/page_backward"),
            ResourceLocation.withDefaultNamespace("recipe_book/page_backward_highlighted"));

    // Layout copied from the vanilla recipe book.
    private static final int BOOK_WIDTH = 147;
    private static final int BOOK_HEIGHT = 166;
    private static final int BOOK_OFFSET = 86;
    private static final int CELL = 25;
    private static final int COLS = 5;
    private static final int ROWS = 4;
    private static final int PER_PAGE = COLS * ROWS;
    private static final int GRID_X = 11;
    private static final int GRID_Y = 31;
    private static final int TAB_WIDTH = 35;
    private static final int TAB_HEIGHT = 27;
    /** Below this GUI width there is no room for the book next to the grid. */
    private static final int MIN_WIDTH_FOR_BOOK = 379;

    private static final List<ResourceKey<CreativeModeTab>> CATEGORY_TABS = List.of(
            CreativeModeTabs.BUILDING_BLOCKS,
            CreativeModeTabs.COLORED_BLOCKS,
            CreativeModeTabs.NATURAL_BLOCKS,
            CreativeModeTabs.FUNCTIONAL_BLOCKS,
            CreativeModeTabs.REDSTONE_BLOCKS);

    private record Category(ItemStack icon, Component name, List<Item> items) {}

    /** Remembered between openings, like the vanilla recipe book. */
    private static boolean bookOpen = true;
    private static int selectedTab = 0;

    private final List<Category> categories;
    private final List<Item> shown = new ArrayList<>();
    private Button modeButton;
    private ImageButton prevButton;
    private ImageButton nextButton;
    private EditBox searchBox;
    private int page = 0;
    private String query = "";

    public PickyObserverScreen(PickyObserverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.categories = buildCategories();
        if (selectedTab >= categories.size()) {
            selectedTab = 0;
        }
    }

    /** "All" holds every block item; the other tabs mirror the vanilla creative tabs, blocks only. */
    private static List<Category> buildCategories() {
        List<Item> every = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem && item != Items.AIR) {
                every.add(item);
            }
        }
        List<Category> result = new ArrayList<>();
        result.add(new Category(new ItemStack(Items.COMPASS), Component.translatable("gui.pickyobserver.tab.all"), every));

        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.level != null && minecraft.player != null) {
                CreativeModeTabs.tryRebuildTabContents(
                        minecraft.level.enabledFeatures(),
                        minecraft.player.canUseGameMasterBlocks(),
                        minecraft.level.registryAccess());
            }
            for (ResourceKey<CreativeModeTab> key : CATEGORY_TABS) {
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
                if (tab == null) {
                    continue;
                }
                Set<Item> items = new LinkedHashSet<>();
                for (ItemStack stack : tab.getDisplayItems()) {
                    if (stack.getItem() instanceof BlockItem && stack.getItem() != Items.AIR) {
                        items.add(stack.getItem());
                    }
                }
                if (!items.isEmpty()) {
                    result.add(new Category(tab.getIconItem(), tab.getDisplayName(), new ArrayList<>(items)));
                }
            }
        } catch (RuntimeException e) {
            // If the creative tabs can't be read, the book still works with the "All" tab alone.
        }
        return result;
    }

    private boolean showBook() {
        return bookOpen && this.width >= MIN_WIDTH_FOR_BOOK;
    }

    private int bookX() {
        return (this.width - BOOK_WIDTH) / 2 - BOOK_OFFSET;
    }

    private int tabX() {
        return bookX() - 30;
    }

    private int tabY(int index) {
        return this.topPos + 3 + TAB_HEIGHT * index;
    }

    @Override
    protected void init() {
        super.init();
        if (showBook()) {
            // Same GUI shift the vanilla recipe book uses.
            this.leftPos = 177 + (this.width - this.imageWidth - 200) / 2;
        }
        // Above the grid, like the "Crafting" label of the crafting table.
        this.titleLabelX = 29;

        this.modeButton = this.addRenderableWidget(
                Button.builder(modeLabel(), button ->
                                this.minecraft.gameMode.handleInventoryButtonClick(
                                        this.menu.containerId, PickyObserverMenu.BUTTON_TOGGLE_EXACT))
                        .bounds(this.leftPos + 100, this.topPos + 35, 56, 18)
                        .tooltip(Tooltip.create(Component.translatable("gui.pickyobserver.tooltip")))
                        .build());

        // Same spot as the recipe book button of the crafting table.
        this.addRenderableWidget(new ImageButton(this.leftPos + 5, this.height / 2 - 49, 20, 18, TOGGLE_BUTTON_SPRITES, button -> {
            bookOpen = !bookOpen;
            this.rebuildWidgets();
        }));

        this.searchBox = new EditBox(this.font, bookX() + 25, this.topPos + 13, 111, 14,
                Component.translatable("gui.pickyobserver.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.pickyobserver.search")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(text -> {
            this.query = text;
            this.page = 0;
            refreshShown();
        });

        this.prevButton = new ImageButton(bookX() + 38, this.topPos + 137, 12, 17, PAGE_BACKWARD_SPRITES,
                button -> setPage(page - 1));
        this.nextButton = new ImageButton(bookX() + 93, this.topPos + 137, 12, 17, PAGE_FORWARD_SPRITES,
                button -> setPage(page + 1));

        if (showBook()) {
            this.addRenderableWidget(this.searchBox);
            this.addRenderableWidget(this.prevButton);
            this.addRenderableWidget(this.nextButton);
        }
        refreshShown();
    }

    private void refreshShown() {
        shown.clear();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (Item item : categories.get(selectedTab).items()) {
            if (needle.isEmpty()
                    || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(needle)
                    || BuiltInRegistries.ITEM.getKey(item).getPath().contains(needle)) {
                shown.add(item);
            }
        }
        page = Math.min(page, pageCount() - 1);
        updatePageButtons();
    }

    private int pageCount() {
        return Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void setPage(int newPage) {
        page = Math.max(0, Math.min(newPage, pageCount() - 1));
        updatePageButtons();
    }

    private void updatePageButtons() {
        if (prevButton != null) {
            prevButton.visible = page > 0;
            nextButton.visible = page < pageCount() - 1;
        }
    }

    private void selectTab(int index) {
        selectedTab = index;
        page = 0;
        refreshShown();
    }

    private Component modeLabel() {
        return Component.translatable(menu.isExact() ? "gui.pickyobserver.exact" : "gui.pickyobserver.any");
    }

    /** Index into {@link #shown} of the book cell under the mouse, or -1. */
    private int cellAt(double mouseX, double mouseY) {
        if (!showBook()) {
            return -1;
        }
        int x = (int) mouseX - (bookX() + GRID_X);
        int y = (int) mouseY - (this.topPos + GRID_Y);
        if (x < 0 || y < 0 || x >= COLS * CELL || y >= ROWS * CELL) {
            return -1;
        }
        int index = page * PER_PAGE + (y / CELL) * COLS + (x / CELL);
        return index < shown.size() ? index : -1;
    }

    /** Index of the category tab under the mouse, or -1. */
    private int tabAt(double mouseX, double mouseY) {
        if (!showBook()) {
            return -1;
        }
        for (int i = 0; i < categories.size(); i++) {
            int x = tabX() - (i == selectedTab ? 2 : 0);
            int y = tabY(i);
            if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.modeButton.setMessage(modeLabel());
        super.render(graphics, mouseX, mouseY, partialTick);

        if (showBook()) {
            // Tab icons.
            for (int i = 0; i < categories.size(); i++) {
                int x = tabX() + 9 - (i == selectedTab ? 2 : 0);
                graphics.renderItem(categories.get(i).icon(), x, tabY(i) + 5);
            }

            // Block items inside the grid cells.
            int start = page * PER_PAGE;
            for (int i = 0; i < PER_PAGE && start + i < shown.size(); i++) {
                int x = bookX() + GRID_X + (i % COLS) * CELL + 4;
                int y = this.topPos + GRID_Y + (i / COLS) * CELL + 4;
                graphics.renderItem(new ItemStack(shown.get(start + i)), x, y);
            }

            String pageText = (page + 1) + "/" + pageCount();
            graphics.drawString(this.font, pageText, bookX() - this.font.width(pageText) / 2 + 73,
                    this.topPos + 141, 0xFFFFFFFF);

            int hovered = cellAt(mouseX, mouseY);
            if (hovered >= 0) {
                int cell = hovered - start;
                int x = bookX() + GRID_X + (cell % COLS) * CELL + 4;
                int y = this.topPos + GRID_Y + (cell / COLS) * CELL + 4;
                graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                graphics.renderTooltip(this.font, new ItemStack(shown.get(hovered)), mouseX, mouseY);
            } else {
                int tab = tabAt(mouseX, mouseY);
                if (tab >= 0) {
                    graphics.renderTooltip(this.font, categories.get(tab).name(), mouseX, mouseY);
                }
            }
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        if (showBook()) {
            graphics.blit(BOOK_TEXTURE, bookX(), this.topPos, 1, 1, BOOK_WIDTH, BOOK_HEIGHT);
            for (int i = 0; i < categories.size(); i++) {
                boolean selected = i == selectedTab;
                graphics.blitSprite(selected ? TAB_SELECTED_SPRITE : TAB_SPRITE,
                        tabX() - (selected ? 2 : 0), tabY(i), TAB_WIDTH, TAB_HEIGHT);
            }
            int start = page * PER_PAGE;
            for (int i = 0; i < PER_PAGE && start + i < shown.size(); i++) {
                graphics.blitSprite(CELL_SPRITE,
                        bookX() + GRID_X + (i % COLS) * CELL,
                        this.topPos + GRID_Y + (i / COLS) * CELL, CELL, CELL);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Checked first: the container screen's own handler claims nearly every click.
        int tab = tabAt(mouseX, mouseY);
        if (tab >= 0) {
            if (button == 0) {
                selectTab(tab);
            }
            return true;
        }
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            if (button == 0) {
                int rawId = BuiltInRegistries.ITEM.getId(shown.get(cell));
                this.minecraft.gameMode.handleInventoryButtonClick(
                        this.menu.containerId, PickyObserverMenu.BUTTON_PICK_BASE + rawId);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showBook() && mouseX < this.leftPos && scrollY != 0) {
            setPage(page + (scrollY < 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showBook() && this.searchBox.isFocused() && keyCode != InputConstants.KEY_ESCAPE) {
            // Typing in the search box must not trigger the inventory key / hotbar keys.
            this.searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
