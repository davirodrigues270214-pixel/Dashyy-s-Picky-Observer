package dev.pickyobserver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PickyObserverBlockEntity extends RandomizableContainerBlockEntity {
    public static final int SIZE = 9;
    public static final int DATA_EXACT = 0;
    public static final int DATA_LOCKED = 1;
    public static final int DATA_COUNT = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    /** false = match the block in any state, true = only its default state. */
    private boolean exact = false;
    /** What the block in front looked like the last time it changed. */
    private BlockState lastSeen = null;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == DATA_LOCKED) {
                return getBlockState().getValue(PickyObserverBlock.LOCKED) ? 1 : 0;
            }
            return exact ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_EXACT) {
                exact = value != 0;
                setChanged();
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public PickyObserverBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PICKY_OBSERVER_BE.get(), pos, state);
    }

    public void setLastSeen(BlockState state) {
        this.lastSeen = state;
        this.setChanged();
    }

    /**
     * Called when the block in front of the observer changed.
     * Returns true when the observer should send its pulse.
     */
    public boolean onFrontChanged(BlockState newState) {
        BlockState old = lastSeen == null ? Blocks.AIR.defaultBlockState() : lastSeen;
        lastSeen = newState;
        if (old == newState) {
            return false;
        }
        if (!hasFilter()) {
            return true;
        }
        return matches(old) || matches(newState);
    }

    public boolean hasFilter() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(BlockState state) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (exact ? state == block.defaultBlockState() : state.is(block)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    // The grid only stores "ghost" copies of blocks (nothing is consumed or dropped), so hoppers and
    // other automation must never be able to put items in or pull them out.
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeItem(net.minecraft.world.Container target, int slot, ItemStack stack) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.pickyobserver.picky_observer");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new PickyObserverMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            net.minecraft.world.ContainerHelper.loadAllItems(tag, this.items, registries);
        }
        this.exact = tag.getBoolean("Exact");
        if (tag.contains("LastSeen")) {
            this.lastSeen = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("LastSeen"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.trySaveLootTable(tag)) {
            net.minecraft.world.ContainerHelper.saveAllItems(tag, this.items, registries);
        }
        tag.putBoolean("Exact", this.exact);
        if (this.lastSeen != null) {
            tag.put("LastSeen", NbtUtils.writeBlockState(this.lastSeen));
        }
    }
}
