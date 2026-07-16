package com.gameplayadditions.command;

import com.gameplayadditions.report.ReportManager;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * ReportCommand — /ga report <player> <reason>.
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.ReportSubcommand} из MC-Plugin.
 */
public class ReportCommand implements SubCommand {

    @Override
    public String getName() { return "report"; }

    @Override
    public String getDescription() { return "Report a player for rule violation."; }

    @Override
    public String getUsage() { return "/ga report <player> <reason>"; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        var source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(MessageUtil.legacy("&c❌ Only players can use /ga report!"));
            return 0;
        }

        if (args.length < 3) {
            source.sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga report <player> <reason>"));
            return 0;
        }

        String targetName = args[1];
        StringBuilder reason = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (reason.length() > 0) reason.append(" ");
            reason.append(args[i]);
        }

        String error = ReportManager.createReport(player.getUUID(), targetName, reason.toString());
        if (error != null) {
            source.sendFailure(MessageUtil.legacy("&c❌ " + error));
        } else {
            source.sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fReport on &e" + targetName + " &fsubmitted!"), false);
        }

        return 1;
    }
}
