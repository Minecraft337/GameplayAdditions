package com.gameplayadditions.command;

import com.gameplayadditions.server.PowerManager;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * PowerCommand — управление питанием сервера.
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.PowerSubcommand} из MC-Plugin.
 * <pre>
 * /ga power off       — запрос на выключение (игрок → консоль подтверждает)
 * /ga power reboot    — запрос на перезагрузку
 * /ga power confirm   — подтверждение (только консоль)
 * /ga power undo      — отмена запроса
 * </pre>
 */
public class PowerCommand implements SubCommand {

    @Override
    public String getName() { return "power"; }

    @Override
    public String getDescription() { return "Server shutdown/restart management."; }

    @Override
    public String getUsage() { return "/ga power off|reboot|confirm|undo"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; } // OP

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        if (args.length < 2) {
            context.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ Usage: &f/ga power off|reboot|confirm|undo"));
            return 0;
        }

        var pm = PowerManager.getInstance();
        if (pm == null) {
            context.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ Power management not initialized!"));
            return 0;
        }

        return switch (args[1].toLowerCase()) {
            case "off" -> handleOff(context, pm);
            case "reboot" -> handleReboot(context, pm);
            case "confirm" -> handleConfirm(context, pm);
            case "undo" -> handleUndo(context, pm);
            default -> {
                context.getSource().sendFailure(MessageUtil.legacy(
                        "&c❌ Usage: &f/ga power off|reboot|confirm|undo"));
                yield 0;
            }
        };
    }

    private int handleOff(CommandContext<CommandSourceStack> ctx, PowerManager pm) {
        var source = ctx.getSource();

        // Console executes directly
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            pm.executeDirect(false);
            return 1;
        }

        if (!source.hasPermission(2)) {
            source.sendFailure(MessageUtil.legacy("&c❌ You don't have permission!"));
            return 0;
        }

        if (pm.hasPendingRequest()) {
            source.sendFailure(MessageUtil.legacy("&c❌ There is already an active power request."));
            return 0;
        }

        pm.requestStop(player.getScoreboardName(), player.getUUID());
        source.sendSuccess(() -> MessageUtil.legacy(
                "&8[&c⚠&8] &eShutdown initiated. Waiting for console confirmation."), false);

        // Console notification
        var server = source.getServer();
        server.sendSystemMessage(MessageUtil.legacy(
                "&8[&c⚠&8] &eShutdown requested by player &f" + player.getScoreboardName()));
        server.sendSystemMessage(MessageUtil.legacy(
                "&8[&c⚠&8] &eConfirm: &f/ga power confirm  &eCancel: &f/ga power undo"));
        server.sendSystemMessage(MessageUtil.legacy(
                "&8[&c⚠&8] &eRequest auto-cancels after 30 seconds."));

        return 1;
    }

    private int handleReboot(CommandContext<CommandSourceStack> ctx, PowerManager pm) {
        var source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            pm.executeDirect(true);
            return 1;
        }

        if (!source.hasPermission(2)) {
            source.sendFailure(MessageUtil.legacy("&c❌ You don't have permission!"));
            return 0;
        }

        if (pm.hasPendingRequest()) {
            source.sendFailure(MessageUtil.legacy("&c❌ There is already an active power request."));
            return 0;
        }

        pm.requestRestart(player.getScoreboardName(), player.getUUID());
        source.sendSuccess(() -> MessageUtil.legacy(
                "&8[&c⚠&8] &eRestart initiated. Waiting for console confirmation."), false);

        var server = source.getServer();
        server.sendSystemMessage(MessageUtil.legacy(
                "&8[&c⚠&8] &eRestart requested by player &f" + player.getScoreboardName()));
        server.sendSystemMessage(MessageUtil.legacy(
                "&8[&c⚠&8] &eConfirm: &f/ga power confirm  &eCancel: &f/ga power undo"));
        server.sendSystemMessage(MessageUtil.legacy(
                "&8[&c⚠&8] &eRequest auto-cancels after 30 seconds."));

        return 1;
    }

    private int handleConfirm(CommandContext<CommandSourceStack> ctx, PowerManager pm) {
        var source = ctx.getSource();

        // Only console can confirm
        if (source.getEntity() instanceof ServerPlayer) {
            source.sendFailure(MessageUtil.legacy("&c❌ Only console can confirm a power request."));
            return 0;
        }

        if (!pm.hasPendingRequest()) {
            source.sendFailure(MessageUtil.legacy("&c❌ No active power requests."));
            return 0;
        }

        String action = pm.getCurrentRequestType() == PowerManager.RequestType.STOP
                ? "Shutdown" : "Restart";
        String requester = pm.getRequesterName();

        if (pm.confirmRequest()) {
            source.sendSuccess(() -> MessageUtil.legacy(
                    "&8[&a✔&8] &a" + action + " confirmed (request from " + requester + ")."), false);
        } else {
            source.sendFailure(MessageUtil.legacy("&c❌ Error during confirmation."));
        }
        return 1;
    }

    private int handleUndo(CommandContext<CommandSourceStack> ctx, PowerManager pm) {
        var source = ctx.getSource();

        if (!pm.hasPendingRequest()) {
            source.sendFailure(MessageUtil.legacy("&c❌ No active power requests."));
            return 0;
        }

        String undoerName = source.getEntity() instanceof ServerPlayer player
                ? player.getScoreboardName() : "Console";
        String action = pm.undoRequest(undoerName);

        if (action != null) {
            source.sendSuccess(() -> MessageUtil.legacy(
                    "&8[&a✔&8] &a" + action + " cancelled."), false);
        } else {
            source.sendFailure(MessageUtil.legacy("&c❌ Error during cancellation."));
        }
        return 1;
    }
}
