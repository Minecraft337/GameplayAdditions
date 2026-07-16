package com.gameplayadditions.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collections;
import java.util.List;

/**
 * SubCommand — интерфейс для всех субкоманд /ga.
 * <p>
 * Аналог {@code com.mcplugin.command.SubCommand} из MC-Plugin,
 * адаптированный для NeoForge (Brigadier CommandSourceStack).
 */
public interface SubCommand {

    /**
     * Выполняет субкоманду.
     *
     * @param context Brigadier context с CommandSourceStack
     * @param args    полный массив аргументов (args[0] — имя субкоманды)
     * @return 1 если успешно, 0 если ошибка
     */
    int execute(CommandContext<CommandSourceStack> context, String[] args);

    /**
     * Возвращает имя субкоманды (регистронезависимое).
     * По умолчанию — имя класса в lowercase.
     */
    default String getName() {
        return getClass().getSimpleName()
                .replace("Command", "")
                .toLowerCase();
    }

    /**
     * Возвращает список алиасов (дополнительных имён).
     */
    default List<String> getAliases() {
        return Collections.emptyList();
    }

    /**
     * Возвращает краткое описание для /ga help.
     */
    default String getDescription() {
        return "No description available.";
    }

    /**
     * Возвращает синтаксис использования.
     */
    default String getUsage() {
        return "/ga " + getName();
    }

    /**
     * Минимальный уровень OP для использования команды (0 = все, 2 = OP, 4 = консоль).
     */
    default int getRequiredPermissionLevel() {
        return 0;
    }
}
