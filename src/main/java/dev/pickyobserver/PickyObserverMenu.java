package dev.pickyobserver;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Crafting-table style menu: only the 3x3 grid, no arrow and no result slot.
 *
 * The grid holds "ghost" copies of blocks:
 *  - picking a block in the block book fills the next free grid slot for free,
 *  - clicking a grid slot with a block on the cursor (or shift-clicking a block) copies it in
 *    without consuming the original,
 *  - clicking a grid slot with nothing / anything else deletes its content, nothing is dropped,
 *  - non-block items are rejected everywhere.
 */
public class PickyObserverMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_EXACT = 0;
    /** Button ids from this value on mean "put the item with raw registry id (id - base) into the grid". */
    public static final int BUTTON_PICK_BASE = 1000;

    private static final int GRID = PickyObserverBlockEntity.SIZE;

    private final Container container;
    private final ContainerData data;

    /** Client side. */
    public PickyObserverMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(GRID), new SimpleContainerData(PickyObserverBlockEntity.DATA_COUNT));
    }

    /** Server side. */
    public PickyObserverMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModRegistry.PICKY_OBSERVER_MENU.get(), containerId);
        checkContainerSize(container, GRID);
        checkContainerDataCount(data, PickyObserverBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        container.startOpen(inventory.player);

        // Same position as the crafting table's 3x3 grid.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new FilterSlot(container, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        this.addDataSlots(data);
    }

    public boolean isExact() {
        return data.get(PickyObserverBlockEntity.DATA_EXACT) != 0;
    }

    public boolean isLocked() {
        return data.get(PickyObserverBlockEntity.DATA_LOCKED) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (isLocked()) {
            return false;
        }
        if (id == BUTTON_TOGGLE_EXACT) {
            data.set(PickyObserverBlockEntity.DATA_EXACT, isExact() ? 0 : 1);
            return true;
        }
        if (id >= BUTTON_PICK_BASE) {
            Item item = BuiltInRegistries.ITEM.byId(id - BUTTON_PICK_BASE);
            if (isFilterable(item)) {
                addToGrid(new ItemStack(item));
                return true;
            }
        }
        return false;
    }

    private static boolean isFilterable(Item item) {
        return item instanceof BlockItem && item != Items.AIR;
    }

    private static boolean isFilterable(ItemStack stack) {
        return !stack.isEmpty() && isFilterable(stack.getItem());
    }

    /** Puts a one-item ghost copy in the first free grid slot. Does nothing if it is already there or the grid is full. */
    private void addToGrid(ItemStack stack) {
        if (!isFilterable(stack)) {
            return;
        }
        int free = -1;
        for (int i = 0; i < GRID; i++) {
            ItemStack existing = this.slots.get(i).getItem();
            if (existing.isEmpty()) {
                if (free < 0) {
                    free = i;
                }
            } else if (existing.is(stack.getItem())) {
                return;
            }
        }
        if (free >= 0) {
            this.slots.get(free).set(stack.copyWithCount(1));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= GRID) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        // A click on one of the filter slots. Never goes through vanilla handling, so nothing can be
        // picked up, thrown, swapped out or dragged into the grid.
        if (isLocked()) {
            return;
        }
        Slot slot = this.slots.get(slotId);
        switch (clickType) {
            case PICKUP -> {
                ItemStack carried = this.getCarried();
                if (carried.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else if (isFilterable(carried)) {
                    // Copy, the carried stack stays untouched. Skip if the same block is in another slot.
                    for (int i = 0; i < GRID; i++) {
                        if (i != slotId && this.slots.get(i).getItem().is(carried.getItem())) {
                            return;
                        }
                    }
                    slot.set(carried.copyWithCount(1));
                }
                // Non-block on the cursor: rejected, nothing changes.
            }
            case QUICK_MOVE, THROW, PICKUP_ALL -> slot.set(ItemStack.EMPTY);
            case SWAP -> {
                ItemStack hotbar = button >= 0 && button < 9 ? player.getInventory().getItem(button) : ItemStack.EMPTY;
                if (hotbar.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else if (isFilterable(hotbar)) {
                    for (int i = 0; i < GRID; i++) {
                        if (i != slotId && this.slots.get(i).getItem().is(hotbar.getItem())) {
                            return;
                        }
                    }
                    slot.set(hotbar.copyWithCount(1));
                }
            }
            default -> {
                // CLONE and QUICKCRAFT (dragging): ignored.
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        // Locking while the screen is open closes it for everyone looking at it.
        return !isLocked() && container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    /** Shift-click on an inventory block copies it into the grid (free). Never moves real items. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= GRID && !isLocked()) {
            Slot slot = this.slots.get(index);
            if (slot.hasItem()) {
                addToGrid(slot.getItem());
            }
        }
        return ItemStack.EMPTY;
    }

    /** Grid slot that vanilla handling can never place into or take from. */
    private static class FilterSlot extends Slot {
        FilterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
