package com.gameplayadditions.server;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.MinecraftServer;

/**
 * RedstoneGuard — защита от лагов красной пыли.
 * Портирован из MC-Plugin.
 */
public class RedstoneGuard {

    private static boolean enabled = true;
    private static int redstoneTickCount = 0;
    private static final int MAX_REDSTONE_TICKS = 1000; // per second

    public static void init() {
        ConsoleLogger.info("[RedstoneGuard] Initialized.");
    }

    public static boolean allowRedstoneUpdate() {
        if (!enabled) return true;
        redstoneTickCount++;
        if (redstoneTickCount > MAX_REDSTONE_TICKS) {
            return false;
        }
        return true;
    }

    public static void resetTickCount() {
        redstoneTickCount = 0;
    }

    public static void setEnabled(boolean val) { enabled = val; }
}
