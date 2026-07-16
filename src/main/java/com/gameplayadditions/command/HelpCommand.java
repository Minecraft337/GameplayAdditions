package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;

/**
 * HelpCommand — отображает список всех зарегистрированных команд.
 * <p>
 * Аналог {@code HelpSubcommand} из MC-Plugin.
 */
public class HelpCommand implements SubCommand {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Show command list.";
    }

    @Override
    public String getUsage() {
        return "/ga help [page]";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        var source = context.getSource();
        var registry = SubCommandRegistry.getInstance();
        var commands = registry.getAllCommands().stream()
                .filter(cmd -> !cmd.getName().equals("help"))
                .toList();

        // Make a defensive copy for use in lambdas (effectively final)
        int currentPage = 1;
        if (args.length > 1) {
            try {
                currentPage = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        int perPage = 8;
        int totalPages = Math.max(1, (int) Math.ceil((double) commands.size() / perPage));
        int clampedPage = Math.max(1, Math.min(currentPage, totalPages));

        int start = (clampedPage - 1) * perPage;
        int end = Math.min(start + perPage, commands.size());

        // Make final copies for lambda
        final int page = clampedPage;
        final int pages = totalPages;

        source.sendSuccess(() -> MessageUtil.legacy(""), false);
        source.sendSuccess(() -> MessageUtil.legacy("&6═══════════════════════════════════"), false);
        source.sendSuccess(() -> MessageUtil.legacy("&6  &e✦ &fGameplay Additions &7- Commands"), false);
        source.sendSuccess(() -> MessageUtil.legacy("&6═══════════════════════════════════"), false);

        for (int i = start; i < end; i++) {
            var cmd = commands.get(i);
            final String usage = cmd.getUsage();
            final String desc = cmd.getDescription();
            source.sendSuccess(() -> MessageUtil.legacy("&7┃ &e" + usage + " &8- &7" + desc), false);
        }

        source.sendSuccess(() -> MessageUtil.legacy("&6═══════════════════════════════════"), false);
        source.sendSuccess(() -> MessageUtil.legacy("&7Page &e" + page + "&7/&e" + pages), false);

        return 1;
    }
}
