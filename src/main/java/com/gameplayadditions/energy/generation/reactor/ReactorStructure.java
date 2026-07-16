package com.gameplayadditions.energy.generation.reactor;

import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * ReactorStructure — валидация структуры реактора.
 * <p>
 * Порт {@code com.mcplugin.energy.generation.reactor.ReactorStructure} из MC-Plugin.
 * Адаптирован для NeoForge: использует BlockPos/Level вместо Bukkit Location/World.
 * <p>
 * Реактор — это структура 5×6×6 с центром = позиция рамки (item frame).
 * Все смещения относительны центра.
 * Валидация проверяет ключевые функциональные блоки, а не всю структуру.
 */
public class ReactorStructure {

    // =========================
    // KEY FUNCTIONAL BLOCKS (relative to center)
    // =========================
    private static final BlockPos BULB_COOL      = new BlockPos(1, 0, -2);
    private static final BlockPos BULB_HEAT      = new BlockPos(-1, 0, -2);
    private static final BlockPos BULB_SH_INT    = new BlockPos(-1, 0, 2);
    private static final BlockPos BULB_CASE_INT  = new BlockPos(1, 0, 2);
    private static final BlockPos DIAMOND_BARREL = new BlockPos(0, -3, -2);
    private static final BlockPos GOLD_BARREL    = new BlockPos(0, -3, 2);
    private static final BlockPos LEVER          = new BlockPos(-1, 0, -3);
    private static final BlockPos LEVER_COOL     = new BlockPos(1, 0, -3);
    private static final BlockPos[] WALL_SIGNS = {
        new BlockPos(-1, -4, -3),
        new BlockPos(0, -4, -3),
        new BlockPos(1, -4, -3)
    };
    private static final BlockPos UPPER_CORE = new BlockPos(0, -1, 0);
    private static final BlockPos LOWER_CORE = new BlockPos(0, -5, 0);

    // =========================
    // VALIDATE STRUCTURE
    // =========================
    public static boolean isValid(BlockPos center, Level level) {
        return isValid(center, level, true);
    }

    public static boolean isValid(BlockPos center, Level level, boolean requireFrame) {
        if (center == null || level == null) return false;

        // 1. Copper bulbs
        if (!isBlock(level, center, BULB_COOL, Blocks.WAXED_COPPER_BULB)) return false;
        if (!isBlock(level, center, BULB_HEAT, Blocks.WAXED_COPPER_BULB)) return false;
        if (!isBlock(level, center, BULB_SH_INT, Blocks.WAXED_COPPER_BULB)) return false;
        if (!isBlock(level, center, BULB_CASE_INT, Blocks.WAXED_COPPER_BULB)) return false;

        // 2. Barrels
        if (!isBlock(level, center, DIAMOND_BARREL, Blocks.BARREL)) return false;
        if (!isBlock(level, center, GOLD_BARREL, Blocks.BARREL)) return false;

        // 3. Core blocks
        if (!isBlock(level, center, UPPER_CORE, Blocks.POLISHED_BLACKSTONE)) return false;
        if (!isBlock(level, center, LOWER_CORE, Blocks.POLISHED_BLACKSTONE)) return false;

        // 4. Levers (optional)
        if (!isBlockOrAir(level, center, LEVER, Blocks.LEVER)) return false;
        if (!isBlockOrAir(level, center, LEVER_COOL, Blocks.LEVER)) return false;

        // 5. Wall signs (south face)
        for (BlockPos signPos : WALL_SIGNS) {
            if (!isAnyWallSign(level, center, signPos)) return false;
        }

        // 6. Structure walls
        if (!hasSolidWalls(level, center)) return false;
        if (!hasSolidFloor(level, center)) return false;

        // 7. Item frame
        if (requireFrame && !hasItemFrame(level, center)) return false;

        return true;
    }

    // =========================
    // FIND CENTER FROM ENTITY LOCATION
    // =========================
    public static BlockPos findCenter(BlockPos entityPos, Level level) {
        BlockPos center = locateCenter(entityPos, level);
        if (center != null && isValid(center, level)) {
            return center;
        }
        return null;
    }

