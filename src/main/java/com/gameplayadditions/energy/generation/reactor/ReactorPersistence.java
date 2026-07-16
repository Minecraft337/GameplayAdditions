package com.gameplayadditions.energy.generation.reactor;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * ReactorPersistence — сохранение/загрузка реактора из SQLite.
 * <p>
 * Порт {@code com.mcplugin.energy.generation.reactor.ReactorPersistence} из MC-Plugin.
 */
public class ReactorPersistence {

    /**
     * Сохраняет состояние реактора в БД.
     */
    public static void saveToDb(ReactorState state) {
        BlockPos pos = state.getReactorPos();
        String id = state.getReactorId();
        if (pos == null || id == null) return;

        String worldKey = state.getReactorLevel() != null
                ? state.getReactorLevel().dimension().location().toString()
                : "unknown";

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO reactors
                (reactor_id, world, x, y, z,
                 core_temp, core_press, core_sh_int,
                 core_case_temp, core_case_press, core_case_int,
                 recipe_time, self_destruct,
                 reactor_wear, energy_generated)
                VALUES (?, ?, ?, ?, ?,
                        ?, ?, ?,
                        ?, ?, ?,
                        ?, ?,
                        ?, ?)
            """)) {

            ps.setString(1, id);
            ps.setString(2, worldKey);
            ps.setInt(3, pos.getX());
            ps.setInt(4, pos.getY());
            ps.setInt(5, pos.getZ());
            ps.setInt(6, state.getCoreTemp());
            ps.setInt(7, state.getCorePress());
            ps.setInt(8, state.getCoreShInt());
            ps.setInt(9, state.getCoreCaseTemp());
            ps.setInt(10, state.getCoreCasePress());
            ps.setInt(11, state.getCoreCaseInt());
            ps.setInt(12, state.getRecipeTime());
            ps.setInt(13, state.isSelfDestruct() ? 1 : 0);
            ps.setInt(14, state.getReactorWear());
            ps.setLong(15, state.getEnergyGenerated());

            ps.executeUpdate();

            ConsoleLogger.info("[Reactor] Saved reactor " + id);
        } catch (Exception e) {
            ConsoleLogger.error("[Reactor] Save error: " + e.getMessage());
        }
    }

    /**
     * Загружает реактор из БД.
     */
    public static boolean loadFromDb(ReactorState state) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM reactors");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String worldKey = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");

                // We can't get Level here during DB init (no server handle),
                // so we just load parameters. Level will be resolved later.
                // For now, store the raw values.
                state.setReactorLocation(null, new BlockPos(x, y, z));
                // Store worldKey in a temporary field by overriding id
                state.setCoreTemp(rs.getInt("core_temp"));
                state.setCorePress(rs.getInt("core_press"));
                state.setCoreShInt(rs.getInt("core_sh_int"));
                state.setCoreCaseTemp(rs.getInt("core_case_temp"));
                state.setCoreCasePress(rs.getInt("core_case_press"));
                state.setCoreCaseInt(rs.getInt("core_case_int"));
                state.setRecipeTime(rs.getInt("recipe_time"));
                state.setSelfDestruct(rs.getInt("self_destruct") == 1);

                try { state.setReactorWear(rs.getInt("reactor_wear")); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load reactor_wear: " + e.getMessage());
                }
                try { state.setEnergyGenerated(rs.getLong("energy_generated")); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load energy_generated: " + e.getMessage());
                }

                ConsoleLogger.info("[Reactor] Loaded reactor state from DB");
                return true;
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Reactor] Load error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Удаляет реактор из БД.
     */
    public static void deleteFromDb(String reactorId) {
        if (reactorId == null) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM reactors WHERE reactor_id = ?")) {
            ps.setString(1, reactorId);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Reactor] Delete error: " + e.getMessage());
        }
    }
}
