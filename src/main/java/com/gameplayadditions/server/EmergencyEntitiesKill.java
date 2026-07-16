package com.gameplayadditions.server;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.MinecraftServer;

/**
 * EmergencyEntitiesKill — очистка лишних сущностей при перегрузке.
 * Портирован из MC-Plugin.
 */
public class EmergencyEntitiesKill {

    private static boolean enabled = true;
    private static int maxEntities = 500;

    public static void init() {
        ConsoleLogger.info("[EmergencyEntitiesKill] Initialized. Max entities: " + maxEntities);
    }

    public static void setEnabled(boolean val) { enabled = val; }
    public static void setMaxEntities(int max) { maxEntities = Math.max(100, max); }
}
