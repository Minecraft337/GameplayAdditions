package com.gameplayadditions.mechanics.environment.lightning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * ⚡ Lightning Structure — валидатор 5×4×5 «грозовой варки».
 * <p>
 * Порт {@code com.mcplugin.mechanics.environment.lightning.LightningStructure}
 * (Bukkit, ~230 строк). Layout относительно центра (= Громоотвод на Y=0):
 * <pre>
 *   Y= 0 : Lightning Rod at (0, 0, 0)  — обязательно с Item Frame на верхней грани
 *   Y=-1 : Copper Bulb at (0, -1, 0) + 4× Waxed Copper Trapdoor вокруг
 *   Y=-2 : Copper Grate at (0, -2, 0) + 4× Waxed Cut Copper Stairs + 4 угловых Trapdoor
 *   Y=-3 : Chiseled Copper at (0, -3, 0) + 4× Cut Copper крест +
 *          4 угловых Copper Block + 4 боковых Trapdoor
 * </pre>
 * Bounds: X = -2..+2, Y = -3..0, Z = -2..+2.
 * <p>
 * <b>Энергетический input:</b> Chiseled Copper on Y=-3 (offset 0, -3, 0).
 * MVP: проверка энергии делегирована вызывающему коду (см. {@link com.gameplayadditions.mechanics.features.lightning.LightCookingFeature}).
 */
public final class LightningStructure {

    private LightningStructure() {}

    // Структурные bounds (relative to center)
    public static final int BOUNDS_HALF = 2;                        // -2..+2 по X/Z
    public static final int STRUCTURE_MIN_Y_REL = -3;
    public static final int STRUCTURE_MAX_Y_REL = 0;

    // ── Y = -1
    private static final int[][] TRAPDOORS_INNER = {
            { 0, -1, -1}, {-1, -1,  0}, { 1, -1,  0}, { 0, -1,  1}
    };

    // ── Y = -2
    private static final int[][] STAIRS = {
            { 0, -2, -1}, {-1, -2,  0}, { 1, -2,  0}, { 0, -2,  1}
    };
    private static final int[][] TRAPDOORS_MID = {
            {-1, -2, -1}, { 1, -2, -1}, {-1, -2,  1}, { 1, -2,  1}
    };

    // ── Y = -3
    private static final int[][] CUT_COPPER_CROSS = {
            {-1, -3,  0}, { 1, -3,  0}, { 0, -3, -1}, { 0, -3,  1}
    };
    private static final int[][] COPPER_BLOCK_CORNERS = {
            {-1, -3, -1}, { 1, -3, -1}, {-1, -3,  1}, { 1, -3,  1}
    };
    private static final int[][] TRAPDOORS_OUTER = {
            { 0, -3, -2}, {-2, -3,  0}, { 2, -3,  0}, { 0, -3,  2}
    };

    /** Энергетический input — Chiseled Copper на (0, -3, 0). */
    public static BlockPos getEnergyInputPos(BlockPos center) {
        if (center == null) return null;
        return center.offset(0, -3, 0);
    }

    /** Является ли loc частью структуры (5×4×5 bounds). */
    public static boolean isPartOfStructure(BlockPos center, BlockPos loc) {
        if (center == null || loc == null) return false;
        int dx = loc.getX() - center.getX();
        int dy = loc.getY() - center.getY();
        int dz = loc.getZ() - center.getZ();
        return dx >= -BOUNDS_HALF && dx <= BOUNDS_HALF
                && dy >= STRUCTURE_MIN_Y_REL && dy <= STRUCTURE_MAX_Y_REL
                && dz >= -BOUNDS_HALF && dz <= BOUNDS_HALF;
    }

