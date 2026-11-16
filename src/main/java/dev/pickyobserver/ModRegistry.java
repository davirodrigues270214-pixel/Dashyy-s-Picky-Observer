package dev.pickyobserver;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PickyObserverMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PickyObserverMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PickyObserverMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PickyObserverMod.MODID);

    public static final DeferredBlock<PickyObserverBlock> PICKY_OBSERVER = BLOCKS.registerBlock(
            "picky_observer",
            PickyObserverBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
                    .isRedstoneConductor((state, level, pos) -> false));

    public static final DeferredItem<BlockItem> PICKY_OBSERVER_ITEM =
            ITEMS.registerSimpleBlockItem("picky_observer", PICKY_OBSERVER);

    public static final Supplier<BlockEntityType<PickyObserverBlockEntity>> PICKY_OBSERVER_BE =
            BLOCK_ENTITIES.register("picky_observer",
                    () -> BlockEntityType.Builder.of(PickyObserverBlockEntity::new, PICKY_OBSERVER.get()).build(null));

    public static final Supplier<MenuType<PickyObserverMenu>> PICKY_OBSERVER_MENU =
            MENUS.register("picky_observer",
                    () -> new MenuType<>(PickyObserverMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
    }
}
