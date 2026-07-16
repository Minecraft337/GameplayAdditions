package com.gameplayadditions.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * LocationUtil — утилиты для работы с позициями/координатами.
 * <p>
 * Аналог {@code com.mcplugin.util.LocationUtil} из MC-Plugin.
 * Адаптирован для NeoForge: использует BlockPos и Level вместо Bukkit Location/World.
 */
public final class LocationUtil {

    private LocationUtil() {}

    // =========================
    // CONSTANTS
    // =========================
    public static final int COORD_OFFSET = 33554432;
    public static final int Y_OFFSET = 64;

    // =========================
    // KEY (long packing for cable network)
    // =========================
    public static long toKey(int x, int y, int z) {
        return ((long) (x + COORD_OFFSET) << 38)
             | ((long) (z + COORD_OFFSET) << 12)
             | ((y + Y_OFFSET) & 0xFFFL);
    }

    public static long toKey(BlockPos pos) {
        return toKey(pos.getX(), pos.getY(), pos.getZ());
    }

    public static int getX(long key) {
        return (int) ((key >>> 38) & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public static int getZ(long key) {
        return (int) ((key >>> 12) & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public static int getY(long key) {
        return (int) (key & 0xFFFL) - Y_OFFSET;
    }

    // =========================
    // NORMALIZE
    // =========================
    public static BlockPos normalize(BlockPos pos) {
        if (pos == null) return null;
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    // =========================
    // NEIGHBOR KEYS
    // =========================
    public static long[] getNeighborKeys(long key) {
        int x = getX(key);
        int y = getY(key);
        int z = getZ(key);
        return new long[]{
            toKey(x + 1, y, z),
            toKey(x - 1, y, z),
            toKey(x, y + 1, z),
            toKey(x, y - 1, z),
            toKey(x, y, z + 1),
            toKey(x, y, z - 1)
        };
    }

    /**
     * Возвращает соседние BlockPos для кабельных соединений.
     */
    public static List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>(6);
        neighbors.add(pos.above());
        neighbors.add(pos.below());
        neighbors.add(pos.north());
        neighbors.add(pos.south());
        neighbors.add(pos.east());
        neighbors.add(pos.west());
        return neighbors;
    }

    // =========================
    // FULL CONNECTION CHECK
    // Два блока соединены, если разница только по одной оси.
    // =========================
    public static boolean isFullyConnected(long keyA, long keyB) {
        int ax = getX(keyA), ay = getY(keyA), az = getZ(keyA);
        int bx = getX(keyB), by = getY(keyB), bz = getZ(keyB);

        int dx = Math.abs(ax - bx);
        int dy = Math.abs(ay - by);
        int dz = Math.abs(az - bz);

        // Должна быть разница ровно по одной оси, и ровно на 1 блок
        int nonZero = (dx > 0 ? 1 : 0) + (dy > 0 ? 1 : 0) + (dz > 0 ? 1 : 0);
        return nonZero == 1 && (dx + dy + dz) == 1;
    }

    public static boolean isFullyConnected(BlockPos a, BlockPos b) {
        return isFullyConnected(toKey(a), toKey(b));
    }

    // =========================
    // TO LOCATION
    // =========================
    public static BlockPos toPosition(long key, Level level) {
        return new BlockPos(getX(key), getY(key), getZ(key));
    }

    // =========================
    // WORLD-DIMENSION KEY
    // =========================
    /**
     * Создаёт строковый ключ мира для использования в HashMap.
     * Использует dimension location (ResourceLocation) как идентификатор.
     */
    public static String worldKey(Level level) {
        if (level == null || level.dimension() == null) return "unknown";
        return level.dimension().location().toString();
    }

    // =========================
    // UTILITY
    // =========================
    public static boolean isSameBlock(Level world, BlockPos pos, Level otherWorld, BlockPos otherPos) {
        return world != null && world.equals(otherWorld) && pos.equals(otherPos);
    }

    public static String posToString(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public static String vecToString(Vec3 vec) {
        return String.format("%.1f, %.1f, %.1f", vec.x, vec.y, vec.z);
    }

    public static double distance(BlockPos a, BlockPos b) {
        return Math.sqrt(
                Math.pow(a.getX() - b.getX(), 2) +
                Math.pow(a.getY() - b.getY(), 2) +
                Math.pow(a.getZ() - b.getZ(), 2)
        );
    }

    /**
     * Parst long ключ из строки и возвращает BlockPos.
     * Используется в ParticleAcceleratorManager для восстановления позиции из Map<String, Integer>.
     */
    public static BlockPos fromKey(String keyString) {
        try {
            long key = Long.parseLong(keyString);
            return new BlockPos(getX(key), getY(key), getZ(key));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Добавляет import net.minecraft.world.phys.Vec3;
     */
}
