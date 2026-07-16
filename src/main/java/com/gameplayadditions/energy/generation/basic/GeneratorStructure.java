package com.gameplayadditions.energy.generation.basic;

import com.gameplayadditions.energy.util.Materials;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * GeneratorStructure — валидация структуры генератора.
 * <p>
 * Структура: BLAST_FURNACE + item frame на верхней грани.
 */
public class GeneratorStructure {

    public static boolean isValid(BlockPos center, Level level) {
        return isValid(center, level, true);
    }

    public static boolean isValid(BlockPos center, Level level, boolean requireFrame) {
        if (center == null || level == null) return false;
        if (!level.getBlockState(center).is(Materials.BLAST_FURNACE)) return false;
        if (requireFrame && !hasItemFrameOnTop(center, level)) return false;
        return true;
    }

    private static boolean hasItemFrameOnTop(BlockPos center, Level level) {
        AABB box = new AABB(center.getX(), center.getY() + 1, center.getZ(),
                            center.getX() + 1, center.getY() + 2, center.getZ() + 1);
        return !level.getEntitiesOfClass(ItemFrame.class, box).isEmpty();
    }

    public static List<String> getValidationErrors(BlockPos center, Level level) {
        List<String> errors = new ArrayList<>();
        if (center == null || level == null) {
            errors.add("§c[1] Center is null");
            return errors;
        }
        if (!level.getBlockState(center).is(Materials.BLAST_FURNACE)) {
            errors.add("§6[1] Must be BLAST_FURNACE at §f[" + center.toShortString() + "]");
        }
        if (!hasItemFrameOnTop(center, level)) {
            errors.add("§6[2] Item frame not found on top of blast furnace");
        }
        return errors;
    }
}
