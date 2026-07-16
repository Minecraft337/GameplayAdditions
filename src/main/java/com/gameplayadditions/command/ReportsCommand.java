package com.gameplayadditions.command;

import com.gameplayadditions.report.ReportManager;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * ReportsCommand — /ga reports list.
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.ReportsSubcommand} из MC-Plugin.
 */
public class ReportsCommand implements SubCommand {

    @Override
    public String getName() { return "reports"; }

    @Override
    public String getDescription() { return "View and manage player reports (admin)."; }

    @Override
    public String getUsage() { return "/ga reports list"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        var source = context.getSource();

        if (args.length < 2) {
            source.sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga reports list"));
            return 0;
        }

        String action = args[1].toLowerCase();

        if (!action.equals("list")) {
            source.sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga reports list"));
            return 0;
        }

        var reports = ReportManager.getAllReports();

        source.sendSuccess(() -> MessageUtil.legacy("&8═══ &fReports &8═══"), false);
        source.sendSuccess(() -> MessageUtil.legacy("&7Total: &f" + reports.size()), false);

        if (reports.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.legacy("  &8(empty)"), false);
            return 1;
        }

        for (var r : reports) {
            String statusColor = switch (r.status) {
                case "pending" -> "&e";
                case "confirmed" -> "&a";
                case "rejected" -> "&c";
                case "closed" -> "&7";
                case "expired" -> "&8";
                default -> "&f";
            };

            source.sendSuccess(() -> MessageUtil.legacy(
                    "&7#" + r.id + " &f" + r.reportedName +
                    " &7by &f" + r.reporterName +
                    " " + statusColor + "[" + r.status + "]"), false);
            source.sendSuccess(() -> MessageUtil.legacy(
                    "  &7└ " + r.reason), false);
        }

        return 1;
    }
}
