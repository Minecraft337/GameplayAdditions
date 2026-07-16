package com.gameplayadditions.mechanics.homes;

import com.gameplayadditions.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HomeDatabase — SQLite операции для таблицы player_homes.
 * <p>
 * Порт {@code com.mcplugin.command.home.HomeDatabase} из MC-Plugin.
 * Таблица уже создаётся в {@link com.gameplayadditions.database.DatabaseInit}.
 */
public final class HomeDatabase {

    private static final int MAX_HOMES = 10;
    private static final int NAME_MIN = 1;
    private static final int NAME_MAX = 16;

    private HomeDatabase() {}

    public static int getMaxHomes() { return MAX_HOMES; }
    public static int getNameMin() { return NAME_MIN; }
    public static int getNameMax() { return NAME_MAX; }

    public static int countHomes(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM player_homes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static boolean saveHome(UUID uuid, String homeName, String world,
                                   double x, double y, double z, float yaw, float pitch) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     INSERT OR REPLACE INTO player_homes
                     (uuid, home_name, world, x, y, z, yaw, pitch)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, homeName.toLowerCase(Locale.ROOT));
            ps.setString(3, world);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.setFloat(7, yaw);
            ps.setFloat(8, pitch);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static HomeData getHome(UUID uuid, String homeName) {
        String key = homeName.toLowerCase(Locale.ROOT);
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT world, x, y, z, yaw, pitch FROM player_homes WHERE uuid = ? AND home_name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new HomeData(
                        homeName,
                        rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch")
                );
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean homeExists(UUID uuid, String homeName) {
        return getHome(uuid, homeName) != null;
    }

    public static boolean deleteHome(UUID uuid, String homeName) {
        String key = homeName.toLowerCase(Locale.ROOT);
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM player_homes WHERE uuid = ? AND home_name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<HomeData> listHomes(UUID uuid) {
        List<HomeData> list = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT home_name, world, x, y, z, yaw, pitch FROM player_homes WHERE uuid = ? ORDER BY home_name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HomeData(
                            rs.getString("home_name"), rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch")
                    ));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static List<String> getHomeNames(UUID uuid) {
        List<String> names = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT home_name FROM player_homes WHERE uuid = ? ORDER BY home_name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("home_name"));
                }
            }
        } catch (Exception ignored) {}
        return names;
    }

    public record HomeData(
            String homeName, String world,
            double x, double y, double z,
            float yaw, float pitch
    ) {}
}
