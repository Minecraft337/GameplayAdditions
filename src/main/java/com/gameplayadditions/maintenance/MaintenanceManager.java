package com.gameplayadditions.maintenance;

import com.gameplayadditions.GameplayAdditionsMod;
import com.gameplayadditions.config.MessagesManager;
import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maintenance mode manager.
 */
public class MaintenanceManager {

    private static MaintenanceManager instance;
    private boolean maintenanceMode = false;

    private MaintenanceManager() {}

    public static void init() {
        instance = new MaintenanceManager();
        instance.loadFromDb();
    }

    public static MaintenanceManager getInstance() {
        return instance;
    }

    public void loadFromDb() {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement st = con.prepareStatement(
                     "SELECT value FROM maintenance_meta WHERE key = ?")) {
                st.setString(1, "enabled");
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        maintenanceMode = Boolean.parseBoolean(rs.getString("value"));
                    } else {
                        maintenanceMode = false;
                        try (PreparedStatement ins = con.prepareStatement(
                                "INSERT OR REPLACE INTO maintenance_meta (key, value) VALUES (?, ?)")) {
                            ins.setString(1, "enabled");
                            ins.setString(2, "false");
                            ins.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[Maintenance] Failed to load state from DB: " + e.getMessage());
        }
    }

    private void saveStateToDb() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO maintenance_meta (key, value) VALUES (?, ?)")) {
            st.setString(1, "enabled");
            st.setString(2, String.valueOf(maintenanceMode));
            st.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.warn("[Maintenance] Failed to save state to DB: " + e.getMessage());
        }
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void enable(MinecraftServer server) {
        maintenanceMode = true;
        saveStateToDb();
        kickNonWhitelisted(server);
    }

    public void disable() {
        maintenanceMode = false;
        saveStateToDb();
    }

    public List<String> getWhitelistNames() {
        List<String> names = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT player_name FROM maintenance_whitelist ORDER BY player_name");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[Maintenance] Failed to list whitelist: " + e.getMessage());
        }
        return names;
    }

    public boolean isWhitelisted(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM maintenance_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean addWhitelist(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO maintenance_whitelist (player_name) VALUES (?)")) {
            st.setString(1, lower);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Maintenance] Failed to add: " + lower + " - " + e.getMessage());
            return false;
        }
    }

    public boolean removeWhitelist(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM maintenance_whitelist WHERE player_name = ?")) {
            st.setString(1, lower);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Maintenance] Failed to remove: " + lower + " - " + e.getMessage());
            return false;
        }
    }

    private void kickNonWhitelisted(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isWhitelisted(player.getName().getString())) {
                player.connection.disconnect(
                        net.minecraft.network.chat.Component.literal("Server is under maintenance!"));
            }
        }
    }
}
