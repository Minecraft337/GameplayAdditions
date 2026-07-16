package com.gameplayadditions.energy.storage.battery;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.energy.util.Materials;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BatteryManager — мультиблочная батарея (WAXED_COPPER_GRATE).
 * <p>
 * Порт {@code com.mcplugin.energy.storage.battery.BatteryManager} из MC-Plugin.
 * Использует SQLite persistence вместо Marker entity.
 */
public class BatteryManager {

    // ════════════════════════════════════════
    // РЕЖИМЫ БАТАРЕИ
    // ════════════════════════════════════════
    public enum BatteryMode {
        CHARGE, CHARGE_DISCHARGE, DISCHARGE
    }

    private static BatteryManager instance;
    private static final Map<String, Map<Long, BatteryCluster>> clustersByWorld = new ConcurrentHashMap<>();
    private static final Map<Integer, BatteryCluster> clustersById = new ConcurrentHashMap<>();
    private static int nextId = 1;

    // ════════════════════════════════════════
    // BATTERY CLUSTER
    // ════════════════════════════════════════
    public static class BatteryCluster {
        public int id;
        public String worldKey;
        public Level level;
        public Set<Long> blockKeys = new HashSet<>();
        public BlockPos center;
        public int capacity;
        public BatteryMode mode = BatteryMode.CHARGE_DISCHARGE;

        private long sumX, sumY, sumZ;

        void addBlock(long key) {
            if (blockKeys.add(key)) {
                sumX += LocationUtil.getX(key);
                sumY += LocationUtil.getY(key);
                sumZ += LocationUtil.getZ(key);
                capacity = blockKeys.size() * 1000;
                updateCenterFromSums();
            }
        }

        void removeBlock(long key) {
            if (blockKeys.remove(key)) {
                sumX -= LocationUtil.getX(key);
                sumY -= LocationUtil.getY(key);
                sumZ -= LocationUtil.getZ(key);
                if (!blockKeys.isEmpty()) {
                    capacity = blockKeys.size() * 1000;
                    updateCenterFromSums();
                } else {
                    center = null;
                    capacity = 0;
                }
            }
        }

        void recalculateCenter() {
            if (blockKeys.isEmpty()) return;
            sumX = 0; sumY = 0; sumZ = 0;
            for (long key : blockKeys) {
                sumX += LocationUtil.getX(key);
                sumY += LocationUtil.getY(key);
                sumZ += LocationUtil.getZ(key);
            }
            updateCenterFromSums();
        }

        private void updateCenterFromSums() {
            int size = blockKeys.size();
            if (size == 0) { center = null; capacity = 0; return; }
            center = new BlockPos(
                    (int) Math.round((double) sumX / size),
                    (int) Math.round((double) sumY / size),
                    (int) Math.round((double) sumZ / size));
            capacity = size * 1000;
        }

        boolean contains(BlockPos pos) { return blockKeys.contains(LocationUtil.toKey(pos)); }

        public boolean canCharge() {
            return mode == BatteryMode.CHARGE || mode == BatteryMode.CHARGE_DISCHARGE;
        }

        public boolean canDischarge() {
            return mode == BatteryMode.DISCHARGE || mode == BatteryMode.CHARGE_DISCHARGE;
        }

        void cycleMode() {
            mode = switch (mode) {
                case CHARGE -> BatteryMode.CHARGE_DISCHARGE;
                case CHARGE_DISCHARGE -> BatteryMode.DISCHARGE;
                case DISCHARGE -> BatteryMode.CHARGE;
            };
        }
    }

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init() {
        instance = new BatteryManager();
        loadAll();
        ConsoleLogger.info("[Battery] Manager initialized with " + clustersById.size() + " clusters.");
    }

    public static BatteryManager getInstance() { return instance; }

    // ════════════════════════════════════════
    // FLOOD-FILL
    // ════════════════════════════════════════
    private static final int[][] DIR = {
        {1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}
    };

