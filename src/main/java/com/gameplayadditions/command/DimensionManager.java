package com.gameplayadditions.command;

import com.gameplayadditions.database.DatabaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class DimensionManager {

    public static void saveReturnLocation(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        UUID uuid = player.getUUID();
        ServerLevel level = player.serverLevel();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO dimension_returns
                (uuid, world, x, y, z, has_return)
                VALUES (?, ?, ?, ?, ?, 1)
             """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, level.dimension().location().toString());
            ps.setDouble(3, pos.getX());
            ps.setDouble(4, pos.getY());
            ps.setDouble(5, pos.getZ());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean hasReturnLocation(ServerPlayer player) {
        UUID uuid = player.getUUID();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                SELECT has_return FROM dimension_returns WHERE uuid = ?
             """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("has_return") == 1;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static ServerLevel getReturnLevel(ServerPlayer player, MinecraftServer server) {
        UUID uuid = player.getUUID();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                SELECT world, x, y, z FROM dimension_returns
                WHERE uuid = ? AND has_return = 1
             """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    // Find the level by dimension location string
                    for (ServerLevel level : server.getAllLevels()) {
                        if (level.dimension().location().toString().equals(worldName)) {
                            return level;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void removeReturnLocation(ServerPlayer player) {
        UUID uuid = player.getUUID();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                DELETE FROM dimension_returns WHERE uuid = ?
             """)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
