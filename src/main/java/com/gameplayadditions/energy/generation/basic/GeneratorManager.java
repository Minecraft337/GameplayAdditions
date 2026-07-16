package com.gameplayadditions.energy.generation.basic;

import com.gameplayadditions.energy.generation.reactor.ReactorConfig;
import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.energy.util.Materials;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GeneratorManager — управление сборкой и работой генераторов.
 * <p>
 * Порт {@code com.mcplugin.energy.generation.basic.GeneratorManager} из MC-Plugin.
 */
public class GeneratorManager {

    private static final Map<String, Set<BlockPos>> activeGenerators = new ConcurrentHashMap<>();

    // Fuel tracking
    private static final Map<BlockPos, Integer> fuelTimer = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Double> energyAccumulator = new ConcurrentHashMap<>();

    private static int totalEnergyPerFuel = 100;
    private static int burnDuration = 100;

    public static void init() {
        activeGenerators.clear();
        fuelTimer.clear();
        energyAccumulator.clear();
        ConsoleLogger.info("[Generator] Manager initialized.");
    }

    public static void configure(int energyPerFuel, int burnTicks) {
        totalEnergyPerFuel = energyPerFuel;
        burnDuration = burnTicks;
    }

    // =========================
    // STATE
    // =========================
    public static boolean isAssembled(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String worldKey = LocationUtil.worldKey(level);
        Set<BlockPos> generators = activeGenerators.get(worldKey);
        return generators != null && generators.contains(pos);
    }

    public static boolean assemble(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!GeneratorStructure.isValid(pos, level, false)) return false;
        if (!hasNearbyCable(level, pos)) return false;

        String worldKey = LocationUtil.worldKey(level);
        activeGenerators.computeIfAbsent(worldKey, k -> ConcurrentHashMap.newKeySet()).add(pos);
        return true;
    }

    public static boolean disassemble(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String worldKey = LocationUtil.worldKey(level);
        Set<BlockPos> generators = activeGenerators.get(worldKey);
        if (generators == null) return false;
        boolean removed = generators.remove(pos);
        if (removed) {
            fuelTimer.remove(pos);
            energyAccumulator.remove(pos);
        }
        return removed;
    }

    // =========================
    // CABLE NEARBY CHECK
    // =========================
    public static boolean hasNearbyCable(Level level, BlockPos pos) {
        for (BlockPos near : LocationUtil.getNeighbors(pos)) {
            if (CableNetwork.exists(level, near)) return true;
        }
        return false;
    }

    public static CableNode findConnectedNode(Level level, BlockPos pos) {
        for (BlockPos near : LocationUtil.getNeighbors(pos)) {
            CableNode node = CableNetwork.getNode(level, near);
            if (node != null && LocationUtil.isFullyConnected(pos, near)) {
                return node;
            }
        }
        return null;
    }

    // =========================
    // TASK (call every tick, or every 20 ticks)
    // =========================
    public static void tickGenerators() {
        for (Map.Entry<String, Set<BlockPos>> worldEntry : activeGenerators.entrySet()) {
            for (BlockPos pos : new HashSet<>(worldEntry.getValue())) {
                // World reference would need server - simplified tick
                // In full version, iterate over all loaded levels
            }
        }
    }

    // =========================
    // CLEANUP
    // =========================
    public static void clearAll() {
        activeGenerators.clear();
        fuelTimer.clear();
        energyAccumulator.clear();
    }
}
