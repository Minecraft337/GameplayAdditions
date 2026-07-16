package com.gameplayadditions.mechanics.particle;

import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ParticleAcceleratorManager — управление ускорителем частиц.
 * Портирован из MC-Plugin с адаптацией для NeoForge.
 */
public class ParticleAcceleratorManager {

    public static final net.minecraft.world.level.block.Block RING = Blocks.CHISELED_TUFF_BRICKS;
    public static final net.minecraft.world.level.block.Block ENGINE = Blocks.TUFF_BRICKS;
    public static final net.minecraft.world.level.block.Block SENSOR = Blocks.POLISHED_DIORITE;
    public static final net.minecraft.world.level.block.Block INJECTOR = Blocks.REINFORCED_DEEPSLATE;

    public static final Set<net.minecraft.world.level.block.Block> ACCELERATOR_BLOCKS = Set.of(RING, ENGINE, SENSOR, INJECTOR);

    private static final int ENGINE_MAX_ENERGY = 500;
    private static final int ENGINE_COST_PER_USE = 50;
    private static final int ENGINE_CHARGE_RATE = 10;
    public static final double SPEED_INCREMENT = 0.1;
    public static final double MAX_SPEED = 5.0;
    public static final double INITIAL_SPEED = 0.1;

    private static final Map<String, Integer> engineEnergy = new ConcurrentHashMap<>();
    private static final Map<UUID, ParticleData> activeParticles = new ConcurrentHashMap<>();
    private static boolean enabled = true;

    public static class ParticleData {
        public final UUID id;
        public Vec3 location;
        public final String itemName;
        public final net.minecraft.world.item.Item sourceMaterial;
        public List<BlockPos> path;
        public int pathIndex;
        public double speed;
        public boolean dead = false;
        public Level level;

        public ParticleData(UUID id, Level level, BlockPos start, net.minecraft.world.item.Item source, List<BlockPos> path) {
            this.id = id;
            this.location = new Vec3(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
            this.level = level;
            this.sourceMaterial = source;
            this.itemName = source.getDescription().getString();
            this.path = path;
            this.pathIndex = 0;
            this.speed = INITIAL_SPEED;
        }
    }

    public static void init() {
        enabled = true;
        ConsoleLogger.info("[ParticleAccelerator] Manager initialized.");
    }

    public static void shutdown() {
        activeParticles.clear();
        engineEnergy.clear();
        ConsoleLogger.info("[ParticleAccelerator] Shutdown complete.");
    }

    public static void onBlockPlaced(Level level, BlockPos pos) {
        String key = String.valueOf(LocationUtil.toKey(pos));
        if (level.getBlockState(pos).is(ENGINE)) {
            engineEnergy.putIfAbsent(key, 0);
        }
    }

    public static void onBlockBroken(Level level, BlockPos pos, net.minecraft.world.level.block.Block block) {
        String key = String.valueOf(LocationUtil.toKey(pos));
        if (block == ENGINE) {
            engineEnergy.remove(key);
        }
        activeParticles.values().removeIf(p -> {
            if (p.dead) return true;
            if (p.level == level) {
                for (BlockPos bp : p.path) {
                    if (bp.equals(pos)) {
                        p.dead = true;
                        return true;
                    }
                }
            }
            return false;
        });
    }

    public static void onInjectorInteract(ServerPlayer player, Level level, BlockPos pos) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Hold an item to inject!"));
            return;
        }

