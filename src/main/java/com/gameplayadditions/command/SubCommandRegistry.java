package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SubCommandRegistry — реестр субкоманд /ga.
 * <p>
 * Аналог {@code com.mcplugin.command.SubCommandRegistry} из MC-Plugin.
 * Хранит карту имя → SubCommand и предоставляет dispatch/tabComplete.
 */
public class SubCommandRegistry {

    private static SubCommandRegistry instance;
    private final Map<String, SubCommand> commands = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public static SubCommandRegistry getInstance() {
        if (instance == null) {
            instance = new SubCommandRegistry();
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public void register(SubCommand cmd) {
        String name = cmd.getName().toLowerCase();
        commands.put(name, cmd);
        for (String alias : cmd.getAliases()) {
            aliases.put(alias.toLowerCase(), name);
        }
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    /**
     * Диспатчит субкоманду.
     *
     * @param context Brigadier контекст
     * @param args    аргументы (args[0] — имя субкоманды)
     * @return 1 если обработано, 0 если нет
     */
    public int dispatch(CommandContext<CommandSourceStack> context, String[] args) {
        if (args.length == 0) {
            context.getSource().sendFailure(
                    MessageUtil.legacy("&c❌ Use /ga help for commands.")
            );
            return 0;
        }

        String sub = args[0].toLowerCase();
        SubCommand cmd = findCommand(sub);

        if (cmd == null) {
            context.getSource().sendFailure(
                    MessageUtil.legacy("&c❌ Unknown command! Use &f/ga help&c.")
            );
            return 0;
        }

        return cmd.execute(context, args);
    }

    /**
     * Возвращает имена всех зарегистрированных команд.
     */
    public Set<String> getCommandNames() {
        return commands.keySet();
    }

    /**
     * Возвращает список SubCommand для справки.
     */
    public Collection<SubCommand> getAllCommands() {
        return commands.values();
    }

    private SubCommand findCommand(String name) {
        String lower = name.toLowerCase();
        SubCommand cmd = commands.get(lower);
        if (cmd != null) return cmd;

        String resolved = aliases.get(lower);
        if (resolved != null) return commands.get(resolved);

        return null;
    }
}
