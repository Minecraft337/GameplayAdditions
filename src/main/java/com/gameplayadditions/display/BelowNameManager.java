package com.gameplayadditions.display;

import com.gameplayadditions.util.ConsoleLogger;

/**
 * BelowNameManager — отображение информации под ником.
 * Портирован из MC-Plugin для NeoForge.
 * Упрощённая версия — будет расширена позже.
 */
public class BelowNameManager {

    private static BelowNameManager instance;
    private boolean enabled;

    public static void init() {
        instance = new BelowNameManager();
        instance.enabled = true;
        ConsoleLogger.info("[BelowName] Initialized (simplified mode).");
    }

    public static void shutdown() {
        instance = null;
    }
}
