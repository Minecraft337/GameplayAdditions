package com.gameplayadditions.mechanics.lightning;

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
 * LightningStructure — валидация структуры молний.
 * <p>
 * Порт {@code com.mcplugin.mechanics.environment.lightning.LightningStructure} из MC-Plugin.
 * 3-уровневая структура: громоотвод + лампочка + решётка + резная медь.
 */
public class LightningStructure {

    // Y= 0: громоотвод
    private static final BlockPos[] LIGHTNING_ROD_POS = { new BlockPos(0, 0, 0) };
    // Y=-1: лампочка + 4 люка
    // Y=-2: решётка + 4 ступени + 4 люка
    // Y=-3: резная медь + крест + углы + люки

    public static boolean isValid(BlockPos center, Level level) {
        return isValid(center, level, true);
    }

    public static boolean isValid(BlockPos center, Level level, boolean requireFrame) {
        if (center == null || level == null) return false;

        // 1. Lightning rod at center
        if (!level.getBlockState(center).is(Blocks.LIGHTNING_ROD)) return false;

        // 2. Bulb at Y=-1
        if (!level.getBlockState(center.below(1)).is(Materials.WAXED_COPPER_BULB)) return false;

        // 3. Grate at Y=-2
        if (!level.getBlockState(center.below(2)).is(Materials.WAXED_COPPER_GRATE)) return false;

        // 4. Chiseled at Y=-3
        if (!level.getBlockState(center.below(3)).is(Materials.WAXED_CHISELED_COPPER)) return false;

        // 5. Item frame on top
        if (requireFrame && !hasItemFrameOnTop(center, level)) return false;

        return true;
    }

    private static boolean hasItemFrameOnTop(BlockPos center, Level level) {
        AABB box = new AABB(center.getX(), center.getY() + 1, center.getZ(),
                            center.getX() + 1, center.getY() + 2, center.getZ() + 1);
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
            return true;
        }
        return false;
    }

    public static BlockPos findCenter(BlockPos near, Level level) {
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos check = near.offset(x, y, z);
                    if (level.getBlockState(check).is(Blocks.LIGHTNING_ROD)) {
                        // Quick check: chiseled copper 3 blocks below
                        if (level.getBlockState(check.below(3)).is(Materials.WAXED_CHISELED_COPPER)) {
                            if (isValid(check, level, false)) return check;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos getEnergyInputPos(BlockPos center) {
        return center.below(3);
    }

    public static boolean isPartOfStructure(BlockPos center, BlockPos check) {
        int dx = check.getX() - center.getX();
        int dy = check.getY() - center.getY();
        int dz = check.getZ() - center.getZ();
        return dx >= -2 && dx <= 2 && dy >= -3 && dy <= 0 && dz >= -2 && dz <= 2;
    }

    public static List<String> getValidationErrors(BlockPos center, Level level) {
        List<String> errors = new ArrayList<>();
        if (center == null || level == null) {
            errors.add("§c[1] Center is null");
            return errors;
        }

        if (!level.getBlockState(center).is(Blocks.LIGHTNING_ROD))
            errors.add("§6[1] Lightning rod not found at center");
        if (!level.getBlockState(center.below(1)).is(Materials.WAXED_COPPER_BULB))
            errors.add("§6[2] Copper bulb missing at Y=-1");
        if (!level.getBlockState(center.below(2)).is(Materials.WAXED_COPPER_GRATE))
            errors.add("§6[3] Copper grate missing at Y=-2");
        if (!level.getBlockState(center.below(3)).is(Materials.WAXED_CHISELED_COPPER))
            errors.add("§6[4] Chiseled copper missing at Y=-3");
        if (!hasItemFrameOnTop(center, level))
            errors.add("§6[5] Item frame not found on top");

        return errors;
    }
}
