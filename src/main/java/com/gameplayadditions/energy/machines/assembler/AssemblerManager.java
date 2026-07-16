package com.gameplayadditions.energy.machines.assembler;

import com.gameplayadditions.energy.machines.workbench.EnergyWorkbenchManager;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AssemblerManager — управление сборкой/разборкой авто-сборщиков.
 * <p>
 * Порт {@code com.mcplugin.energy.machines.assembler.AssemblerManager} из MC-Plugin.
 */
public class AssemblerManager {

    private static AssemblerManager instance;
    private static final Map<String, Set<BlockPos>> activeAssemblers = new ConcurrentHashMap<>();

    public static void init() {
        instance = new AssemblerManager();
        ConsoleLogger.info("[Assembler] Manager initialized.");
    }

    public static AssemblerManager getInstance() { return instance; }

    public static boolean isAssembled(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String worldKey = LocationUtil.worldKey(level);
        Set<BlockPos> assemblers = activeAssemblers.get(worldKey);
        return assemblers != null && assemblers.contains(pos);
    }

    public static boolean assemble(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!AssemblerStructure.isValid(pos, level, false)) return false;

        String worldKey = LocationUtil.worldKey(level);
        activeAssemblers.computeIfAbsent(worldKey, k -> ConcurrentHashMap.newKeySet()).add(pos);

        // Register in workbench manager for GUI
        EnergyWorkbenchManager.add(pos);

        // Visual effects
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    30, 0.5, 0.5, 0.5, 0);
            sl.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 1.0f, 1.0f);
            sl.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1.0f, 0.8f);
        }

        return true;
    }

    public static boolean disassemble(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String worldKey = LocationUtil.worldKey(level);
        Set<BlockPos> assemblers = activeAssemblers.get(worldKey);
        if (assemblers == null) return false;

        boolean removed = assemblers.remove(pos);
        if (removed) {
            EnergyWorkbenchManager.remove(pos);

            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    20, 0.3, 0.3, 0.3, 0);
                sl.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0f, 1.0f);
            }
        }
        return removed;
    }

    public static Collection<BlockPos> getActiveAssemblers(String worldKey) {
        Set<BlockPos> assemblers = activeAssemblers.get(worldKey);
        return assemblers != null ? Collections.unmodifiableSet(assemblers) : Collections.emptySet();
    }

    public static void clearAll() {
        activeAssemblers.clear();
    }

    public static void onBlockPlaced(Level level, BlockPos pos) {
        assemble(level, pos);
    }

    public static void onBlockBroken(Level level, BlockPos pos) {
        disassemble(level, pos);
    }
}
