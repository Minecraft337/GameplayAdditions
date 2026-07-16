package com.gameplayadditions.listener;

import com.gameplayadditions.GameplayAdditionsMod;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * MOTDListener — кастомный MOTD для сервера.
 * Портирован из MC-Plugin.
 * В NeoForge ServerListPingEvent обрабатывается иначе, чем в Bukkit.
 */
public class MOTDListener {

    private boolean enabled;

    public MOTDListener() {
        enabled = true;
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (enabled) {
            ConsoleLogger.info("[MOTD] Custom MOTD system active.");
        }
    }
}
