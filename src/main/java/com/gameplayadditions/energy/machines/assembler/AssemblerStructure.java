package com.gameplayadditions.energy.machines.assembler;

import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * AssemblerStructure — валидация структуры авто-сборщика.
 * <p>
 * Порт {@code com.mcplugin.energy.machines.assembler.AssemblerStructure} из MC-Plugin.
 * Структура: CRAFTER + item frame на верхней грани.
 */
public class AssemblerStructure {

    public static boolean isValid(BlockPos center, Level level) {
        return isValid(center, level, true);
    }

    public static boolean isValid(BlockPos center, Level level, boolean requireFrame) {
        if (center == null || level == null) return false;
        if (!level.getBlockState(center).is(Blocks.CRAFTER)) return false;
        if (requireFrame && !hasItemFrameOnTop(center, level)) return false;
        return true;
    }

    private static boolean hasItemFrameOnTop(BlockPos center, Level level) {
        AABB box = new AABB(center.getX() - 0.5, center.getY() + 0.5, center.getZ() - 0.5,
                            center.getX() + 1.5, center.getY() + 1.6, center.getZ() + 1.5);
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
            if (frame.getDirection() == net.minecraft.core.Direction.UP) return true;
        }
        return false;
    }

    public static List<String> getValidationErrors(BlockPos center, Level level) {
        List<String> errors = new ArrayList<>();
        if (center == null || level == null) {
            errors.add("§c[1] Center is null");
            return errors;
        }
        if (!level.getBlockState(center).is(Blocks.CRAFTER)) {
            errors.add("§6[1] Must be CRAFTER at " + center.toShortString());
        }
        if (!hasItemFrameOnTop(center, level)) {
            errors.add("§6[2] Item frame not found on top of CRAFTER");
        }
        return errors;
    }
}
