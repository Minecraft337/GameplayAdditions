package com.gameplayadditions.mechanics.features.blocks;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Конфигурируемая система: если в контейнере (сундук, бочка и т.д.) на заданных
 * координатах есть предметы — указанные блоки начинают мигать заданным
 * {@code BlockState} свойством (например, {@code lit} на фонаре).
 * <p>
 * Когда контейнер пуст — блоки возвращаются в off-value состояние.
 * <p>
 * Свойства, которые можно моргать — type-safe обработаны три подтипа:
 * {@link BooleanProperty} ({@code lit}, {@code powered}, {@code waterlogged}…),
 * {@link IntegerProperty} ({@code age}, {@code delay}…),
 * {@link DirectionProperty} ({@code facing}, {@code rotation}…).
 * Остальные property types пропускаются как unsupported.
 */
public class ContainerTriggerFeature extends AbstractFeature {

    private static final boolean ENABLED = true;
    private static final int INTERVAL_TICKS = 1; // каждые N тиков сервера

    private static final List<TriggerConfig> TRIGGERS = new ArrayList<>();

    // ============================
    // DTO
    // ============================
    public static class TriggerConfig {
        public String name;
        public ResourceKey<Level> sourceDimension;
        public int sourceX, sourceY, sourceZ;
        public List<TargetBlock> targetBlocks = new ArrayList<>();
        public String stateProperty; // e.g. "lit"
        public String onValue;
        public String offValue;
        public double blinkRate; // blinks per second
    }

    public static class TargetBlock {
        public ResourceKey<Level> dimension;
        public int x, y, z;
    }

    // ============================
    // Lifecycle
    // ============================
    @Override
    public String getName() {
        return "container_trigger";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        TRIGGERS.clear();
        // TODO: load triggers from YAML (features.container_trigger.*).
        // Сейчас пусто — фича no-op до загрузки конфига.
        ConsoleLogger.info("[ContainerTrigger] Initialized with " + TRIGGERS.size() + " trigger(s).");
        registerGameEvents();
        super.onServerStart(event);
    }

    // ============================
    // Tick
    // ============================
    @SubscribeEvent
    public void onServerTickPre(ServerTickEvent.Pre event) {
        if (!ENABLED || TRIGGERS.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long now = System.currentTimeMillis();

        for (TriggerConfig trigger : TRIGGERS) {
            try {
                ServerLevel level = server.getLevel(trigger.sourceDimension);
                if (level == null) continue;

                // Проверка загрузки чанка
                if (!level.hasChunkAt(trigger.sourceX, trigger.sourceZ)) continue;

                BlockPos sourcePos = new BlockPos(trigger.sourceX, trigger.sourceY, trigger.sourceZ);
                boolean hasItems = checkContainerHasItems(level, sourcePos);

                applyBlinkState(level, trigger, now, hasItems);
            } catch (Exception e) {
                ConsoleLogger.warn("[ContainerTrigger] Error processing trigger '" + trigger.name + "': " + e.getMessage());
            }
        }
    }

    // ============================
    // Public API
    // ============================
    public static void addTrigger(TriggerConfig trigger) {
        TRIGGERS.add(trigger);
    }

    public static List<TriggerConfig> getTriggers() {
        return Collections.unmodifiableList(TRIGGERS);
    }

    /** Convenience helper для YAML-парсера: {@code "minecraft:overworld"} → {@link ResourceKey}. */
    public static ResourceKey<Level> dimensionKey(String dimString) {
        if (dimString == null || dimString.isBlank()) return Level.OVERWORLD;
        ResourceLocation loc = ResourceLocation.tryParse(dimString.toLowerCase(Locale.ROOT));
        if (loc == null) return Level.OVERWORLD;
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
    }

    // ============================
    // Container check
    // ============================
    private static boolean checkContainerHasItems(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) return false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    // ============================
    // Blink propagation
    // ============================
    private static void applyBlinkState(ServerLevel sourceLevel, TriggerConfig trigger, long nowMs, boolean hasItems) {
        // Determine target value (off / on by time)
        String targetValue;
        if (!hasItems) {
            targetValue = trigger.offValue;
        } else {
            // Blink phase — scaled fixed-point math to avoid floating-point drift
            long scaledPeriod = (long) (1000.0 * 1000.0 / trigger.blinkRate); // microseconds per period
            if (scaledPeriod < 1) scaledPeriod = 1;
            long scaledHalf = scaledPeriod / 2;
            long scaledPhase = (nowMs * 1000L) % scaledPeriod;
            boolean isOnPhase = scaledPhase < scaledHalf;
            targetValue = isOnPhase ? trigger.onValue : trigger.offValue;
        }

        MinecraftServer server = sourceLevel.getServer();
        for (TargetBlock target : trigger.targetBlocks) {
            try {
                ServerLevel targetLevel = target.dimension.equals(trigger.sourceDimension)
                        ? sourceLevel
                        : server.getLevel(target.dimension);
                if (targetLevel == null) continue;
                if (!targetLevel.hasChunkAt(target.x, target.z)) continue;

                BlockPos targetPos = new BlockPos(target.x, target.y, target.z);
                setBlockStateProperty(targetLevel, targetPos, trigger.stateProperty, targetValue);
            } catch (Exception ignored) {
                // skip per-target errors to keep loop alive
            }
        }
    }

    // ============================
    // BlockState mutation (type-safe switch)
    // ============================
    private static void setBlockStateProperty(ServerLevel level, BlockPos pos, String propertyName, String valueStr) {
        BlockState state = level.getBlockState(pos);
        BlockState newState = mutateProperty(state, propertyName, valueStr);
        if (newState == null || newState == state) return;
        // UPDATE_CLIENTS — отправляет клиентам и сохраняет серверный state,
        // но НЕ триггерит neighbour-physics updates (Bukkit `applyPhysics=false`).
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
    }

    private static BlockState mutateProperty(BlockState state, String propertyName, String valueStr) {
        for (Property<?> prop : state.getProperties()) {
            if (!prop.getName().equalsIgnoreCase(propertyName)) continue;

            if (prop instanceof BooleanProperty bp) {
                Boolean newVal = parseBooleanStrict(valueStr);
                if (newVal == null) return null;
                Boolean cur = state.getValue(bp);
                if (cur.equals(newVal)) return state;
                return state.setValue(bp, newVal);
            }
            if (prop instanceof IntegerProperty ip) {
                Integer newVal;
                try { newVal = Integer.parseInt(valueStr); }
                catch (NumberFormatException e) { return null; }
                Integer cur = state.getValue(ip);
                if (cur.equals(newVal)) return state;
                return state.setValue(ip, newVal);
            }
            if (prop instanceof DirectionProperty dp) {
                Direction newVal = Direction.byName(valueStr.toLowerCase(Locale.ROOT));
                if (newVal == null) return null;
                Direction cur = state.getValue(dp);
                if (cur.equals(newVal)) return state;
                return state.setValue(dp, newVal);
            }

            // Unsupported property type (EnumProperty etc) — silently skip
            return null;
        }
        return null;
    }

    private static Boolean parseBooleanStrict(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("on")) return Boolean.TRUE;
        if (lower.equals("false") || lower.equals("off")) return Boolean.FALSE;
        return null;
    }
}
