package com.gameplayadditions.server;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PacketGuard — защита от подозрительных пакетов.
 * Портирован из MC-Plugin.
 */
public class PacketGuard {

    private static boolean enabled = true;
    private static final Map<UUID, Long> lastPacketTime = new ConcurrentHashMap<>();
    private static final long PACKET_THRESHOLD_MS = 50; // 50ms minimum between packets

    public static void init() {
        ConsoleLogger.info("[PacketGuard] Initialized.");
    }

    public static boolean checkPacket(UUID playerUuid) {
        if (!enabled) return true;
        long now = System.currentTimeMillis();
        Long last = lastPacketTime.get(playerUuid);
        if (last != null && (now - last) < PACKET_THRESHOLD_MS) {
            return false; // Too many packets
        }
        lastPacketTime.put(playerUuid, now);
        return true;
    }

    public static void setEnabled(boolean val) { enabled = val; }
}
