package com.gameplayadditions.mechanics.features.world;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * DragonEggFeature — периодическое возрождение яйца дракона в мире End.
 *
 * <p>Порт {@code com.mcplugin.mechanics.features.world.DragonEggManager} из MC-Plugin.
 *
 * <p>Конфигурация (дефолты совпадают с Bukkit):
 * <ul>
 *   <li>{@code enabled=false} — по умолчанию выключено (т.к. дефолт был false)</li>
 *   <li>{@code world="world_the_end"} — имя End-уровня</li>
 *   <li>{@code x=0, y=142, z=0} — координаты спавна яйца</li>
 *   <li>{@code interval_ticks=1728000} — 24 часа</li>
 *   <li>{@code spawn_chance=1.0} — 100% вероятность</li>
 * </ul>
 *
 * <p>Логика поиска End-уровня:
 * <ol>
 *   <li>По имени из конфига (например, {@code world_the_end})</li>
 *   <li>Fallback — по типу измерения {@link Level#END}</li>
 * </ol>
 */
public class DragonEggFeature extends AbstractFeature {

    private int tickCounter = 0;

    // TODO(config): перенести в ConfigManager
    private boolean enabled = false;
    private String worldName = "world_the_end";
    private int eggX = 0;
    private int eggY = 142;
    private int eggZ = 0;
    private int intervalTicks = 1728000; // 24 часа
    private double spawnChance = 1.0;

    @Override
    public String getName() {
        return "dragonegg";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (!enabled) {
            return;
        }
        tickCounter++;
        if (tickCounter % intervalTicks != 0) {
            return;
        }
        if (Math.random() > spawnChance) {
            return;
        }

        ServerLevel endLevel = findEndLevel(event.getServer());
        if (endLevel == null || endLevel.isClientSide()) {
            logWarn("Skipped DragonEgg spawn: End level not available");
            return;
        }

        BlockPos eggPos = new BlockPos(eggX, eggY, eggZ);
        // B4 reviewer-fix: pre-check чтобы не делать no-op setBlockAndUpdate каждый 24ч
        if (endLevel.getBlockState(eggPos).is(Blocks.DRAGON_EGG)) {
            return;
        }
        endLevel.setBlockAndUpdate(eggPos, Blocks.DRAGON_EGG.defaultBlockState());
        logInfo("Dragon egg respawned at (" + eggX + ", " + eggY + ", " + eggZ + ") in " + endLevel.dimension().location());
    }

    private ServerLevel findEndLevel(MinecraftServer server) {
        // server.getAllLevels() возвращает Iterable<ServerLevel> в NeoForge 1.21,
        // поэтому материализуем в List для двойного прохода (name → dimension type).
        List<ServerLevel> levels = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.add(level);
        }
        // 1. По имени
        for (ServerLevel level : levels) {
            if (level.dimension().location().toString().equals(worldName)) {
                return level;
            }
        }
        // 2. Fallback — по типу измерения
        for (ServerLevel level : levels) {
            if (level.dimension() == Level.END) {
                return level;
            }
        }
        return null;
    }
}
