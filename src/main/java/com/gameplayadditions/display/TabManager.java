package com.gameplayadditions.display;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * TabManager — кастомный таб-лист (header/footer).
 * Портирован из MC-Plugin для NeoForge.
 * Использует ClientboundTabListPacket для установки header/footer.
 */
public class TabManager {

    private static TabManager instance;
    private boolean enabled;
    private int intervalTicks;
    private int tickCounter = 0;

    public static void init() {
        instance = new TabManager();
        instance.enabled = true;
        instance.intervalTicks = 20;
        ConsoleLogger.info("[Tab] Initialized.");
    }

    public static void shutdown() {
        instance = null;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!enabled) return;
        tickCounter++;
        if (tickCounter < intervalTicks) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        int online = server.getPlayerList().getPlayerCount();
        int maxPlayers = server.getPlayerList().getMaxPlayers();

        Component header = Component.literal(
            "§6✦ Gameplay Additions\n§7Online: §f" + online + "§7/§f" + maxPlayers
        );
        Component footer = Component.literal(
            "§7play.yourserver.com"
        );

        ClientboundTabListPacket packet = new ClientboundTabListPacket(header, footer);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}
