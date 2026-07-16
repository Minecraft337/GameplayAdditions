package com.gameplayadditions.display;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * PlayerSettingsDB — настройки отображения для каждого игрока.
 * Портирован из MC-Plugin.
 */
public class PlayerSettingsDB {

    public static boolean isBossbarEnabled(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT bossbar FROM player_settings WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("bossbar") == 1;
        } catch (Exception e) {
            ConsoleLogger.warn("[PlayerSettings] bossbar check error: " + e.getMessage());
        }
        return true;
    }

    public static boolean isScoreboardEnabled(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT scoreboard FROM player_settings WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("scoreboard") == 1;
        } catch (Exception e) {}
        return true;
    }

    public static boolean isPingEnabled(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT ping_sound FROM player_settings WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("ping_sound") == 1;
        } catch (Exception e) {}
        return true;
    }
}