    /**
     * Полная валидация 5×4×5. {@code requireFrame=true} дополнительно проверяет,
     * что на верхней грани громоотвода висит ItemFrame (как в MC-Plugin).
     */
    public static boolean isValid(Level level, BlockPos center, boolean requireFrame) {
        if (level == null || center == null) return false;

        // 1. Громоотвод на (0, 0, 0). Поддерживаем WAXED и обычный — Bukkit плагин требует WAXED,
        //    но в 1.21 vanilla у Lightning Rod нет waxed-варианта (все wax-предметы только для copper).
        //    Пытаемся WAXED, если нет — fallback на обычный.
        Block rodBlock = level.getBlockState(center).getBlock();
        if (rodBlock != Blocks.LIGHTNING_ROD) {
            // Попытка найти waxed lightning rod в Blocks
            Block waxedRod = safeLookupBlock("minecraft:waxed_lightning_rod");
            if (waxedRod == null || rodBlock != waxedRod) return false;
        }

        // 2. Bulb at Y=-1
        if (!match(level, center, 0, -1, 0, "minecraft:waxed_copper_bulb",
                "minecraft:copper_bulb")) return false;

        // 3. Inner trapdoors Y=-1
        for (int[] p : TRAPDOORS_INNER) {
            if (!match(level, center, p, "minecraft:waxed_copper_trapdoor",
                    "minecraft:copper_trapdoor")) return false;
        }

        // 4. Grate Y=-2
        if (!match(level, center, 0, -2, 0, "minecraft:waxed_copper_grate",
                "minecraft:copper_grate")) return false;

        // 5. Stairs Y=-2
        for (int[] p : STAIRS) {
            if (!match(level, center, p, "minecraft:waxed_cut_copper_stairs",
                    "minecraft:cut_copper_stairs")) return false;
        }

        // 6. Mid trapdoors Y=-2 corners
        for (int[] p : TRAPDOORS_MID) {
            if (!match(level, center, p, "minecraft:waxed_copper_trapdoor",
                    "minecraft:copper_trapdoor")) return false;
        }

        // 7. Chiseled Y=-3
        if (!match(level, center, 0, -3, 0, "minecraft:waxed_chiseled_copper",
                "minecraft:chiseled_copper")) return false;

        // 8. Cut copper cross Y=-3
        for (int[] p : CUT_COPPER_CROSS) {
            if (!match(level, center, p, "minecraft:waxed_cut_copper",
                    "minecraft:cut_copper")) return false;
        }

        // 9. Copper block corners Y=-3
        for (int[] p : COPPER_BLOCK_CORNERS) {
            if (!match(level, center, p, "minecraft:waxed_copper_block",
                    "minecraft:copper_block")) return false;
        }

        // 10. Outer trapdoors Y=-3
        for (int[] p : TRAPDOORS_OUTER) {
            if (!match(level, center, p, "minecraft:waxed_copper_trapdoor",
                    "minecraft:copper_trapdoor")) return false;
        }

        // 11. Item frame on top face of rod
        if (requireFrame && !hasItemFrameOnTop(level, center)) return false;
        return true;
    }

    /**
     * Search ONLY in the block above the rod (Y = centerY + 1) and find an
     * ItemFrame whose direction is {@code UP}. Frame attached to bottom-face
     * of a block below the air-cell means UP. (Match Bukkit's
     * {@code frame.getAttachedFace() == BlockFace.DOWN} semantics.)
     */
    public static boolean hasItemFrameOnTop(Level level, BlockPos center) {
        AABB box = new AABB(center.getX(), center.getY() + 1, center.getZ(),
                center.getX() + 1, center.getY() + 2, center.getZ() + 1).inflate(0.05);
        return !level.getEntitiesOfClass(ItemFrame.class, box,
                frame -> frame.getDirection() == Direction.UP).isEmpty();
    }

    /**
     * Scan 7×5×7 area around {@code near} for a lightning-rod that has
     * chiseled copper exactly 3 blocks below it (i.e. a potential center).
     */
    public static BlockPos locateCenter(Level level, BlockPos near) {
        if (level == null || near == null) return null;
        int bx = near.getX(), by = near.getY(), bz = near.getZ();
        int scanRadius = 3;
        for (int x = bx - scanRadius; x <= bx + scanRadius; x++) {
            for (int y = by - 1; y <= by + 3; y++) {
                for (int z = bz - scanRadius; z <= bz + scanRadius; z++) {
                    BlockPos at = new BlockPos(x, y, z);
                    Block atBlock = level.getBlockState(at).getBlock();
                    boolean isRod = atBlock == Blocks.LIGHTNING_ROD
                            || (safeLookupBlock("minecraft:waxed_lightning_rod") != null
                                && atBlock == safeLookupBlock("minecraft:waxed_lightning_rod"));
                    if (!isRod) continue;
                    BlockPos down = at.offset(0, -3, 0);
                    Block downBlock = level.getBlockState(down).getBlock();
                    boolean downChiseled = downBlock == safeLookupBlock("minecraft:waxed_chiseled_copper")
                            || downBlock == safeLookupBlock("minecraft:chiseled_copper");
                    if (downChiseled) return at;
                }
            }
        }
        return null;
    }

