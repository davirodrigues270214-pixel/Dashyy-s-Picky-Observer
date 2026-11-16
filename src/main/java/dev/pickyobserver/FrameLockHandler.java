package dev.pickyobserver;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Item frames (normal, glowing and invisible: they are all {@link ItemFrame}) ignore every player
 * interaction while redstone power reaches the block they are attached to. That covers placing,
 * taking and rotating the item, and punching the frame off the wall, in survival and creative.
 */
@EventBusSubscriber(modid = PickyObserverMod.MODID)
public final class FrameLockHandler {
    private FrameLockHandler() {}

    /** True when the block the frame hangs on is receiving redstone power. */
    public static boolean isLocked(Level level, Entity entity) {
        if (!(entity instanceof ItemFrame frame)) {
            return false;
        }
        BlockPos support = frame.getPos().relative(frame.getDirection().getOpposite());
        return level.hasNeighborSignal(support);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (isLocked(event.getLevel(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isLocked(event.getLevel(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isLocked(event.getEntity().level(), event.getTarget())) {
            event.setCanceled(true);
        }
    }
}
