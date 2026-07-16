package com.gameplayadditions.energy.consumption.light;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.energy.util.Materials;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.CopperBulbBlock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LightManager — мультиблочная лампочка (REDSTONE_LAMP/WAXED_COPPER_BULB).
 * <p>
 * Порт {@code com.mcplugin.energy.consumption.light.LightManager} из MC-Plugin.
 * Упрощённая версия: SQLite persistence вместо Marker entity.
 */
public class LightManager {

    private static LightManager instance;
    private static final Map<String, Map<Long, LightCluster>> clustersByWorld = new ConcurrentHashMap<>();
    private static int nextId = 1;

    public static class LightCluster {
        public int id;
        public String worldKey;
        public Level level;
        public Set<Long> blockKeys = new HashSet<>();
        public BlockPos center;
        public int power;
        public int buffer;
        public boolean lit;

        int getBufferCapacity() { return power * 2; }
        boolean isBufferFull() { return buffer >= getBufferCapacity(); }

        boolean isAnyBlockPowered() {
            for (long key : blockKeys) {
                BlockPos p = LocationUtil.toPosition(key, level);
                if (level != null && level.hasNeighborSignal(p)) return true;
            }
            return false;
        }

        private long sumX, sumY, sumZ;

        void addBlock(long key) {
            if (blockKeys.add(key)) {
                sumX += LocationUtil.getX(key);
                sumY += LocationUtil.getY(key);
                sumZ += LocationUtil.getZ(key);
                power = blockKeys.size();
                updateCenterFromSums();
            }
        }

        void removeBlock(long key) {
            if (blockKeys.remove(key)) {
                sumX -= LocationUtil.getX(key);
                sumY -= LocationUtil.getY(key);
                sumZ -= LocationUtil.getZ(key);
                if (!blockKeys.isEmpty()) {
                    power = blockKeys.size();
                    updateCenterFromSums();
                } else {
                    center = null;
                    power = 0;
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
            if (size == 0) { center = null; power = 0; return; }
            center = new BlockPos(
                    (int) Math.round((double) sumX / size),
                    (int) Math.round((double) sumY / size),
                    (int) Math.round((double) sumZ / size));
            power = size;
        }

        boolean contains(BlockPos pos) { return blockKeys.contains(LocationUtil.toKey(pos)); }
    }

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new LightManager();
        loadAll();
        ConsoleLogger.info("[Light] Manager initialized with " + countClusters() + " clusters.");
    }

    private static int countClusters() {
        int count = 0;
        for (Map<Long, LightCluster> map : clustersByWorld.values()) {
            count += new HashSet<>(map.values()).size();
        }
        return count;
    }

    public static LightManager getInstance() { return instance; }

    // =========================
    // ASSEMBLE / DISASSEMBLE
    // =========================
    public static void assemble(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        long key = LocationUtil.toKey(pos);
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, LightCluster> worldClusters = clustersByWorld.computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>());
        if (worldClusters.containsKey(key)) return;

        Set<Long> connected = floodFill(level, pos);
        if (connected.isEmpty()) return;

        LightCluster cluster = new LightCluster();
        cluster.id = nextId++;
        cluster.worldKey = worldKey;
        cluster.level = level;
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();
        cluster.lit = false;
        cluster.buffer = 0;

        for (long bk : connected) worldClusters.put(bk, cluster);
        saveCluster(cluster);
    }

    public static void disassemble(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, LightCluster> worldClusters = clustersByWorld.get(worldKey);
        if (worldClusters == null) return;

        LightCluster cluster = worldClusters.get(LocationUtil.toKey(pos));
        if (cluster == null) return;

        if (cluster.lit) setBlocksLit(cluster, false);
        for (long bk : cluster.blockKeys) worldClusters.remove(bk);
        deleteCluster(cluster.id);
    }

    // =========================
    // FLOOD-FILL
    // =========================
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
                BlockState state = level.getBlockState(neighbor);
                if (state.is(Blocks.REDSTONE_LAMP) || state.is(Blocks.WAXED_COPPER_BULB)) {
                    visited.add(nk);
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    // =========================
    // TICK
    // =========================
    public static void tick() {
        for (Map<Long, LightCluster> worldClusters : clustersByWorld.values()) {
            Set<Integer> toRemove = new HashSet<>();
            Set<LightCluster> uniqueClusters = new HashSet<>(worldClusters.values());

            for (LightCluster cluster : uniqueClusters) {
                if (cluster.level == null || cluster.blockKeys.isEmpty()) continue;

                // Phantom check
                long firstKey = cluster.blockKeys.iterator().next();
                BlockPos firstPos = LocationUtil.toPosition(firstKey, cluster.level);
                BlockState firstState = cluster.level.getBlockState(firstPos);
                if (!firstState.is(Blocks.REDSTONE_LAMP) && !firstState.is(Blocks.WAXED_COPPER_BULB)) {
                    toRemove.add(cluster.id);
                    continue;
                }

                boolean hasRedstone = cluster.isAnyBlockPowered();

                if (hasRedstone && !cluster.isBufferFull()) {
                    chargeClusterBuffer(cluster);
                }

                boolean shouldBeLit = hasRedstone && cluster.isBufferFull();

                if (shouldBeLit != cluster.lit) {
                    cluster.lit = shouldBeLit;
                    setBlocksLit(cluster, shouldBeLit);
                }

                if (cluster.lit) {
                    cluster.buffer = Math.max(0, cluster.buffer - cluster.power);
                }
            }

            for (int id : toRemove) {
                // Remove phantom clusters (in-memory only, don't delete from DB)
                worldClusters.entrySet().removeIf(e -> e.getValue().id == id);
            }
        }
    }

    private static void chargeClusterBuffer(LightCluster cluster) {
        if (cluster == null || cluster.buffer >= cluster.getBufferCapacity()) return;

        for (long key : cluster.blockKeys) {
            BlockPos blockPos = LocationUtil.toPosition(key, cluster.level);
            CableNode node = findAdjacentCableNode(cluster.level, blockPos);
            if (node == null) continue;

            int needed = cluster.getBufferCapacity() - cluster.buffer;
            int pulled = pullEnergyFromNetwork(node, needed);
            if (pulled > 0) {
                cluster.buffer += pulled;
            }
            break;
        }
    }

    private static CableNode findAdjacentCableNode(Level level, BlockPos pos) {
        for (BlockPos near : LocationUtil.getNeighbors(pos)) {
            CableNode node = CableNetwork.getNode(level, near);
            if (node != null) return node;
        }
        return null;
    }

    private static int pullEnergyFromNetwork(CableNode start, int amount) {
        if (start == null || amount <= 0) return 0;

        String worldKey = LocationUtil.worldKey(start.getLevel());
        Set<Long> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getKey());

        int remaining = amount;
        while (!queue.isEmpty() && remaining > 0) {
            CableNode node = queue.poll();
            if (node == null) continue;

            int energy = node.getEnergy();
            if (energy > 0 && node.getType() == NodeType.BATTERY) {
                int take = Math.min(energy, remaining);
                node.setEnergy(energy - take);
                remaining -= take;
            }

            for (long connKey : node.getConnectionKeys()) {
                if (visited.contains(connKey)) continue;
                CableNode next = CableNetwork.getNodeByKey(worldKey, connKey);
                if (next == null) continue;
                visited.add(connKey);
                queue.add(next);
            }
        }
        return amount - remaining;
    }

