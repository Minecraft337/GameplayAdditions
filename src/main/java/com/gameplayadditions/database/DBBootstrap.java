package com.gameplayadditions.database;

import com.gameplayadditions.util.ConsoleLogger;

/**
 * DBBootstrap — инициализация и shutdown SQLite.
 * <p>
 * Порт {@code com.mcplugin.database.DBBootstrap} из MC-Plugin.
 */
public class DBBootstrap {

    public static void init() {
        try {
            DatabaseManager.connect();
            DatabaseInit.init();
            ConsoleLogger.info("[DB] SQLite initialized successfully.");
        } catch (Exception e) {
            ConsoleLogger.error("[DB] SQLite initialization failed: " + e.getMessage());
        }
    }

    public static void shutdown() {
        try {
            DatabaseManager.close();
            ConsoleLogger.info("[DB] SQLite connection closed.");
        } catch (Exception e) {
            ConsoleLogger.warn("[DB] SQLite shutdown failed: " + e.getMessage());
        }
    }
}