    private static Set<Long> floodFill(Level level, BlockPos start) {
        Set<Long> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(LocationUtil.toKey(start));
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (BlockPos neighbor : LocationUtil.getNeighbors(pos)) {
                long nk = LocationUtil.toKey(neighbor);
                if (visited.contains(nk)) continue;
                if (level.getBlockState(neighbor).is(Materials.WAXED_COPPER_GRATE)) {
                    visited.add(nk);
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    // ════════════════════════════════════════
    // ASSEMBLE / DISASSEMBLE
    // ════════════════════════════════════════
    public static void assemble(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        long key = LocationUtil.toKey(pos);
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, BatteryCluster> worldClusters = clustersByWorld.computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>());
        if (worldClusters.containsKey(key)) return;

        Set<Long> connected = floodFill(level, pos);
        if (connected.isEmpty()) return;

        BatteryCluster cluster = new BatteryCluster();
        cluster.id = nextId++;
        cluster.worldKey = worldKey;
        cluster.level = level;
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();

        for (long bk : connected) {
            worldClusters.put(bk, cluster);
            CableNetwork.ensureNode(level, LocationUtil.toPosition(bk, level), NodeType.BATTERY);
        }
        clustersById.put(cluster.id, cluster);
        saveCluster(cluster);
    }

    public static void disassemble(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, BatteryCluster> worldClusters = clustersByWorld.get(worldKey);
        if (worldClusters == null) return;

        BatteryCluster cluster = worldClusters.get(LocationUtil.toKey(pos));
        if (cluster == null) return;

        for (long bk : cluster.blockKeys) {
            worldClusters.remove(bk);
            CableNetwork.removeNode(level, LocationUtil.toPosition(bk, level));
        }
        clustersById.remove(cluster.id);
        deleteCluster(cluster.id);
    }

    // ════════════════════════════════════════
    // BLOCK PLACED / BROKEN (hot expand/shrink)
    // ════════════════════════════════════════
    public static void onBlockPlaced(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        long key = LocationUtil.toKey(pos);
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, BatteryCluster> worldClusters = clustersByWorld.get(worldKey);
        if (worldClusters == null || worldClusters.containsKey(key)) return;

        int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();
        long[] neighborKeys = {
            LocationUtil.toKey(bx+1,by,bz), LocationUtil.toKey(bx-1,by,bz),
            LocationUtil.toKey(bx,by+1,bz), LocationUtil.toKey(bx,by-1,bz),
            LocationUtil.toKey(bx,by,bz+1), LocationUtil.toKey(bx,by,bz-1)
        };

        Set<BatteryCluster> adj = new LinkedHashSet<>();
        for (long nk : neighborKeys) {
            BatteryCluster c = worldClusters.get(nk);
            if (c != null) adj.add(c);
        }
        if (adj.isEmpty()) return;

        if (adj.size() == 1) {
            BatteryCluster c = adj.iterator().next();
            c.addBlock(key);
            worldClusters.put(key, c);
            CableNetwork.ensureNode(level, pos, NodeType.BATTERY);
            saveCluster(c);
        } else {
            Iterator<BatteryCluster> it = adj.iterator();
            BatteryCluster primary = it.next();
            while (it.hasNext()) {
                BatteryCluster other = it.next();
                for (long bk : other.blockKeys) {
                    worldClusters.put(bk, primary);
                    primary.addBlock(bk);
                }
                clustersById.remove(other.id);
                deleteCluster(other.id);
            }
            primary.addBlock(key);
            worldClusters.put(key, primary);
            CableNetwork.ensureNode(level, pos, NodeType.BATTERY);
            saveCluster(primary);
        }
    }

    public static void onBlockBroken(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, BatteryCluster> worldClusters = clustersByWorld.get(worldKey);
        if (worldClusters == null) return;

        BatteryCluster cluster = worldClusters.get(LocationUtil.toKey(pos));
        if (cluster == null) return;

        for (long bk : cluster.blockKeys) {
            worldClusters.remove(bk);
            CableNetwork.removeNode(level, LocationUtil.toPosition(bk, level));
        }
        clustersById.remove(cluster.id);
        cluster.blockKeys.clear();
        deleteCluster(cluster.id);
    }

    // ════════════════════════════════════════
    // QUERIES
    // ════════════════════════════════════════
    public static boolean isActive(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, BatteryCluster> worldClusters = clustersByWorld.get(worldKey);
        return worldClusters != null && worldClusters.containsKey(LocationUtil.toKey(pos));
    }

    public static BatteryCluster getCluster(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, BatteryCluster> worldClusters = clustersByWorld.get(worldKey);
        return worldClusters != null ? worldClusters.get(LocationUtil.toKey(pos)) : null;
    }

    public static BatteryMode getMode(Level level, BlockPos pos) {
        BatteryCluster c = getCluster(level, pos);
        return c != null ? c.mode : BatteryMode.CHARGE_DISCHARGE;
    }

    public static double getChargePercentage(BatteryCluster cluster) {
        if (cluster == null || cluster.capacity <= 0) return 0.0;
        int totalEnergy = 0;
        for (long key : cluster.blockKeys) {
            BlockPos bp = LocationUtil.toPosition(key, cluster.level);
            CableNode node = CableNetwork.getNode(cluster.level, bp);
            if (node != null) totalEnergy += node.getEnergy();
        }
        return Math.min(100.0, (double) totalEnergy / cluster.capacity * 100.0);
    }

    // ════════════════════════════════════════
    // PERSISTENCE
    // ════════════════════════════════════════
    private static void saveCluster(BatteryCluster cluster) {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT OR REPLACE INTO batteries (id, world, center_x, center_y, center_z, block_count) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, cluster.id);
                ps.setString(2, cluster.worldKey);
                ps.setInt(3, cluster.center.getX());
                ps.setInt(4, cluster.center.getY());
                ps.setInt(5, cluster.center.getZ());
                ps.setInt(6, cluster.blockKeys.size());
                ps.executeUpdate();
            }
            saveClusterBlocks(cluster);
        } catch (Exception e) {
            ConsoleLogger.warn("[Battery] Save error: " + e.getMessage());
        }
    }

    private static void saveClusterBlocks(BatteryCluster cluster) {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM battery_blocks WHERE battery_id = ?")) {
                ps.setInt(1, cluster.id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO battery_blocks (battery_id, x, y, z) VALUES (?, ?, ?, ?)")) {
                for (long key : cluster.blockKeys) {
                    ps.setInt(1, cluster.id);
                    ps.setInt(2, LocationUtil.getX(key));
                    ps.setInt(3, LocationUtil.getY(key));
                    ps.setInt(4, LocationUtil.getZ(key));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Battery] Save blocks error: " + e.getMessage());
        }
    }

    private static void deleteCluster(int id) {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM batteries WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM battery_blocks WHERE battery_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Battery] Delete error: " + e.getMessage());
        }
    }

    private static void loadAll() {
        clustersByWorld.clear();
        clustersById.clear();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM batteries");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String worldKey = rs.getString("world");
                int cx = rs.getInt("center_x");
                int cy = rs.getInt("center_y");
                int cz = rs.getInt("center_z");

                BatteryCluster cluster = new BatteryCluster();
                cluster.id = id;
                cluster.worldKey = worldKey;
                cluster.level = null;
                loadClusterBlocks(cluster, worldKey);
                cluster.recalculateCenter();
                clustersById.put(cluster.id, cluster);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Battery] Load error: " + e.getMessage());
        }
    }

    private static void loadClusterBlocks(BatteryCluster cluster, String worldKey) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM battery_blocks WHERE battery_id = ?")) {
            ps.setInt(1, cluster.id);
            ResultSet rs = ps.executeQuery();
            Map<Long, BatteryCluster> worldClusters = clustersByWorld.computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>());
            while (rs.next()) {
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                long key = LocationUtil.toKey(x, y, z);
                cluster.blockKeys.add(key);
                worldClusters.put(key, cluster);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Battery] Load blocks error: " + e.getMessage());
        }
    }
}
