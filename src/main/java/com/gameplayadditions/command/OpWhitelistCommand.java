package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.whitelist.OpWhitelistManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;

/**
 * OpWhitelistCommand — /ga opwhitelist (on|off|list|add|remove).
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.OpWhitelistSubcommand} из MC-Plugin.
 */
public class OpWhitelistCommand implements SubCommand {

    @Override
    public String getName() { return "opwhitelist"; }

    @Override
    public List<String> getAliases() {
        return List.of("opwl");
    }

    @Override
    public String getDescription() { return "Manage the OP whitelist."; }

    @Override
    public String getUsage() { return "/ga opwhitelist on|off|list|add <player>|remove <player>"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        if (args.length < 2) {
            sendUsage(context);
            return 0;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "on" -> handleOn(context);
            case "off" -> handleOff(context);
            case "list" -> handleList(context);
            case "add" -> handleAdd(context, args);
            case "remove", "rm", "del" -> handleRemove(context, args);
            default -> {
                sendUsage(context);
                yield 0;
            }
        };
    }

    private int handleOn(CommandContext<CommandSourceStack> ctx) {
        if (OpWhitelistManager.setEnabled(true)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fOP whitelist &aENABLED&f."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fOP whitelist is already enabled."), false);
        }
        return 1;
    }

    private int handleOff(CommandContext<CommandSourceStack> ctx) {
        if (OpWhitelistManager.setEnabled(false)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fOP whitelist &cDISABLED&f."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fOP whitelist is already disabled."), false);
        }
        return 1;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        List<String> names = OpWhitelistManager.getWhitelistNames();
        boolean isOn = OpWhitelistManager.isEnabled();
        String status = isOn ? "&aENABLED" : "&cDISABLED";

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&8═══ &fOP Whitelist &8═══"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&7Status: " + status), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&7Players (&f" + names.size() + "&7):"), false);

        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy("  &8(empty)"), false);
        } else {
            for (String name : names) {
                var player = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                String statusStr = player != null ? "&a●" : "&8●";
                String opStatus = (player != null && player.hasPermissions(2)) ? " &6[OP]" : "";
                ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                        "  " + statusStr + " &f" + name + "" + opStatus), false);
            }
        }
        return 1;
    }

    private int handleAdd(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga opwhitelist add <player>"));
            return 0;
        }

        String playerName = args[2];
        if (OpWhitelistManager.add(playerName)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + playerName + " &fadded to OP whitelist."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fPlayer &e" + playerName + " &falready in OP whitelist."), false);
        }
        return 1;
    }

    private int handleRemove(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga opwhitelist remove <player>"));
            return 0;
        }

        String playerName = args[2];
        if (OpWhitelistManager.remove(playerName)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + playerName + " &fremoved from OP whitelist."), false);
        } else {
            ctx.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ Player &e" + playerName + " &cnot found in OP whitelist."));
        }
        return 1;
    }

    private void sendUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(MessageUtil.legacy(
                "&c❌ Usage:\\n" +
                "&f/ga opwhitelist on\\n" +
                "&f/ga opwhitelist off\\n" +
                "&f/ga opwhitelist list\\n" +
                "&f/ga opwhitelist add <player>\\n" +
                "&f/ga opwhitelist remove <player>"));
    }
}
