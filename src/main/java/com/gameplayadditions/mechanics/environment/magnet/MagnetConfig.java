package com.gameplayadditions.mechanics.environment.magnet;

import com.gameplayadditions.util.ConsoleLogger;

/**
 * MagnetConfig — параметры магнита.
 * <p>
 * Порт {@code com.mcplugin.mechanics.environment.magnet.MagnetConfig} из MC-Plugin.
 * До интеграции со {@code messages.yml}/{@code config.yml} через SnakeYAML —
 * параметры заданы константами. {@link #reload()} — заглушка для будущего hot-reload.
 * <p>
 * Семантика прежняя:
 * <ul>
 *   <li>{@link #MIN_RADIUS} / {@link #MAX_RADIUS} — диапазон притяжения в блоках;</li>
 *   <li>{@link #INTERVAL_TICKS} — как часто (в тиках) применяется сила;</li>
 *   <li>{@link #FORCE_BASE} + {@link #FORCE_DIST_MULT} — формула силы;</li>
 *   <li>{@link #FORCE_MAX} — потолок силы за тик;</li>
 *   <li>{@link #ITEM_Y_BOOST} — лёгкий подъём предметов по Y (чтобы висели в "центре").</li>
 * </ul>
 */
public final class MagnetConfig {

    private MagnetConfig() {}

    public static final boolean ENABLED = true;

    public static final int   MIN_RADIUS = 3;
    public static final int   MAX_RADIUS = 15;
    public static final int   INTERVAL_TICKS = 1;

    public static final double FORCE_BASE = 0.15;
    public static final double FORCE_DIST_MULT = 0.35;
    public static final double FORCE_MAX = 0.45;
    public static final double ITEM_Y_BOOST = 0.05;

    /** Активна ли фича. */
    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Hot-reload параметров из {@code config.yml}. Пока no-op —
     * будущая интеграция будет читать секцию {@code features.magnet.*}.
     */
    public static void reload() {
        ConsoleLogger.info("[Magnet] Config reload requested (using baked-in defaults until YAML hooked).");
    }
}
