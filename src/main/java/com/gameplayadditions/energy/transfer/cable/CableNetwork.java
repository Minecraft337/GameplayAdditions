package com.gameplayadditions.energy.transfer.cable;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * CableNetwork — управление кабельной сетью (in-memory + SQLite persistence).
 * <p>
 * Порт {@code com.mcplugin.energy.transfer.cable.CableNetwork} из MC-Plugin.
 * Адаптирован для NeoForge: без StructureMarker, использует прямую in-memory карту
 * с сохранением в SQLite.
 */
public class CableNetwork {

    // worldKey -> (block key -> CableNode)
    private static final Map<String, Map<Long, CableNode>> nodesByWorld = new ConcurrentHashMap<>();

    // worldKey -> Set of block keys (flowing visual)
    private static final Map<String, Set<Long>> flowingByWorld = new ConcurrentHashMap<>();

    private CableNetwork() {}

    // =========================
    // INIT
    // =========================
    public static void init() {
        nodesByWorld.clear();
        flowingByWorld.clear();
        loadAll();
        ConsoleLogger.info("[CableNetwork] Initialized with " + countNodes() + " nodes.");
    }

    private static int countNodes() {
        int count = 0;
        for (Map<Long, CableNode> worldNodes : nodesByWorld.values()) {
            count += worldNodes.size();
        }
        return count;
    }

    // =========================
    // ADD / ENSURE / REMOVE NODE
    // =========================
    public static void addNode(Level level, BlockPos pos) {
        if (level == null || pos == null) return;

        long key = LocationUtil.toKey(pos);
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, CableNode> worldNodes = nodesByWorld.computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>());
        if (worldNodes.containsKey(key)) return;

        CableNode node = new CableNode(level, pos);
        worldNodes.put(key, node);

        // Auto-connect to neighbors
        autoConnectNode(worldKey, node);

