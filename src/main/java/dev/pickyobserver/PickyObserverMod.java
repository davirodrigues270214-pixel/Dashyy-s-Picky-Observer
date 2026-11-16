package dev.pickyobserver;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(PickyObserverMod.MODID)
public class PickyObserverMod {
    public static final String MODID = "pickyobserver";

    public PickyObserverMod(IEventBus modEventBus) {
        ModRegistry.register(modEventBus);
        modEventBus.addListener(this::addToCreativeTab);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModRegistry.PICKY_OBSERVER_ITEM);
        }
    }
}