    // =========================
    // SET BLOCKS LIT
    // =========================
    private static void setBlocksLit(LightCluster cluster, boolean lit) {
        for (long key : cluster.blockKeys) {
            BlockPos pos = LocationUtil.toPosition(key, cluster.level);
            BlockState state = cluster.level.getBlockState(pos);
            if (state.is(Blocks.WAXED_COPPER_BULB)) {
                cluster.level.setBlock(pos, state.setValue(CopperBulbBlock.LIT, lit), 3);
            } else if (state.is(Blocks.REDSTONE_LAMP)) {
                cluster.level.setBlock(pos, state.setValue(RedstoneLampBlock.LIT, lit), 3);
            }
        }
    }

    // =========================
    // QUERIES
    // =========================
    public static boolean isActive(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, LightCluster> worldClusters = clustersByWorld.get(worldKey);
        return worldClusters != null && worldClusters.containsKey(LocationUtil.toKey(pos));
    }

    public static boolean isLit(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String worldKey = LocationUtil.worldKey(level);
        Map<Long, LightCluster> worldClusters = clustersByWorld.get(worldKey);
        if (worldClusters == null) return false;
        LightCluster c = worldClusters.get(LocationUtil.toKey(pos));
        return c != null && c.lit;
    }

    // =========================
    // PERSISTENCE
    // =========================
    private static void saveCluster(LightCluster cluster) {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT OR REPLACE INTO lights (id, world, center_x, center_y, center_z, block_count, lit) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, cluster.id);
                ps.setString(2, cluster.worldKey);
                ps.setInt(3, cluster.center.getX());
                ps.setInt(4, cluster.center.getY());
                ps.setInt(5, cluster.center.getZ());
                ps.setInt(6, cluster.blockKeys.size());
                ps.setInt(7, cluster.lit ? 1 : 0);
                ps.executeUpdate();
            }
            saveClusterBlocks(cluster);
        } catch (Exception e) {
            ConsoleLogger.warn("[Light] Save error: " + e.getMessage());
        }
    }

    private static void saveClusterBlocks(LightCluster cluster) {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM light_blocks WHERE light_id = ?")) {
                ps.setInt(1, cluster.id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO light_blocks (light_id, x, y, z) VALUES (?, ?, ?, ?)")) {
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
            ConsoleLogger.warn("[Light] Save blocks error: " + e.getMessage());
        }
    }

    private static void deleteCluster(int id) {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM lights WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM light_blocks WHERE light_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Light] Delete error: " + e.getMessage());
        }
    }

    private static void loadAll() {
        clustersByWorld.clear();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM lights");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String worldKey = rs.getString("world");
                int cx = rs.getInt("center_x");
                int cy = rs.getInt("center_y");
                int cz = rs.getInt("center_z");
                int blockCount = rs.getInt("block_count");
                boolean lit = rs.getInt("lit") == 1;

                LightCluster cluster = new LightCluster();
                cluster.id = id;
                cluster.worldKey = worldKey;
                cluster.level = null; // Will be resolved later
                cluster.lit = lit;
                cluster.buffer = 0;

                Map<Long, LightCluster> worldClusters = clustersByWorld.computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>());
                // Load blocks separately
                loadClusterBlocks(cluster);
                cluster.recalculateCenter();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Light] Load error: " + e.getMessage());
        }
    }

    private static void loadClusterBlocks(LightCluster cluster) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM light_blocks WHERE light_id = ?")) {
            ps.setInt(1, cluster.id);
            ResultSet rs = ps.executeQuery();
            Map<Long, LightCluster> worldClusters = clustersByWorld.get(cluster.worldKey);
            if (worldClusters == null) {
                worldClusters = new ConcurrentHashMap<>();
                clustersByWorld.put(cluster.worldKey, worldClusters);
            }
            while (rs.next()) {
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                long key = LocationUtil.toKey(x, y, z);
                cluster.blockKeys.add(key);
                worldClusters.put(key, cluster);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Light] Load blocks error: " + e.getMessage());
        }
    }
}
