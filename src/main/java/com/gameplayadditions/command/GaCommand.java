package com.gameplayadditions.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.concurrent.CompletableFuture;

/**
 * GaCommand — главная команда /ga (Gameplay Additions).
 * <p>
 * Использует greedyString для захвата всех аргументов и диспетчеризации
 * через {@link SubCommandRegistry} — как в оригинальном MC-Plugin.
 * <p>
 * Регистрируется в {@link net.neoforged.neoforge.event.RegisterCommandsEvent}.
 */
public class GaCommand {

    private static final String COMMAND_NAME = "ga";

    /**
     * Регистрирует команду /ga в Brigadier диспетчере.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal(COMMAND_NAME)
                        // /ga без аргументов → help
                        .executes(ctx -> {
                            new HelpCommand().execute(ctx, new String[]{"help"});
                            return Command.SINGLE_SUCCESS;
                        })
                        // /ga <args...> — всё через SubCommandRegistry
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(GaCommand::executeWithArgs)
                                .suggests(GaCommand::suggest))
        );
    }

    /**
     * Выполняет команду с переданными аргументами.
     */
    private static int executeWithArgs(CommandContext<CommandSourceStack> context) {
        String argsStr = StringArgumentType.getString(context, "args");
        String[] args = argsStr.split(" ");

        var registry = SubCommandRegistry.getInstance();
        return registry.dispatch(context, args);
    }

    /**
     * SuggestionProvider для таб-комплита субкоманд.
     */
    private static CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        String input = builder.getRemaining().toLowerCase();
        var registry = SubCommandRegistry.getInstance();

        // Если есть пробел — уже выбрана субкоманда
        if (input.contains(" ")) {
            return Suggestions.empty();
        }

        // Предлагаем имена субкоманд
        for (String name : registry.getCommandNames()) {
            if (name.startsWith(input)) {
                builder.suggest(name);
            }
        }
        // Добавляем алиасы
        for (var cmd : registry.getAllCommands()) {
            for (String alias : cmd.getAliases()) {
                if (alias.startsWith(input)) {
                    builder.suggest(alias);
                }
            }
        }

        return builder.buildFuture();
    }
}
