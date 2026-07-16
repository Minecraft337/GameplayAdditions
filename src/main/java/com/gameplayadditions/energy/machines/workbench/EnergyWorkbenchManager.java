package com.gameplayadditions.energy.machines.workbench;

import com.gameplayadditions.energy.storage.battery.BatteryManager;
import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EnergyWorkbenchManager — управление энергопотреблением крафтера (Item Assembler).
 * <p>
 * Порт {@code com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager} из MC-Plugin.
 */
public class EnergyWorkbenchManager {

    private static final int BUFFER_MAX = 100;

    private static final Set<BlockPos> workbenches = ConcurrentHashMap.newKeySet();
    private static final Map<BlockPos, Integer> energyBuffer = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> playerWorkbench = new ConcurrentHashMap<>();

    public static void init() {
        workbenches.clear();
        energyBuffer.clear();
        playerWorkbench.clear();
        ConsoleLogger.info("[Workbench] Manager initialized.");
    }

    // =========================
    // REGISTRATION
    // =========================
    public static void add(BlockPos pos) {
        if (pos == null) return;
        workbenches.add(pos);
    }

    public static void remove(BlockPos pos) {
        if (pos == null) return;
        workbenches.remove(pos);
        energyBuffer.remove(pos);
    }

    public static boolean exists(BlockPos pos) {
        return pos != null && workbenches.contains(pos);
    }

    public static Set<BlockPos> getAll() { return workbenches; }

    // =========================
    // PLAYER TRACKING
    // =========================
    public static void setPlayerWorkbench(UUID playerUuid, BlockPos pos) {
        if (playerUuid == null || pos == null) return;
        playerWorkbench.put(playerUuid, pos);
    }

    public static BlockPos getPlayerWorkbench(UUID playerUuid) {
        return playerUuid != null ? playerWorkbench.get(playerUuid) : null;
    }

    public static void clearPlayerWorkbench(UUID playerUuid) {
        if (playerUuid != null) playerWorkbench.remove(playerUuid);
    }

    // =========================
    // BUFFER
    // =========================
    public static int getBufferEnergy(BlockPos pos) {
        return pos != null ? energyBuffer.getOrDefault(pos, 0) : 0;
    }

    public static boolean hasBufferEnergy(BlockPos pos, int amount) {
        return getBufferEnergy(pos) >= amount;
    }

    public static void consumeBufferEnergy(BlockPos pos, int amount) {
        if (pos == null) return;
        energyBuffer.computeIfPresent(pos, (k, v) -> v >= amount ? v - amount : v);
    }

    public static void addBufferEnergy(BlockPos pos, int amount) {
        if (pos == null || amount <= 0) return;
        energyBuffer.merge(pos, amount, (old, val) -> Math.min(old + val, BUFFER_MAX));
    }

    // =========================
    // CHARGE ALL BUFFERS (from cable network)
    // =========================
    public static void chargeAllBuffers(Level level) {
        if (level == null) return;
        for (BlockPos loc : workbenches) {
            try {
                int current = energyBuffer.getOrDefault(loc, 0);
                if (current >= BUFFER_MAX) continue;

                CableNode start = findAdjacentCableNode(level, loc);
                if (start == null) continue;

                int needed = BUFFER_MAX - current;
                int pulled = pullEnergyFromNetwork(start, needed);

                if (pulled > 0) {
                    energyBuffer.merge(loc, pulled, (old, val) -> Math.min(old + val, BUFFER_MAX));
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[Workbench] chargeAllBuffers error: " + e.getMessage());
            }
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

    public static void clearAll() {
        workbenches.clear();
        energyBuffer.clear();
        playerWorkbench.clear();
    }
}
