package com.gameplayadditions.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockUtil — утилиты для работы с блоками.
 * Портирован из MC-Plugin для NeoForge.
 */
public final class BlockUtil {

    private BlockUtil() {}

    /**
     * Проверяет, является ли блок кабелем.
     * Делегирует проверку в систему кабельной сети.
     */
    public static boolean isCable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(com.gameplayadditions.energy.util.Materials.WAXED_LIGHTNING_ROD)
            || state.is(com.gameplayadditions.energy.util.Materials.WAXED_CHISELED_COPPER);
    }

    /**
     * Проверяет, может ли блок быть подключён к кабельной сети.
     */
    public static boolean isConnectable(Level level, BlockPos pos) {
        return isCable(level, pos);
    }

    /**
     * Проверяет, является ли блок прямым кабелем (только lightning rod).
     */
    public static boolean isStraightCable(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(com.gameplayadditions.energy.util.Materials.WAXED_LIGHTNING_ROD);
    }

    /**
     * Проверяет, является ли блок junction/угловым кабелем.
     */
    public static boolean isJunction(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(com.gameplayadditions.energy.util.Materials.WAXED_CHISELED_COPPER);
    }
}
