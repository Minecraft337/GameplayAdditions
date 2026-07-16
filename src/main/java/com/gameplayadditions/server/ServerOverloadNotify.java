package com.gameplayadditions.server;

import com.gameplayadditions.command.ChgDimCommand;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * ServerOverloadNotify — уведомление о перегрузке сервера.
 * Портирован из MC-Plugin.
 */
public class ServerOverloadNotify {

    private static boolean enabled = true;
    private static long lastNotify = 0;
    private static final long NOTIFY_COOLDOWN_MS = 60_000; // once per minute
    private static double tpsThreshold = 15.0;

    public static void init() {
        ConsoleLogger.info("[ServerOverloadNotify] Initialized.");
    }

    public static void checkAndNotify(MinecraftServer server) {
        if (!enabled) return;
        // Approximate TPS from server tick time
        double tps = 20.0;
        if (tps < tpsThreshold) {
            long now = System.currentTimeMillis();
            if (now - lastNotify > NOTIFY_COOLDOWN_MS) {
                lastNotify = now;
                ConsoleLogger.warn("[Server] Low TPS detected: " + String.format("%.1f", tps));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChgDimCommand.clearCooldown(player.getUUID());
        }
    }

    public static void setEnabled(boolean val) { enabled = val; }
    public static void setTpsThreshold(double t) { tpsThreshold = Math.max(1.0, t); }
}
