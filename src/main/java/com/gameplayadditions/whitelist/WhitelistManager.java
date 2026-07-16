package com.gameplayadditions.whitelist;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.GameplayAdditionsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 📋 WhitelistManager — кастомный вайтлист.
 */
public class WhitelistManager {

    private static boolean enabled = false;

    public static void init() {
        load();
    }

    public static void load() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT value FROM whitelist_meta WHERE key = ?")) {
            st.setString(1, "enabled");
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    enabled = Boolean.parseBoolean(rs.getString("value"));
                }
            }
            int count = 0;
            try (PreparedStatement cnt = con.prepareStatement("SELECT COUNT(*) FROM whitelist");
                 ResultSet rs = cnt.executeQuery()) {
                if (rs.next()) count = rs.getInt(1);
            }
            ConsoleLogger.info("[Whitelist] Loaded " + count + " players, enabled=" + enabled);
        } catch (Exception e) {
            ConsoleLogger.warn("[Whitelist] Failed to load: " + e.getMessage());
        }
    }

    public static boolean isEnabled() { return enabled; }

    public static List<String> getWhitelistNames() {
        List<String> result = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM whitelist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[Whitelist] Failed to list: " + e.getMessage());
        }
        return result;
    }

    public static boolean add(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO whitelist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Whitelist] Failed to add: " + lower + " - " + e.getMessage());
            return false;
        }
    }

    public static boolean remove(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Whitelist] Failed to remove: " + lower + " - " + e.getMessage());
            return false;
        }
    }

    public static boolean setEnabled(boolean val) {
        if (enabled == val) return false;
        enabled = val;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO whitelist_meta (key, value) VALUES (?, ?)")) {
            st.setString(1, "enabled");
            st.setString(2, String.valueOf(val));
            st.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.warn("[Whitelist] Failed to save state: " + e.getMessage());
        }
        return true;
    }

    public static boolean isWhitelisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