    /** Подробный список расхождений для админ-фидбека. */
    public static List<String> getValidationErrors(Level level, BlockPos center) {
        List<String> errors = new ArrayList<>();
        if (level == null || center == null) {
            errors.add("§cCenter is null");
            return errors;
        }
        Block rod = level.getBlockState(center).getBlock();
        Block waxedRod = safeLookupBlock("minecraft:waxed_lightning_rod");
        if (rod != Blocks.LIGHTNING_ROD && (waxedRod == null || rod != waxedRod)) {
            errors.add("§6[1] Lightning rod §eat center§6 — must be LIGHTNING_ROD (or waxed)");
        }
        if (!match(level, center, 0, -1, 0, "minecraft:waxed_copper_bulb",
                "minecraft:copper_bulb")) {
            errors.add("§6[2] Copper bulb at (0,-1,0) — missing");
        }
        int badInner = 0;
        for (int[] p : TRAPDOORS_INNER) {
            if (!match(level, center, p, "minecraft:waxed_copper_trapdoor",
                    "minecraft:copper_trapdoor")) badInner++;
        }
        if (badInner > 0) errors.add("§6[3] Inner trapdoors Y=-1 — §e" + badInner + "§6 missing");
        if (!match(level, center, 0, -2, 0, "minecraft:waxed_copper_grate",
                "minecraft:copper_grate")) {
            errors.add("§6[4] Copper grate at (0,-2,0) — missing");
        }
        int badStairs = 0;
        for (int[] p : STAIRS) {
            if (!match(level, center, p, "minecraft:waxed_cut_copper_stairs",
                    "minecraft:cut_copper_stairs")) badStairs++;
        }
        if (badStairs > 0) errors.add("§6[5] Cut copper stairs Y=-2 — §e" + badStairs + "§6 missing");
        int badMidTr = 0;
        for (int[] p : TRAPDOORS_MID) {
            if (!match(level, center, p, "minecraft:waxed_copper_trapdoor",
                    "minecraft:copper_trapdoor")) badMidTr++;
        }
        if (badMidTr > 0) errors.add("§6[6] Corner trapdoors Y=-2 — §e" + badMidTr + "§6 missing");
        if (!match(level, center, 0, -3, 0, "minecraft:waxed_chiseled_copper",
                "minecraft:chiseled_copper")) {
            errors.add("§6[7] Chiseled copper at (0,-3,0) — missing");
        }
        int badCross = 0;
        for (int[] p : CUT_COPPER_CROSS) {
            if (!match(level, center, p, "minecraft:waxed_cut_copper",
                    "minecraft:cut_copper")) badCross++;
        }
        if (badCross > 0) errors.add("§6[8] Cut copper cross Y=-3 — §e" + badCross + "§6 missing");
        int badCorners = 0;
        for (int[] p : COPPER_BLOCK_CORNERS) {
            if (!match(level, center, p, "minecraft:waxed_copper_block",
                    "minecraft:copper_block")) badCorners++;
        }
        if (badCorners > 0) errors.add("§6[9] Copper block corners Y=-3 — §e" + badCorners + "§6 missing");
        int badOuterTr = 0;
        for (int[] p : TRAPDOORS_OUTER) {
            if (!match(level, center, p, "minecraft:waxed_copper_trapdoor",
                    "minecraft:copper_trapdoor")) badOuterTr++;
        }
        if (badOuterTr > 0) errors.add("§6[10] Outer trapdoors Y=-3 — §e" + badOuterTr + "§6 missing");
        if (!hasItemFrameOnTop(level, center)) {
            errors.add("§6[11] Item frame — not found on top face of lightning rod");
        }
        return errors;
    }

    // ==========================================================================
    // INTERNAL HELPERS
    // ==========================================================================

    /** Lookup с fallback на обычный (waxed-or-not). Возвращает null если не разрешён ни один. */
    private static boolean match(Level level, BlockPos center, int dx, int dy, int dz,
                                 String waxedKey, String plainKey) {
        BlockPos at = center.offset(dx, dy, dz);
        Block got = level.getBlockState(at).getBlock();
        Block waxed = safeLookupBlock(waxedKey);
        if (waxed != null && got == waxed) return true;
        Block plain = safeLookupBlock(plainKey);
        return plain != null && got == plain;
    }

    private static boolean match(Level level, BlockPos center, int[] relPos,
                                 String waxedKey, String plainKey) {
        return match(level, center, relPos[0], relPos[1], relPos[2], waxedKey, plainKey);
    }

    /** Registry lookup с guard от {@link NullPointerException} при отсутствии. */
    private static Block safeLookupBlock(String id) {
        if (id == null) return null;
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) return null;
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(rl).orElse(null);
    }
}
