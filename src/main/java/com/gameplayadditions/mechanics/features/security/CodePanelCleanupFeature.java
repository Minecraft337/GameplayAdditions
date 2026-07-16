package com.gameplayadditions.mechanics.features.security;

import com.gameplayadditions.core.AbstractFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * CodePanelCleanupFeature — периодическая очистка просроченных ключей код-панели.
 *
 * <p>Порт {@code com.mcplugin.mechanics.security.codepanel.CodePanelCleanupTask} из MC-Plugin.
 * Bukkit: {@code BukkitRunnable} с интервалом 400 тиков (20 сек).
 * NeoForge: {@link ServerTickEvent.Post} с tick-счётчиком.
 *
 * <p>Вызывает {@code CodePanelDatabase.cleanupExpiredKeys()} при каждом тике очистки.
 * Внимание: требуется интеграция с CodePanelDatabase (TODO).
 */
public class CodePanelCleanupFeature extends AbstractFeature {

    private int tickCounter = 0;
    private final int intervalTicks = 400; // каждые 20 секунд

    @Override
    public String getName() {
        return "codepanel_cleanup";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % intervalTicks != 0) {
            return;
        }
        // TODO: интеграция с CodePanelDatabase.cleanupExpiredKeys()
        // Как только CodePanelDatabase будет портирована, раскомментировать:
        // try {
        //     CodePanelDatabase.cleanupExpiredKeys();
        // } catch (Exception e) {
        //     logError("Failed to cleanup expired CodePanel keys: " + e.getMessage());
        // }
        logInfo("[CodePanel] Cleanup tick (placeholder)");
    }
}
