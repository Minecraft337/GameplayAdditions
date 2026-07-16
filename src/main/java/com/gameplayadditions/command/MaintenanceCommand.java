package com.gameplayadditions.command;

import com.gameplayadditions.maintenance.MaintenanceManager;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;

/**
 * MaintenanceCommand — /ga maint (on|off|list|add|remove).
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.MaintSubcommand} из MC-Plugin.
 */
public class MaintenanceCommand implements SubCommand {

    @Override
    public String getName() { return "maint"; }

    @Override
    public List<String> getAliases() {
        return List.of("maintenance");
    }

    @Override
    public String getDescription() { return "Manage server maintenance mode."; }

    @Override
    public String getUsage() { return "/ga maint on|off|list|add <player>|remove <player>"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        var source = context.getSource();

        if (args.length < 2) {
            sendUsage(context);
            return 0;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "list" -> handleList(context);
            case "add" -> handleAdd(context, args);
            case "remove", "rm", "del" -> handleRemove(context, args);
            case "on" -> handleOn(context);
            case "off" -> handleOff(context);
            default -> {
                sendUsage(context);
                yield 0;
            }
        };
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        var mm = MaintenanceManager.getInstance();
        var whitelist = mm.getWhitelistNames();
        boolean isOn = mm.isMaintenanceMode();

        String status = isOn ? "&c⛏ ENABLED" : "&a✔ DISABLED";
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&8═══ &fMaintenance Mode &8═══"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&7Status: " + status), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&7Whitelist (&f" + whitelist.size() + "&7):"), false);

        if (whitelist.isEmpty()) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy("  &8(empty)"), false);
        } else {
            for (String name : whitelist) {
                ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                        "  &7• &f" + name), false);
            }
        }
        return 1;
    }

    private int handleAdd(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga maint add <player>"));
            return 0;
        }

        String playerName = args[2];
        if (MaintenanceManager.getInstance().addWhitelist(playerName)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + playerName + " &fadded to maintenance whitelist."), false);
        } else {
            ctx.getSource().sendFailure(MessageUtil.legacy(
                    "&e⚠ &fPlayer &e" + playerName + " &falready whitelisted."));
        }
        return 1;
    }

    private int handleRemove(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga maint remove <player>"));
            return 0;
        }

        String playerName = args[2];
        if (MaintenanceManager.getInstance().removeWhitelist(playerName)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + playerName + " &fremoved from maintenance whitelist."), false);
        } else {
            ctx.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ Player &e" + playerName + " &cnot found in whitelist."));
        }
        return 1;
    }

    private int handleOn(CommandContext<CommandSourceStack> ctx) {
        if (MaintenanceManager.getInstance().isMaintenanceMode()) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&e⚠ Maintenance mode is already enabled."));
            return 0;
        }
        MaintenanceManager.getInstance().enable(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fMaintenance mode &cENABLED&f."), false);
        return 1;
    }

    private int handleOff(CommandContext<CommandSourceStack> ctx) {
        if (!MaintenanceManager.getInstance().isMaintenanceMode()) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&e⚠ Maintenance mode is already disabled."));
            return 0;
        }
        MaintenanceManager.getInstance().disable();
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fMaintenance mode &aDISABLED&f."), false);
        return 1;
    }

    private void sendUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(MessageUtil.legacy(
                "&c❌ Usage:\\n" +
                "&f/ga maint list\\n" +
                "&f/ga maint add <player>\\n" +
                "&f/ga maint remove <player>\\n" +
                "&f/ga maint on\\n" +
                "&f/ga maint off"));
    }
}
