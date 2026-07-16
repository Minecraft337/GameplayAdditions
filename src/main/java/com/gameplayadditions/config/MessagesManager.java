package com.gameplayadditions.config;

import com.gameplayadditions.util.ConsoleLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * MessagesManager — загрузка messages.yml (MiniMessage-строки).
 * <p>
 * Аналог {@code com.mcplugin.config.MessagesManager} из MC-Plugin.
 */
public class MessagesManager {

    private static MessagesManager instance;
    private Map<String, String> messages = new HashMap<>();
    private File messagesFile;
    private File configDir;

    public static void init(File configDir) {
        instance = new MessagesManager();
        instance.configDir = configDir;
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        instance.messagesFile = new File(configDir, "messages.yml");
        instance.reload();
    }

    public static MessagesManager getInstance() {
        return instance;
    }

    // =========================
    // LOAD / RELOAD
    // =========================

    @SuppressWarnings("unchecked")
    public void reload() {
        if (!messagesFile.exists()) {
            saveDefaultMessages();
        }

        messages.clear();

        try (InputStream input = new FileInputStream(messagesFile)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(input);
            if (loaded instanceof Map) {
                Map<String, Object> raw = (Map<String, Object>) loaded;
                flattenMap(raw, "");
            }
            ConsoleLogger.info("[Messages] Loaded: " + messagesFile.getName());
        } catch (Exception e) {
            ConsoleLogger.warn("[Messages] Failed to load messages.yml: " + e.getMessage());
        }
    }

    /**
     * Разворачивает вложенные Map в плоские ключи (пример: "auth.login.success").
     */
    @SuppressWarnings("unchecked")
    private void flattenMap(Map<String, Object> map, String prefix) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flattenMap((Map<String, Object>) entry.getValue(), key);
            } else {
                messages.put(key, entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }
    }

    private void saveDefaultMessages() {
        try (InputStream defaultMessages = getClass().getClassLoader()
                .getResourceAsStream("messages.yml")) {
            if (defaultMessages != null) {
                Files.copy(defaultMessages, messagesFile.toPath());
                ConsoleLogger.info("[Messages] Created default messages.yml");
            } else {
                ConsoleLogger.info("[Messages] No default messages.yml in resources.");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Messages] Failed to create default messages: " + e.getMessage());
        }
    }

    // =========================
    // GETTERS
    // =========================

    public static String getString(String path, String def) {
        if (instance == null) return def;
        return instance.messages.getOrDefault(path, def);
    }
}
