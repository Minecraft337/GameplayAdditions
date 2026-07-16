package com.gameplayadditions.energy.machines.assembler;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Обрабатывает события размещения/разрушения блоков авто-сборщика.
 */
public class AssemblerListener {

    public static final net.minecraft.world.level.block.Block ASSEMBLER_CORE = Blocks.CHISELED_TUFF;
    public static final net.minecraft.world.level.block.Block ASSEMBLER_INPUT = Blocks.POLISHED_TUFF;
    public static final net.minecraft.world.level.block.Block ASSEMBLER_OUTPUT = Blocks.CHISELED_TUFF_BRICKS;
    public static final net.minecraft.world.level.block.Block ASSEMBLER_ENERGY = Blocks.TUFF_BRICKS;

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        if (event.getState().is(ASSEMBLER_CORE) || event.getState().is(ASSEMBLER_INPUT)
                || event.getState().is(ASSEMBLER_OUTPUT) || event.getState().is(ASSEMBLER_ENERGY)) {
            AssemblerManager.onBlockPlaced(level, pos);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        if (event.getState().is(ASSEMBLER_CORE) || event.getState().is(ASSEMBLER_INPUT)
                || event.getState().is(ASSEMBLER_OUTPUT) || event.getState().is(ASSEMBLER_ENERGY)) {
            AssemblerManager.onBlockBroken(level, pos);
        }
    }
}
