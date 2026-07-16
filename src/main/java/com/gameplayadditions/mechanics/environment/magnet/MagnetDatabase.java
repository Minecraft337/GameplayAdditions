package com.gameplayadditions.mechanics.environment.magnet;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MagnetDatabase — SQLite persistence для центров магнитов.
 * <p>
 * Таблица {@code magnets} уже создана в {@code DatabaseInit}:
 * <pre>
 * CREATE TABLE magnets (id INTEGER PK, world TEXT, center_x INTEGER,
 *                       center_y INTEGER, center_z INTEGER,
 *                       block_count INTEGER DEFAULT 1, active INTEGER DEFAULT 1);
 * </pre>
 * <p>
 * MVP: загружаем центры по world → используем только координаты.
 * Связь с таблицей {@code magnet_blocks} (FLOOD-FILL граф блоков) — позже.
 */
public final class MagnetDatabase {

    private MagnetDatabase() {}

    /**
     * Загрузить все активные центры из БД в {@code centersByWorld}.
     *
     * @param centersByWorld карта world → список центров (immutable BlockPos).
     */
    public static void loadAll(Map<String, List<BlockPos>> centersByWorld) {
        if (centersByWorld == null) return;
        centersByWorld.clear();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT world, center_x, center_y, center_z FROM magnets WHERE active = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String world = rs.getString("world");
                    BlockPos pos = new BlockPos(
                            rs.getInt("center_x"),
                            rs.getInt("center_y"),
                            rs.getInt("center_z"));
                    centersByWorld.computeIfAbsent(world, k -> new ArrayList<>()).add(pos);
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Magnet] DB load failed: " + e.getMessage());
        }

        int total = centersByWorld.values().stream().mapToInt(List::size).sum();
        ConsoleLogger.info("[Magnet] Loaded " + total + " active magnet center(s) from DB.");
    }

    /**
     * Полная перезапись таблицы {@code magnets}. Сбрасывает AUTOINCREMENT-счётчик,
     * затем вставляет все центры. Используется при {@link #addCenter}/{@link #removeCenter}.
     */
    public static void persistAll(Map<String, List<BlockPos>> centersByWorld) {
        if (centersByWorld == null) return;
        try (Connection con = DatabaseManager.getConnection()) {
            boolean prevAuto = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                try (PreparedStatement del = con.prepareStatement("DELETE FROM magnets");
                     PreparedStatement ins = con.prepareStatement(
                             "INSERT INTO magnets (world, center_x, center_y, center_z, block_count, active) VALUES (?, ?, ?, ?, ?, 1)")) {
                    del.executeUpdate();
                    int count = 0;
                    for (Map.Entry<String, List<BlockPos>> e : centersByWorld.entrySet()) {
                        for (BlockPos pos : e.getValue()) {
                            ins.setString(1, e.getKey());
                            ins.setInt(2, pos.getX());
                            ins.setInt(3, pos.getY());
                            ins.setInt(4, pos.getZ());
                            ins.setInt(5, 1);
                            ins.addBatch();
                            count++;
                        }
                    }
                    ins.executeBatch();
                    con.commit();
                    ConsoleLogger.info("[Magnet] Persisted " + count + " magnet center(s) to DB.");
                }
            } catch (Exception inner) {
                con.rollback();
                throw inner;
            } finally {
                con.setAutoCommit(prevAuto);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Magnet] DB persist failed: " + e.getMessage());
        }
    }

    /** Утилита — defensive snapshot (для передачи в другие потоки). */
    public static Map<String, List<BlockPos>> snapshot(Map<String, List<BlockPos>> source) {
        Map<String, List<BlockPos>> copy = new HashMap<>();
        if (source == null) return copy;
        source.forEach((w, list) -> copy.put(w, new ArrayList<>(list)));
        return copy;
    }
}