    public static BlockPos locateCenter(BlockPos searchPos, Level level) {
        if (searchPos == null || level == null) return null;

        int bx = searchPos.getX(), by = searchPos.getY(), bz = searchPos.getZ();

        // Scan ±5 in X/Z, -7 to +3 in Y for barrel pair
        for (int x = bx - 5; x <= bx + 5; x++) {
            for (int y = by - 7; y <= by + 3; y++) {
                for (int z = bz - 5; z <= bz + 5; z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.BARREL)) {
                        // Barrel at rel (0, -3, -2) → center = (x, y+3, z+2)
                        // Verify second barrel at (x, y, z+4)
                        if (level.getBlockState(new BlockPos(x, y, z + 4)).is(Blocks.BARREL)) {
                            return new BlockPos(x, y + 3, z + 2);
                        }
                    }
                }
            }
        }
        return null;
    }

    // =========================
    // VALIDATION ERRORS
    // =========================
    public static List<String> getValidationErrors(BlockPos center, Level level) {
        List<String> errors = new ArrayList<>();
        if (center == null || level == null) {
            errors.add("§c[1] Reactor center is null");
            return errors;
        }

        checkBlockDetailed(errors, level, center, BULB_COOL, Blocks.WAXED_COPPER_BULB,
                "§6[1] Cooling bulb (1, 0, -2) — must be WAXED_COPPER_BULB");
        checkBlockDetailed(errors, level, center, BULB_HEAT, Blocks.WAXED_COPPER_BULB,
                "§6[2] Heating bulb (-1, 0, -2) — must be WAXED_COPPER_BULB");
        checkBlockDetailed(errors, level, center, BULB_SH_INT, Blocks.WAXED_COPPER_BULB,
                "§6[3] Shell integrity bulb (-1, 0, 2) — must be WAXED_COPPER_BULB");
        checkBlockDetailed(errors, level, center, BULB_CASE_INT, Blocks.WAXED_COPPER_BULB,
                "§6[4] Case integrity bulb (1, 0, 2) — must be WAXED_COPPER_BULB");
        checkBlockDetailed(errors, level, center, DIAMOND_BARREL, Blocks.BARREL,
                "§6[5] Diamond barrel (0, -3, -2) — must be BARREL");
        checkBlockDetailed(errors, level, center, GOLD_BARREL, Blocks.BARREL,
                "§6[6] Gold barrel (0, -3, 2) — must be BARREL");
        checkBlockDetailed(errors, level, center, UPPER_CORE, Blocks.POLISHED_BLACKSTONE,
                "§6[6.5] Upper core (0, -1, 0) — must be POLISHED_BLACKSTONE");
        checkBlockDetailed(errors, level, center, LOWER_CORE, Blocks.POLISHED_BLACKSTONE,
                "§6[6.6] Lower core (0, -5, 0) — must be POLISHED_BLACKSTONE");
        checkBlockDetailedOptional(errors, level, center, LEVER, Blocks.LEVER,
                "§6[7] Heating lever (-1, 0, -3) — optional");
        checkBlockDetailedOptional(errors, level, center, LEVER_COOL, Blocks.LEVER,
                "§6[7.2] Cooling lever (1, 0, -3) — optional");

        for (int i = 0; i < WALL_SIGNS.length; i++) {
            BlockPos signPos = WALL_SIGNS[i];
            if (!isAnyWallSign(level, center, signPos)) {
                errors.add("§6[8." + (i+1) + "] Wall sign at " + formatOffset(signPos) + " — not found");
            }
        }

        checkSolidWallsDetailed(errors, level, center);
        checkSolidFloorDetailed(errors, level, center);

        if (!hasItemFrame(level, center)) {
            errors.add("§6[11] Item frame at (0, 0, 0) — not found on top of core");
        }

        return errors;
    }

    // =========================
    // HELPERS
    // =========================
    private static String formatOffset(BlockPos offset) {
        return "(" + offset.getX() + ", " + offset.getY() + ", " + offset.getZ() + ")";
    }

    private static void checkBlockDetailed(List<String> errors, Level level, BlockPos center,
                                            BlockPos offset, Block expected, String desc) {
        BlockPos pos = center.offset(offset);
        BlockState actual = level.getBlockState(pos);
        if (!actual.is(expected)) {
            errors.add(desc + ". Current: " + actual.getBlock());
        }
    }

    private static void checkBlockDetailedOptional(List<String> errors, Level level, BlockPos center,
                                                    BlockPos offset, Block expected, String desc) {
        BlockPos pos = center.offset(offset);
        BlockState actual = level.getBlockState(pos);
        if (!actual.is(expected) && !actual.is(Blocks.AIR)) {
            errors.add(desc + ". Current: " + actual.getBlock());
        }
    }

    private static void checkSolidWallsDetailed(List<String> errors, Level level, BlockPos center) {
        int relY = -2;
        BlockPos[] checkPositions = {
            new BlockPos(-2, relY, -2), new BlockPos(0, relY, -2), new BlockPos(2, relY, -2),
            new BlockPos(-2, relY, 2),  new BlockPos(0, relY, 2),  new BlockPos(2, relY, 2),
            new BlockPos(-2, relY, 0),  new BlockPos(2, relY, 0)
        };

        for (int i = 0; i < checkPositions.length; i++) {
            BlockPos worldPos = center.offset(checkPositions[i]);
            BlockState state = level.getBlockState(worldPos);
            if (state.isAir()) {
                errors.add("§6[9." + (i+1) + "] Wall at " + formatOffset(checkPositions[i]) + " — empty (AIR)");
            }
        }
    }

    private static void checkSolidFloorDetailed(List<String> errors, Level level, BlockPos center) {
        BlockPos[] floorPositions = {
            new BlockPos(-2, -5, -2), new BlockPos(-1, -5, -2), new BlockPos(0, -5, -2),
            new BlockPos(1, -5, -2), new BlockPos(2, -5, -2),
            new BlockPos(-2, -5, 0), new BlockPos(0, -5, 0), new BlockPos(2, -5, 0),
            new BlockPos(-2, -5, 2), new BlockPos(0, -5, 2), new BlockPos(2, -5, 2)
        };

        for (BlockPos checkPos : floorPositions) {
            BlockPos worldPos = center.offset(checkPos);
            if (level.getBlockState(worldPos).isAir()) {
                errors.add("§6[10] Floor at " + formatOffset(checkPos) + " — empty (AIR)");
            }
        }
    }

    private static boolean hasSolidWalls(Level level, BlockPos center) {
        int relY = -2;
        BlockPos[] checkPositions = {
            new BlockPos(-2, relY, -2), new BlockPos(0, relY, -2), new BlockPos(2, relY, -2),
            new BlockPos(-2, relY, 2),  new BlockPos(0, relY, 2),  new BlockPos(2, relY, 2),
            new BlockPos(-2, relY, 0),  new BlockPos(2, relY, 0)
        };

        for (BlockPos checkPos : checkPositions) {
            BlockPos worldPos = center.offset(checkPos);
            BlockState state = level.getBlockState(worldPos);
            if (state.isAir()) return false;
        }
        return true;
    }

    private static boolean hasSolidFloor(Level level, BlockPos center) {
        BlockPos[] floorPositions = {
            new BlockPos(-2, -5, -2), new BlockPos(-1, -5, -2), new BlockPos(0, -5, -2),
            new BlockPos(1, -5, -2), new BlockPos(2, -5, -2),
            new BlockPos(-2, -5, 0), new BlockPos(0, -5, 0), new BlockPos(2, -5, 0),
            new BlockPos(-2, -5, 2), new BlockPos(0, -5, 2), new BlockPos(2, -5, 2)
        };

        for (BlockPos checkPos : floorPositions) {
            if (level.getBlockState(center.offset(checkPos)).isAir()) return false;
        }
        return true;
    }

    private static boolean hasItemFrame(Level level, BlockPos center) {
        AABB searchBox = new AABB(center.getX() - 1, center.getY() - 1, center.getZ() - 1,
                                  center.getX() + 2, center.getY() + 2, center.getZ() + 2);
        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, searchBox);
        return !frames.isEmpty();
    }

    private static boolean isBlock(Level level, BlockPos center, BlockPos offset, Block expected) {
        BlockPos pos = center.offset(offset);
        return level.getBlockState(pos).is(expected);
    }

    private static boolean isBlockOrAir(Level level, BlockPos center, BlockPos offset, Block expected) {
        BlockPos pos = center.offset(offset);
        BlockState actual = level.getBlockState(pos);
        return actual.is(expected) || actual.is(Blocks.AIR);
    }

    private static boolean isAnyWallSign(Level level, BlockPos center, BlockPos offset) {
        BlockPos pos = center.offset(offset);
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.OAK_WALL_SIGN)
            || state.is(Blocks.DARK_OAK_WALL_SIGN)
            || state.is(Blocks.BIRCH_WALL_SIGN)
            || state.is(Blocks.SPRUCE_WALL_SIGN)
            || state.is(Blocks.JUNGLE_WALL_SIGN)
            || state.is(Blocks.ACACIA_WALL_SIGN)
            || state.is(Blocks.CHERRY_WALL_SIGN)
            || state.is(Blocks.MANGROVE_WALL_SIGN)
            || state.is(Blocks.CRIMSON_WALL_SIGN)
            || state.is(Blocks.WARPED_WALL_SIGN);
    }
}
