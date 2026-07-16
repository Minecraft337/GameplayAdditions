package com.gameplayadditions.util;

import java.io.File;
import java.io.IOException;

/**
 * FileLogger — утилита для логирования создания файлов и директорий.
 * Портирован из MC-Plugin.
 */
public final class FileLogger {

    private FileLogger() {}

    public static void ensureFile(File file, String description) {
        if (file.exists()) {
            ConsoleLogger.info("[" + description + "] File exists: " + file.getName());
            return;
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            try {
                if (parent.mkdirs()) {
                    ConsoleLogger.info("[" + description + "] Created directory: " + parent.getPath());
                }
            } catch (Exception e) {
                ConsoleLogger.error("[" + description + "] Failed to create directory: " + parent.getPath());
            }
        }

        try {
            if (file.createNewFile()) {
                ConsoleLogger.info("[" + description + "] Created new file: " + file.getName());
            } else {
                ConsoleLogger.info("[" + description + "] File exists: " + file.getName());
            }
        } catch (IOException e) {
            ConsoleLogger.error("[" + description + "] ERROR: Failed to create file: " + file.getAbsolutePath());
        }
    }

    public static void ensureDirectory(File dir, String description) {
        if (dir.exists()) {
            if (dir.isDirectory()) {
                ConsoleLogger.info("[" + description + "] Directory exists: " + dir.getPath());
            } else {
                ConsoleLogger.warn("[" + description + "] Path exists but is NOT a directory: " + dir.getPath());
            }
            return;
        }

        try {
            if (dir.mkdirs()) {
                ConsoleLogger.info("[" + description + "] Created directory: " + dir.getPath());
            } else {
                ConsoleLogger.error("[" + description + "] ERROR: Failed to create directory: " + dir.getPath());
            }
        } catch (Exception e) {
            ConsoleLogger.error("[" + description + "] ERROR: Failed to create directory: " + dir.getPath());
        }
    }
}
