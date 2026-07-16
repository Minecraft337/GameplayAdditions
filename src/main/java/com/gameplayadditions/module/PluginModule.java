package com.gameplayadditions.module;

import com.gameplayadditions.util.ConsoleLogger;

/**
 * PluginModule — базовая абстракция модуля мода.
 * <p>
 * Аналог {@code com.mcplugin.module.PluginModule} из MC-Plugin.
 * <p>
 * Каждый модуль независим: если {@link #onInit} выбрасывает исключение,
 * модуль считается отключённым, но остальные модули продолжают работу.
 */
public abstract class PluginModule {

    private final String name;
    private final String modulePath;
    private boolean enabled = false;
    private final boolean essential;
    private String disableReason = null;

    public PluginModule(String name, String modulePath, boolean essential) {
        this.name = name;
        this.modulePath = modulePath;
        this.essential = essential;
    }

    public PluginModule(String name, boolean essential) {
        this(name, name.toLowerCase().replace(" ", "_"), essential);
    }

    // =========================
    // GETTERS
    // =========================

    public String getName() { return name; }
    public String getModulePath() { return modulePath; }
    public boolean isEnabled() { return enabled; }
    public boolean isEssential() { return essential; }
    public String getDisableReason() { return disableReason; }

    // =========================
    // LIFECYCLE
    // =========================

    public boolean initialize() {
        if (enabled) return true;
        try {
            onInit();
            enabled = true;
            disableReason = null;
            ConsoleLogger.info("[Module:" + name + "] \u2713 Enabled");
            return true;
        } catch (Throwable t) {
            enabled = false;
            String msg = t.getMessage() != null ? t.getMessage() : "";
            disableReason = msg.isEmpty() ? t.getClass().getSimpleName() : msg;
            ConsoleLogger.error("[Module:" + name + "] \u2717 FAILED: " + disableReason);
            return false;
        }
    }

    public boolean disable() {
        if (!enabled) return true;
        try {
            onDisable();
            ConsoleLogger.info("[Module:" + name + "] \u2713 Disabled");
        } catch (Throwable t) {
            ConsoleLogger.warn("[Module:" + name + "] Shutdown error: " + t.getMessage());
        }
        enabled = false;
        return true;
    }

    // =========================
    // ABSTRACT / OVERRIDE POINTS
    // =========================

    /** Выполнить инициализацию модуля. */
    protected abstract void onInit() throws Exception;

    /** Выполнить остановку модуля. */
    protected abstract void onDisable();
}
