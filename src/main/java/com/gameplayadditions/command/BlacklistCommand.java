package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.whitelist.BlacklistManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;

/**
 * BlacklistCommand — /ga blacklist (on|off|list|add|remove).
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.BlacklistSubcommand} из MC-Plugin.
 */
public class BlacklistCommand implements SubCommand {

    @Override
    public String getName() { return "blacklist"; }

    @Override
    public String getDescription() { return "Manage the custom blacklist."; }

    @Override
    public String getUsage() { return "/ga blacklist on|off|list|add <player>|remove <player>"; }

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
        if (BlacklistManager.setEnabled(true)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fBlacklist &aENABLED&f."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fBlacklist is already enabled."), false);
        }
        return 1;
    }

    private int handleOff(CommandContext<CommandSourceStack> ctx) {
        if (BlacklistManager.setEnabled(false)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fBlacklist &cDISABLED&f."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fBlacklist is already disabled."), false);
        }
        return 1;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        List<String> names = BlacklistManager.getBlacklistNames();
        boolean isOn = BlacklistManager.isEnabled();
        String status = isOn ? "&aENABLED" : "&cDISABLED";

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&8═══ &fBlacklist &8═══"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&7Status: " + status), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&7Players (&f" + names.size() + "&7):"), false);

        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy("  &8(empty)"), false);
        } else {
            for (String name : names) {
                var player = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                String statusStr = player != null ? "&c●" : "&8●";
                ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                        "  " + statusStr + " &f" + name), false);
            }
        }
        return 1;
    }

    private int handleAdd(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga blacklist add <player>"));
            return 0;
        }

        String playerName = args[2];
        if (BlacklistManager.add(playerName)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + playerName + " &fadded to blacklist."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fPlayer &e" + playerName + " &falready in blacklist."), false);
        }
        return 1;
    }

    private int handleRemove(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga blacklist remove <player>"));
            return 0;
        }

        String playerName = args[2];
        if (BlacklistManager.remove(playerName)) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + playerName + " &fremoved from blacklist."), false);
        } else {
            ctx.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ Player &e" + playerName + " &cnot found in blacklist."));
        }
        return 1;
    }

    private void sendUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(MessageUtil.legacy(
                "&c❌ Usage:\\n" +
                "&f/ga blacklist on\\n" +
                "&f/ga blacklist off\\n" +
                "&f/ga blacklist list\\n" +
                "&f/ga blacklist add <player>\\n" +
                "&f/ga blacklist remove <player>"));
    }
}
