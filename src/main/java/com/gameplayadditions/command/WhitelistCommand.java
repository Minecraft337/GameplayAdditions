package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.whitelist.WhitelistManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * WhitelistCommand — /ga whitelist (on|off|list|add|remove).
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.WhitelistSubcommand} из MC-Plugin.
 */
public class WhitelistCommand implements SubCommand {

    @Override
    public String getName() { return "whitelist"; }

    @Override
    public String getDescription() { return "Manage the custom whitelist."; }

    @Override
    public String getUsage() { return "/ga whitelist on|off|list|add <player>|remove <player>"; }

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
        if (WhitelistManager.setEnabled(true)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fWhitelist &aENABLED&f."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fWhitelist is already enabled."), false);
        }
        return 1;
    }

    private int handleOff(CommandContext<CommandSourceStack> ctx) {
        if (WhitelistManager.setEnabled(false)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fWhitelist &cDISABLED&f."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fWhitelist is already disabled."), false);
        }
        return 1;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        List<String> names = WhitelistManager.getWhitelistNames();
        boolean isOn = WhitelistManager.isEnabled();
        String status = isOn ? "&aENABLED" : "&cDISABLED";

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&8═══ &fWhitelist &8═══"), false);
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
                ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                        "  " + statusStr + " &f" + name), false);
            }
        }
        return 1;
    }

    private int handleAdd(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga whitelist add <player>"));
            return 0;
        }

        String playerName = args[2];
        if (WhitelistManager.add(playerName)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + playerName + " &fadded to whitelist."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fPlayer &e" + playerName + " &falready in whitelist."), false);
        }
        return 1;
    }

    private int handleRemove(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga whitelist remove <player>"));
            return 0;
        }

        String playerName = args[2];
        if (WhitelistManager.remove(playerName)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + playerName + " &fremoved from whitelist."), false);
        } else {
            ctx.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ Player &e" + playerName + " &cnot found in whitelist."));
        }
        return 1;
    }

    private void sendUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(MessageUtil.legacy(
                "&c❌ Usage:\\n" +
                "&f/ga whitelist on\\n" +
                "&f/ga whitelist off\\n" +
                "&f/ga whitelist list\\n" +
                "&f/ga whitelist add <player>\\n" +
                "&f/ga whitelist remove <player>"));
    }
}