        List<BlockPos> path = findPath(level, pos);
        if (path.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("No accelerator path found!"));
            return;
        }

        boolean alreadyRunning = activeParticles.values().stream()
                .anyMatch(p -> !p.dead && p.level == level && pathsOverlap(p.path, path));
        if (alreadyRunning) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("A particle is already running!"));
            return;
        }

        UUID id = UUID.randomUUID();
        ParticleData data = new ParticleData(id, level, pos, hand.getItem(), path);
        activeParticles.put(id, data);

        hand.shrink(1);

        if (level instanceof ServerLevel sl) {
            sl.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1.0f, 1.5f);
            sl.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    20, 0.3, 0.3, 0.3, 0.01);
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Particle injected!"));
    }

    public static void tick(ServerLevel level) {
        if (!enabled) return;

        // Charge engines
        chargeEngines(level);

        // Move particles
        activeParticles.values().removeIf(data -> {
            if (data.dead || data.level != level) return false;
            tickParticle(level, data);
            return data.dead;
        });
    }

    private static void tickParticle(ServerLevel level, ParticleData data) {
        if (data.path == null || data.path.isEmpty() || data.pathIndex >= data.path.size()) {
            dissipateParticle(level, data);
            return;
        }

        BlockPos target = data.path.get(data.pathIndex);
        double dx = target.getX() + 0.5 - data.location.x;
        double dy = target.getY() + 0.5 - data.location.y;
        double dz = target.getZ() + 0.5 - data.location.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance <= 0.4) {
            handleBlockReached(level, data, target);
            data.pathIndex++;
            if (data.pathIndex >= data.path.size()) {
                dissipateParticle(level, data);
                return;
            }
            data.location = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        } else {
            double moveAmount = Math.min(data.speed, distance);
            double ratio = moveAmount / distance;
            data.location = new Vec3(
                    data.location.x + dx * ratio,
                    data.location.y + dy * ratio,
                    data.location.z + dz * ratio
            );
        }

        level.sendParticles(ParticleTypes.END_ROD,
                data.location.x, data.location.y, data.location.z,
                2, 0.05, 0.05, 0.05, 0.001);
    }

    private static void handleBlockReached(ServerLevel level, ParticleData data, BlockPos blockLoc) {
        BlockState state = level.getBlockState(blockLoc);
        if (state.is(ENGINE)) {
            if (consumeEngineEnergy(blockLoc) && data.speed < MAX_SPEED) {
                data.speed = Math.min(MAX_SPEED, data.speed + SPEED_INCREMENT);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        blockLoc.getX() + 0.5, blockLoc.getY() + 0.5, blockLoc.getZ() + 0.5,
                        5, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    private static void dissipateParticle(ServerLevel level, ParticleData data) {
        level.sendParticles(ParticleTypes.END_ROD,
                data.location.x, data.location.y, data.location.z,
                15, 0.3, 0.3, 0.3, 0.02);
        data.dead = true;
    }

    private static List<BlockPos> findPath(Level level, BlockPos start) {
        List<BlockPos> path = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos current = start;
        visited.add(current);

        BlockPos next = findNextAcceleratorBlock(level, current, null);
        while (next != null && !visited.contains(next)) {
            path.add(next);
            visited.add(next);
            BlockPos prev = current;
            current = next;
            next = findNextAcceleratorBlock(level, current, prev);
        }
        return path;
    }

    private static boolean pathsOverlap(List<BlockPos> pathA, List<BlockPos> pathB) {
        Set<BlockPos> setA = new HashSet<>(pathA);
        for (BlockPos loc : pathB) {
            if (setA.contains(loc)) return true;
        }
        return false;
    }

    private static BlockPos findNextAcceleratorBlock(Level level, BlockPos from, BlockPos exclude) {
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : dirs) {
            BlockPos neighbor = from.offset(d[0], d[1], d[2]);
            if (exclude != null && neighbor.equals(exclude)) continue;
            BlockState state = level.getBlockState(neighbor);
            if (ACCELERATOR_BLOCKS.contains(state.getBlock())) {
                return neighbor;
            }
        }
        return null;
    }

    private static void chargeEngines(ServerLevel level) {
        for (Map.Entry<String, Integer> entry : engineEnergy.entrySet()) {
            int current = entry.getValue();
            if (current >= ENGINE_MAX_ENERGY) continue;

            int pulled = pullFromCables(level, entry.getKey(), ENGINE_CHARGE_RATE);
            int newEnergy = Math.min(ENGINE_MAX_ENERGY, current + pulled);
            entry.setValue(newEnergy);
        }
    }

    private static int pullFromCables(ServerLevel level, String key, int maxAmount) {
        BlockPos pos = LocationUtil.fromKey(key);
        if (pos == null) return 0;

        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        int totalPulled = 0;

        for (int[] d : dirs) {
            if (totalPulled >= maxAmount) break;
            BlockPos neighbor = pos.offset(d[0], d[1], d[2]);
            var node = CableNetwork.getNode(level, neighbor);
            if (node == null || node.getType() != NodeType.CABLE) continue;
            int available = node.getEnergy();
            if (available <= 0) continue;
            int toTake = Math.min(available, maxAmount - totalPulled);
            node.removeEnergy(toTake);
            totalPulled += toTake;
        }
        return totalPulled;
    }

    private static boolean consumeEngineEnergy(BlockPos engineLoc) {
        String key = String.valueOf(LocationUtil.toKey(engineLoc));
        int current = engineEnergy.getOrDefault(key, 0);
        if (current < ENGINE_COST_PER_USE) return false;
        engineEnergy.put(key, current - ENGINE_COST_PER_USE);
        return true;
    }

    public static Collection<ParticleData> getActiveParticles() {
        return activeParticles.values();
    }

    public static boolean isEnabled() { return enabled; }
}
