package com.gameplayadditions.hook;

import com.gameplayadditions.util.ConsoleLogger;
import net.neoforged.fml.ModList;

/**
 * Утилита для безопасных хуков к softdepend-модам.
 */
public final class PluginHook {

    private PluginHook() {}

    public static boolean check(String modId, String featureName) {
        if (!ModList.get().isLoaded(modId)) {
            ConsoleLogger.info("[Hook:" + featureName + "] " + modId
                    + " not found — " + featureName + " hook disabled.");
            return false;
        }
        return true;
    }

    public static boolean check(String modId) {
        return check(modId, modId);
    }
}
