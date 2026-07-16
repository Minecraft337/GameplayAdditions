package com.gameplayadditions.util;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MessageUtil — утилиты для обработки текстовых сообщений.
 * <p>
 * Аналог {@code com.mcplugin.util.MessageUtil} из MC-Plugin.
 * Адаптирован для NeoForge: поддерживает MiniMessage-строки и конвертацию
 * в {@link Component} для отправки игрокам.
 */
public final class MessageUtil {

    private MessageUtil() {}

    /**
     * Создаёт NeoForge Component из строки с §-цветами или MiniMessage тегами.
     * <p>
     * §-коды парсятся через {@link net.minecraft.util.StringUtil#formatText(String)}.
     */
    public static Component legacy(String message) {
        if (message == null || message.isEmpty()) return Component.literal("");
        // Заменяем & на § для удобства (чтобы не писать § в исходниках)
        String formatted = message.replace('&', '\u00A7');
        // Minecraft автоматически парсит §-коды при отображении Component.literal
        return Component.literal(formatted);
    }

    /**
     * Парсит MiniMessage-строку в plain text (убирает теги).
     * Для отправки в чат используй {@link #legacy(String)} с §-кодами.
     */
    public static String parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return "";
        return miniMessage
                .replaceAll("<[^>]+>", "")
                .replace("\\<", "<")
                .replace("\\>", ">");
    }

    /**
     * Парсит список MiniMessage-строк.
     */
    public static List<String> parse(List<String> miniMessages) {
        return miniMessages.stream()
                .map(MessageUtil::parse)
                .collect(Collectors.toList());
    }
}
