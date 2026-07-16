package com.gameplayadditions.config;

import com.gameplayadditions.util.ConsoleLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;

/**
 * ConfigManager — загрузка конфигурации из config.yml.
 * <p>
 * Использует SnakeYAML (встроен в Minecraft/NeoForge).
 * Поддерживает авто-создание config.yml из ресурсов при первом запуске.
 * <p>
 * Аналог Bukkit {@code plugin.getConfig()} + {@code saveDefaultConfig()}.
 */
public class ConfigManager {

    private static ConfigManager instance;
    private File configFile;
    private Map<String, Object> config;
    private File configDir;

    public static void init(File configDir) {
        instance = new ConfigManager();
        instance.configDir = configDir;
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        instance.configFile = new File(configDir, "config.yml");
        instance.reload();
    }

    public static ConfigManager getInstance() {
        return instance;
    }

    // =========================
    // LOAD / RELOAD
    // =========================

    @SuppressWarnings("unchecked")
    public void reload() {
        // If config doesn't exist, create from resource
        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        // Load YAML
        try (InputStream input = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(input);
            if (loaded instanceof Map) {
                config = (Map<String, Object>) loaded;
            } else {
                config = Map.of();
            }
            ConsoleLogger.info("[Config] Loaded: " + configFile.getName());
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] Failed to load config.yml: " + e.getMessage());
            config = Map.of();
        }
    }

    /**
     * Создаёт config.yml из ресурсов мода (если нет).
     */
    private void saveDefaultConfig() {
        // Try to copy from classpath resources
        try (InputStream defaultConfig = getClass().getClassLoader()
                .getResourceAsStream("config.yml")) {
            if (defaultConfig != null) {
                Files.copy(defaultConfig, configFile.toPath());
                ConsoleLogger.info("[Config] Created default config.yml");
            } else {
                // No default config in resources — create empty
                ConsoleLogger.info("[Config] No default config.yml in resources, creating empty.");
                configFile.createNewFile();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] Failed to create default config: " + e.getMessage());
        }
    }

    // =========================
    // GETTERS
    // =========================

    public String getString(String path, String def) {
        Object value = getValue(path);
        return value != null ? value.toString() : def;
    }

    public int getInt(String path, int def) {
        Object value = getValue(path);
        if (value instanceof Number) return ((Number) value).intValue();
        return def;
    }

    public double getDouble(String path, double def) {
        Object value = getValue(path);
        if (value instanceof Number) return ((Number) value).doubleValue();
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = getValue(path);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return def;
    }

    public File getConfigFile() {
        return configFile;
    }

    public File getConfigDir() {
        return configDir;
    }

    // =========================
    // INTERNAL
    // =========================

    @SuppressWarnings("unchecked")
    private Object getValue(String path) {
        if (config == null) return null;

        String[] parts = path.split("\\.");
        Map<String, Object> current = config;

        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) return null;
            current = (Map<String, Object>) next;
        }

        return current.get(parts[parts.length - 1]);
    }
}