        saveNode(node);
    }

    public static void ensureNode(Level level, BlockPos pos, NodeType type) {
        if (level == null || pos == null) return;

        long key = LocationUtil.toKey(pos);
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, CableNode> worldNodes = nodesByWorld.computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>());
        if (worldNodes.containsKey(key)) return;

        CableNode node = new CableNode(level, pos);
        if (type != null) node.setType(type);
        worldNodes.put(key, node);

        autoConnectNode(worldKey, node);
        saveNode(node);
    }

    public static void removeNode(Level level, BlockPos pos) {
        if (level == null || pos == null) return;

        long key = LocationUtil.toKey(pos);
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldKey);
        if (worldNodes == null) return;

        CableNode node = worldNodes.remove(key);
        if (node != null) {
            for (long connKey : node.getConnectionKeys()) {
                CableNode other = worldNodes.get(connKey);
                if (other != null) other.disconnectKey(key);
            }
        }

        deleteNode(level, pos);
    }

    // =========================
    // AUTO-CONNECT
    // =========================
    private static void autoConnectNode(String worldKey, CableNode node) {
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldKey);
        if (worldNodes == null) return;

        for (long nearKey : LocationUtil.getNeighborKeys(node.getKey())) {
            CableNode neighbor = worldNodes.get(nearKey);
            if (neighbor == null) continue;
            if (node.getType() == NodeType.BATTERY && neighbor.getType() == NodeType.BATTERY) continue;
            if (!LocationUtil.isFullyConnected(node.getKey(), nearKey)) continue;
            node.connectKey(nearKey);
            neighbor.connectKey(node.getKey());
        }
    }

    // =========================
    // GET / EXISTS
    // =========================
    public static void forEachNode(Consumer<CableNode> action) {
        for (Map<Long, CableNode> worldNodes : nodesByWorld.values()) {
            for (CableNode node : worldNodes.values()) {
                action.accept(node);
            }
        }
    }

    public static CableNode getNode(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;

        long key = LocationUtil.toKey(pos);
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldKey);
        return worldNodes != null ? worldNodes.get(key) : null;
    }

    public static CableNode getNodeByKey(String worldKey, long key) {
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldKey);
        return worldNodes != null ? worldNodes.get(key) : null;
    }

    public static boolean exists(Level level, BlockPos pos) {
        return getNode(level, pos) != null;
    }

    public static Collection<CableNode> getAllNodes() {
        List<CableNode> all = new ArrayList<>();
        for (Map<Long, CableNode> worldNodes : nodesByWorld.values()) {
            all.addAll(worldNodes.values());
        }
        return all;
    }

    public static Collection<CableNode> getWorldNodes(String worldKey) {
        Map<Long, CableNode> worldNodes = nodesByWorld.get(worldKey);
        return worldNodes != null ? worldNodes.values() : Collections.emptyList();
    }

    // =========================
    // FLOWING TRACKING
    // =========================
    public static void markFlowing(String worldKey, long key) {
        if (worldKey == null || key == 0) return;
        flowingByWorld.computeIfAbsent(worldKey, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public static boolean isFlowing(String worldKey, long key) {
        if (worldKey == null || key == 0) return false;
        Set<Long> flowing = flowingByWorld.get(worldKey);
        return flowing != null && flowing.contains(key);
    }

    public static void clearFlowing() {
        flowingByWorld.clear();
    }

    // =========================
    // SQLITE PERSISTENCE
    // =========================
    public static void saveNode(CableNode node) {
        if (node == null) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "INSERT OR REPLACE INTO cables (world, x, y, z, energy, type) VALUES (?, ?, ?, ?, ?, ?)")) {

            ps.setString(1, LocationUtil.worldKey(node.getLevel()));
            ps.setInt(2, node.getBlockX());
            ps.setInt(3, node.getBlockY());
            ps.setInt(4, node.getBlockZ());
            ps.setInt(5, node.getEnergy());
            ps.setString(6, node.getType().name());
            ps.executeUpdate();

            // Save connections
            saveConnections(node);
        } catch (Exception e) {
            ConsoleLogger.warn("[CableNetwork] Save error: " + e.getMessage());
        }
    }

    private static void saveConnections(CableNode node) {
        try (Connection con = DatabaseManager.getConnection()) {
            // Delete existing connections for this node
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM cable_connections WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, LocationUtil.worldKey(node.getLevel()));
                ps.setInt(2, node.getBlockX());
                ps.setInt(3, node.getBlockY());
                ps.setInt(4, node.getBlockZ());
                ps.executeUpdate();
            }

            // Insert current connections
            String worldKey = LocationUtil.worldKey(node.getLevel());
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO cable_connections (world, x, y, z, to_world, to_x, to_y, to_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (long connKey : node.getConnectionKeys()) {
                    ps.setString(1, worldKey);
                    ps.setInt(2, node.getBlockX());
                    ps.setInt(3, node.getBlockY());
                    ps.setInt(4, node.getBlockZ());
                    ps.setString(5, worldKey);
                    ps.setInt(6, LocationUtil.getX(connKey));
                    ps.setInt(7, LocationUtil.getY(connKey));
                    ps.setInt(8, LocationUtil.getZ(connKey));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[CableNetwork] Save connections error: " + e.getMessage());
        }
    }

    public static void deleteNode(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM cables WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, LocationUtil.worldKey(level));
                ps.setInt(2, pos.getX());
                ps.setInt(3, pos.getY());
                ps.setInt(4, pos.getZ());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM cable_connections WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, LocationUtil.worldKey(level));
                ps.setInt(2, pos.getX());
                ps.setInt(3, pos.getY());
                ps.setInt(4, pos.getZ());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[CableNetwork] Delete error: " + e.getMessage());
        }
    }

    public static void loadAll() {
        nodesByWorld.clear();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM cables");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String worldKey = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                int energy = rs.getInt("energy");
                String typeStr = rs.getString("type");

                // We can't get Level reference here without server access,
                // so we store the node with null level and resolve later
                // For now, store by worldKey
                long key = LocationUtil.toKey(x, y, z);
                NodeType type = NodeType.CABLE;
                try { type = NodeType.valueOf(typeStr); } catch (Exception ignored) {}

                Map<Long, CableNode> worldNodes = nodesByWorld.computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>());

                // Create basic node - level will be resolved lazily
                CableNode node = new CableNode(null, new BlockPos(x, y, z));
                node.setEnergy(energy);
                node.setMaxEnergy(type == NodeType.BATTERY ? 100000 : type == NodeType.GENERATOR ? 60000 : 0);
                node.setType(type);
                worldNodes.put(key, node);
            }

            // Load connections
            loadConnections();
        } catch (Exception e) {
            ConsoleLogger.warn("[CableNetwork] Load error: " + e.getMessage());
        }
    }

    private static void loadConnections() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM cable_connections");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String worldKey = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                int toX = rs.getInt("to_x");
                int toY = rs.getInt("to_y");
                int toZ = rs.getInt("to_z");

                long key = LocationUtil.toKey(x, y, z);
                long toKey = LocationUtil.toKey(toX, toY, toZ);

                Map<Long, CableNode> worldNodes = nodesByWorld.get(worldKey);
                if (worldNodes == null) continue;

                CableNode node = worldNodes.get(key);
                CableNode neighbor = worldNodes.get(toKey);
                if (node != null && neighbor != null) {
                    // Directly add connection without full connection check (already validated when saved)
                    node.addConnectionKey(toKey);
                    neighbor.addConnectionKey(key);
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[CableNetwork] Load connections error: " + e.getMessage());
        }
    }
}
