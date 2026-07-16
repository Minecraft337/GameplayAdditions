package com.gameplayadditions.util;

import com.gameplayadditions.GameplayAdditionsMod;
import net.neoforged.fml.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConsoleLogger — цветной логгер для мода.
 * <p>
 * Цветовая схема:
 * <ul>
 *   <li>{@link #info(String)} — информационные сообщения</li>
 *   <li>{@link #success(String)} — успешные операции</li>
 *   <li>{@link #warn(String)} — предупреждения</li>
 *   <li>{@link #error(String)} — ошибки</li>
 * </ul>
 */
public final class ConsoleLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameplayAdditionsMod.MOD_ID);
    private static boolean initialized = false;

    private ConsoleLogger() {}

    /**
     * Инициализировать логгер.
     */
    public static void init() {
        initialized = true;
        info("ConsoleLogger initialized.");
    }

    /** Информационные сообщения */
    public static void info(String message) {
        if (!initialized) return;
        LOGGER.info("{}", message);
    }

    /** Отладочные сообщения. По умолчанию отключены — чтобы увидеть, поднимите уровень логгера в конфиге сервера. */
    public static void debug(String message) {
        if (!initialized) return;
        LOGGER.debug("{}", message);
    }

    /** Успешные операции */
    public static void success(String message) {
        if (!initialized) return;
        LOGGER.info("{}", message);
    }

    /** Предупреждения */
    public static void warn(String message) {
        if (!initialized) return;
        LOGGER.warn("{}", message);
    }

    /** Ошибки */
    public static void error(String message) {
        if (!initialized) return;
        LOGGER.error("{}", message);
    }

    /**
     * Сырое сообщение без префикса (для ASCII-баннеров).
     */
    public static void raw(String message) {
        if (!initialized) return;
        LOGGER.info("{}", message);
    }
}
