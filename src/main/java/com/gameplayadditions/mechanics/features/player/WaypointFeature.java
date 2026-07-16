package com.gameplayadditions.mechanics.features.player;

import com.gameplayadditions.core.AbstractFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * WaypointFeature — управление точками пути (waypoints).
 *
 * <p>Порт {@code com.mcplugin.mechanics.features.world.WaypointManager} из MC-Plugin.
 * Bukkit: {@code BukkitRunnable} с интервалом по умолчанию 200 тиков (10 сек).
 *
 * <p>ВНИМАНИЕ: оригинальный плагин помечает, что Paper API не позволяет управлять
 * waypoints напрямую — это реализовано через датапак. Данный порт — каркас для
 * будущей интеграции с NeoForge-способом управления точками пути.
 *
 * <p>Конфигурация:
 * <ul>
 *   <li>{@code enabled=true} — вкл/выкл</li>
 *   <li>{@code interval_ticks=200} — период тика</li>
 * </ul>
 */
public class WaypointFeature extends AbstractFeature {

    private int tickCounter = 0;

    // TODO(config): перенести в ConfigManager
    private boolean enabled = true;
    private int intervalTicks = 200;

    @Override
    public String getName() {
        return "waypoint";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (!enabled) return;
        tickCounter++;
        if (tickCounter % intervalTicks != 0) return;

        // TODO: реализовать waypoint management через NeoForge API
        // В оригинале — placeholder, т.к. Paper API не поддерживает waypoints напрямую.
        // В NeoForge можно использовать scoreboard/dimension or команды.
        logInfo("[Waypoint] Tick placeholder");
    }
}
