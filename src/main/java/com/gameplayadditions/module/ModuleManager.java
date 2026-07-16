package com.gameplayadditions.module;

import com.gameplayadditions.util.ConsoleLogger;

import java.util.*;

/**
 * ModuleManager — оркестратор модулей мода.
 * <p>
 * Аналог {@code com.mcplugin.module.ModuleManager} из MC-Plugin.
 * Регистрирует, инициализирует, останавливает и перезагружает модули.
 */
public class ModuleManager {

    private static ModuleManager instance;
    private final List<PluginModule> modules = new ArrayList<>();
    private final Map<String, PluginModule> moduleMap = new HashMap<>();

    public static void init() {
        instance = new ModuleManager();
    }

    public static ModuleManager getInstance() {
        return instance;
    }

    // =========================
    // MODULE REGISTRATION
    // =========================

    public void register(PluginModule module) {
        if (moduleMap.containsKey(module.getName())) {
            ConsoleLogger.warn("[ModuleManager] Module '" + module.getName() + "' already registered!");
            return;
        }
        modules.add(module);
        moduleMap.put(module.getName(), module);
    }

    // =========================
    // INIT ALL
    // =========================

    public void initAll() {
        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  Initializing modules...");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        int succeeded = 0;
        int failed = 0;

        for (PluginModule module : modules) {
            boolean ok = module.initialize();
            if (ok) {
                succeeded++;
            } else {
                failed++;
                if (module.isEssential()) {
                    ConsoleLogger.error("");
                    ConsoleLogger.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    ConsoleLogger.error("! ESSENTIAL MODULE FAILED: " + module.getName());
                    ConsoleLogger.error("! Reason: " + module.getDisableReason());
                    ConsoleLogger.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    ConsoleLogger.error("");
                }
            }
        }

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  Modules: " + succeeded + " OK, " + failed + " failed"
                + (failed > 0 ? " \u26A0" : ""));
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");
    }

    // =========================
    // SHUTDOWN ALL (reverse order)
    // =========================

    public void shutdownAll() {
        ConsoleLogger.info("[ModuleManager] Shutting down all modules...");
        for (int i = modules.size() - 1; i >= 0; i--) {
            modules.get(i).disable();
        }
    }

    // =========================
    // QUERIES
    // =========================

    public PluginModule getModule(String name) {
        return moduleMap.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T extends PluginModule> T getModule(Class<T> clazz) {
        for (PluginModule m : modules) {
            if (clazz.isInstance(m)) return (T) m;
        }
        return null;
    }

    public boolean isModuleEnabled(String name) {
        PluginModule m = moduleMap.get(name);
        return m != null && m.isEnabled();
    }

    public List<PluginModule> getModules() {
        return new ArrayList<>(modules);
    }

    public boolean hasFailedModules() {
        for (PluginModule m : modules) {
            if (!m.isEnabled()) return true;
        }
        return false;
    }

    // =========================
    // ENABLE / DISABLE SINGLE MODULE
    // =========================

    public boolean enableModule(String name) {
        PluginModule m = moduleMap.get(name);
        if (m == null) return false;
        if (m.isEnabled()) return true;
        return m.initialize();
    }

    public boolean disableModule(String name) {
        PluginModule m = moduleMap.get(name);
        if (m == null) return false;
        if (!m.isEnabled()) return true;
        m.disable();
        return true;
    }
}
