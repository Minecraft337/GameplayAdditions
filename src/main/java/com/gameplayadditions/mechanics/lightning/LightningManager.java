package com.gameplayadditions.mechanics.lightning;

import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LightningManager — управление структурой молний.
 * <p>
 * Порт {@code com.mcplugin.mechanics.environment.lightning.LightningManager} из MC-Plugin.
 */
public class LightningManager {

    private static LightningManager instance;
    private static final Map<BlockPos, Boolean> activeStructures = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> cookingCooldowns = new ConcurrentHashMap<>();
    private static final long COOKING_COOLDOWN_MS = 1000L;
    private static final int ENERGY_COST = 100;

    public static void init() {
        instance = new LightningManager();
        ConsoleLogger.info("[Lightning] Manager initialized.");
    }

    public static LightningManager getInstance() { return instance; }

    public static boolean isActive(BlockPos center) {
        return activeStructures.containsKey(center);
    }

    // =========================
    // ASSEMBLE / DISASSEMBLE
    // =========================
    public static void assemble(BlockPos center, Level level) {
        if (center == null || level == null) return;
        if (activeStructures.containsKey(center)) return;

        if (!LightningStructure.isValid(center, level, false)) return;

        activeStructures.put(center, true);

        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                    30, 0.5, 0.5, 0.5, 0);
            sl.playSound(null, center, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    public static void disassemble(BlockPos center) {
        if (center == null) return;
        activeStructures.remove(center);
        cookingCooldowns.remove(center);
    }

    // =========================
    // TICK — периодическое приготовление предметов на громоотводе
    // =========================
    public static void tick() {
        for (Map.Entry<BlockPos, Boolean> entry : new HashMap<>(activeStructures).entrySet()) {
            if (!entry.getValue()) continue;
            BlockPos center = entry.getKey();

            // Find level - simplified, need server reference
            long now = System.currentTimeMillis();
            Long lastCook = cookingCooldowns.get(center);
            if (lastCook != null && (now - lastCook) < COOKING_COOLDOWN_MS) continue;

            // Energy check
            BlockPos energyPos = LightningStructure.getEnergyInputPos(center);
            if (!hasEnergyForOperation(energyPos)) continue;

            cookingCooldowns.put(center, now);
        }
    }

    private static boolean hasEnergyForOperation(BlockPos energyPos) {
        // Simplifed - needs level reference
        return true;
    }

    public static void clearAll() {
        activeStructures.clear();
        cookingCooldowns.clear();
    }
}
