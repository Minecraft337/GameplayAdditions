package com.gameplayadditions.mechanics.environment.magnet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * MagnetStructure — NeoForge-side wrapper around {@link MagnetManager#isActiveAt(String, BlockPos)}.
 *
 * <p>Порт {@code com.mcplugin.mechanics.environment.magnet.MagnetStructure} из MC-Plugin.
 * В Bukkit-версии был single-class helper, который проверял активные магнитные структуры.
 * В NeoForge необходимо явно передавать {@link ServerLevel}, т.к. {@link BlockPos} не
 * привязан к измерению.
 */
public final class MagnetStructure {

    private MagnetStructure() {
        // utility class
    }

    /**
     * Проверяет, активна ли магнитная структура в данной позиции.
     *
     * @param level уровень, в котором находится позиция
     * @param pos   проверяемая позиция
     * @return {@code true}, если в этой точке находится активный магнит
     */
    public static boolean isActive(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        MagnetManager manager = MagnetManager.get();
        if (manager == null) {
            return false;
        }
        String worldId = level.dimension().location().toString();
        return manager.isActiveAt(worldId, pos);
    }

    /**
     * Перегрузка для клиентского {@link Level} — возвращает {@code false},
     * поскольку магниты активны только на сервере.
     */
    public static boolean isActive(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return isActive(serverLevel, pos);
    }
}
