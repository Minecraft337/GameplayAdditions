package com.gameplayadditions.energy.machines.furnace;

import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.energy.util.Materials;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ElectricFurnaceManager — электрическая печь.
 * <p>
 * Порт {@code com.mcplugin.energy.machines.furnace.ElectricFurnaceManager} из MC-Plugin.
 * Потребляет 100 энергии из кабельной сети, чтобы моментально переплавлять предметы
 * на верхней грани BLAST_FURNACE с помощью молнии.
 */
public class ElectricFurnaceManager {

    private static ElectricFurnaceManager instance;

    private static final Map<BlockPos, Long> cookingCooldowns = new ConcurrentHashMap<>();
    private static long cooldownMs = 1000L;
    private static int energyCost = 100;

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new ElectricFurnaceManager();
        ConsoleLogger.info("[ElectricFurnace] Manager initialized.");
    }

    public static ElectricFurnaceManager getInstance() { return instance; }

    public static void configure(int cost, long cooldown) {
        energyCost = cost;
        cooldownMs = cooldown;
    }

    // =========================
    // TICK — периодическое сканирование предметов
    // =========================
    public static void tick() {
        CableNetwork.forEachNode(node -> {
            BlockPos nodePos = node.getPos();
            Level level = node.getLevel();
            if (level == null || !(level instanceof ServerLevel sl)) return;

            for (BlockPos nearby : LocationUtil.getNeighbors(nodePos)) {
                if (!level.getBlockState(nearby).is(Materials.BLAST_FURNACE)) continue;

                long now = System.currentTimeMillis();
                Long lastCook = cookingCooldowns.get(nearby);
                if (lastCook != null && (now - lastCook) < cooldownMs) continue;

                // Scan for items on top of furnace
                BlockPos furnaceTop = nearby.above();
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(furnaceTop.getX(), furnaceTop.getY(), furnaceTop.getZ(),
                                                          furnaceTop.getX() + 1, furnaceTop.getY() + 1, furnaceTop.getZ() + 1))) {
                    ItemStack stack = item.getItem();
                    if (stack.isEmpty()) continue;

                    if (tryCookItem(nearby, level, stack)) break;
                }
            }
        });
    }

    // =========================
    // COOKING LOGIC
    // =========================
    private static boolean tryCookItem(BlockPos furnacePos, Level level, ItemStack stack) {
        long now = System.currentTimeMillis();
        Long lastCook = cookingCooldowns.get(furnacePos);
        if (lastCook != null && (now - lastCook) < cooldownMs) return false;

        // Find cooking recipe
        var recipeManager = level.getRecipeManager();
        var recipe = recipeManager.getRecipeFor(RecipeType.SMELTING, 
                new SingleRecipeInput(stack), level);
        if (recipe.isEmpty()) return false;

        // Energy check: pull from cable below furnace
        BlockPos below = furnacePos.below();
        if (!CableNetwork.exists(level, below)) return false;
        if (!pullEnergyFromNetwork(level, below, energyCost)) return false;

        cookingCooldowns.put(furnacePos, now);

        if (level instanceof ServerLevel sl) {
            // Lightning effect
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    furnacePos.getX() + 0.5, furnacePos.getY() + 1.0, furnacePos.getZ() + 0.5,
                    30, 0.5, 0.5, 0.5, 0);
            sl.playSound(null, furnacePos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 0.8f, 1.2f);
        }

        // Spawn cooked result
        ItemStack result = recipe.get().value().getResultItem(level.registryAccess()).copy();
        result.setCount(stack.getCount());
        ItemEntity resultEntity = new ItemEntity(level,
                furnacePos.getX() + 0.5, furnacePos.getY() + 1.5, furnacePos.getZ() + 0.5,
                result);
        level.addFreshEntity(resultEntity);

        return true;
    }

    // =========================
    // ENERGY PULL (BFS to batteries)
    // =========================
    private static boolean pullEnergyFromNetwork(Level level, BlockPos cablePos, int amount) {
        CableNode start = CableNetwork.getNode(level, cablePos);
        if (start == null) return false;

        String worldKey = LocationUtil.worldKey(level);
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
        return remaining <= 0;
    }

    public static void clearAll() {
        cookingCooldowns.clear();
    }
}
