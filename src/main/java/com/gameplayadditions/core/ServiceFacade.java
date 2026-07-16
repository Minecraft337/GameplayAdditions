package com.gameplayadditions.core;

import com.gameplayadditions.module.ModuleManager;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.MessageUtil;

/**
 * ServiceFacade — централизованная точка доступа к сервисам мода.
 * <p>
 * Аналог {@code com.mcplugin.core.ServiceFacade} из MC-Plugin.
 */
public final class ServiceFacade {

    private ServiceFacade() {}

    // ========================================================================
    // LOGGING
    // ========================================================================

    public static void info(String msg) { ConsoleLogger.info(msg); }
    public static void warn(String msg) { ConsoleLogger.warn(msg); }
    public static void error(String msg) { ConsoleLogger.error(msg); }
    public static void success(String msg) { ConsoleLogger.success(msg); }

    // ========================================================================
    // MODULE MANAGER
    // ========================================================================

    public static ModuleManager mm() {
        return ModuleManager.getInstance();
    }

    // ========================================================================
    // MESSAGES
    // ========================================================================

    public static String message(String path, String def) {
        // TODO: implement MessagesManager when config module is ready
        return def;
    }

    public static String parsed(String key, String def) {
        return MessageUtil.parse(message(key, def));
    }
}
