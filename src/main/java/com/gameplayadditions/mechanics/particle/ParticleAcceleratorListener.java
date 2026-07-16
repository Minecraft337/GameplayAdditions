package com.gameplayadditions.mechanics.particle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Обрабатывает события размещения/разрушения блоков ускорителя частиц.
 */
public class ParticleAcceleratorListener {

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        if (ParticleAcceleratorManager.ACCELERATOR_BLOCKS.contains(event.getState().getBlock())) {
            ParticleAcceleratorManager.onBlockPlaced(level, event.getPos());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        if (ParticleAcceleratorManager.ACCELERATOR_BLOCKS.contains(event.getState().getBlock())) {
            ParticleAcceleratorManager.onBlockBroken(level, event.getPos(), event.getState().getBlock());
        }
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Level level = event.getLevel();
        if (level.getBlockState(event.getPos()).is(ParticleAcceleratorManager.INJECTOR)) {
            if (event.getEntity() instanceof ServerPlayer player) {
                ParticleAcceleratorManager.onInjectorInteract(player, level, event.getPos());
            }
        }
    }
}
