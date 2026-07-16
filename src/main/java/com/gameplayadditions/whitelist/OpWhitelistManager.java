package com.gameplayadditions.whitelist;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.util.ConsoleLogger;
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
 * 🛡 OP Whitelist — белый список операторов.
 */
public class OpWhitelistManager {

    private static boolean enabled = false;

    public static void init() {
        load();
    }

    public static void load() {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT value FROM op_whitelist_meta WHERE key = ?")) {
                st.setString(1, "enabled");
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        enabled = Boolean.parseBoolean(rs.getString("value"));
                    }
                }
            }
            int count = 0;
            try (PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM op_whitelist");
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) count = rs.getInt(1);
            }
            ConsoleLogger.info("[OpWhitelist] Loaded " + count + " players, enabled=" + enabled);
        } catch (Exception e) {
            ConsoleLogger.warn("[OpWhitelist] Failed to load: " + e.getMessage());
        }
    }

    public static boolean isEnabled() { return enabled; }

    public static List<String> getWhitelistNames() {
        List<String> result = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM op_whitelist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpWhitelist] Failed to list: " + e.getMessage());
        }
        return result;
    }

    public static boolean add(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO op_whitelist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpWhitelist] Failed to add: " + lower + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean remove(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM op_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpWhitelist] Failed to remove: " + lower + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean setEnabled(boolean val) {
        if (enabled == val) return false;
        enabled = val;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO op_whitelist_meta (key, value) VALUES (?, ?)")) {
            st.setString(1, "enabled");
            st.setString(2, String.valueOf(val));
            st.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpWhitelist] Failed to save enabled state: " + e.getMessage());
        }
        return true;
    }

    public static boolean isWhitelisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM op_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static void checkAndDeop(ServerPlayer player) {
        if (!enabled || player == null || !player.isAlive()) return;
        if (!player.hasPermissions(2)) return;
        if (isWhitelisted(player.getName().getString())) return;
        // In NeoForge, we can't directly deop - just log a warning
        ConsoleLogger.warn("[OpWhitelist] Player " + player.getName().getString() + " has OP but is not in OP whitelist!");
    }
}
