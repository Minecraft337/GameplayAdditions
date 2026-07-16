package com.gameplayadditions.command;

import com.gameplayadditions.punish.PunishJoinListener;
import com.gameplayadditions.punish.PunishmentManager;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * PunishCommand — /ga punish (ban|mute|kick|warn|unban|unmute|unwarn|listwarns).
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.PunishSubcommand} из MC-Plugin.
 */
public class PunishCommand implements SubCommand {

    @Override
    public String getName() { return "punish"; }

    @Override
    public String getDescription() { return "Manage player punishments (ban/mute/kick/warn)."; }

    @Override
    public String getUsage() {
        return "/ga punish ban|mute|kick|warn|unban|unmute|unwarn|listwarns|crash <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]";
    }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        if (args.length < 2) {
            sendUsage(context);
            return 0;
        }

        var source = context.getSource();

        if (!source.hasPermission(2)) {
            source.sendFailure(MessageUtil.legacy("&c❌ You don't have permission to use punish commands!"));
            return 0;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "ban" -> handleBan(context, args);
            case "mute" -> handleMute(context, args);
            case "kick" -> handleKick(context, args);
            case "warn" -> handleWarn(context, args);
            case "listwarns" -> handleListWarns(context, args);
            case "unban" -> handleUnban(context, args);
            case "unmute" -> handleUnmute(context, args);
            case "unwarn" -> handleUnwarn(context, args);
            case "crash" -> handleCrash(context, args);
            default -> {
                sendUsage(context);
                yield 0;
            }
        };
    }

    private int handleBan(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 4) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]"));
            return 0;
        }

        PunishArgs parsed = parsePunishArgs(ctx, args, 3);
        if (parsed == null) return 0;

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        String uuid = target != null ? target.getUUID().toString() : "offline:" + targetName.toLowerCase();
        String name = target != null ? target.getName().getString() : targetName;

        String ip = null;
        String hwId = null;
        if (parsed.ip && target != null && target.connection != null) {
            ip = target.connection.getRemoteAddress().toString();
        }
        if (parsed.hw && target != null) {
            String targetIp = target.connection != null ? target.connection.getRemoteAddress().toString() : "0.0.0.0";
            hwId = PunishmentManager.computeHwId(targetIp, name);
        }

        boolean ok = PunishmentManager.punish(
                PunishmentManager.PunishType.BAN, uuid, name, parsed.reason,
                ctx.getSource().getTextName(), parsed.expiresAt, ip, hwId);

        if (!ok) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Failed to ban &e" + name));
            return 0;
        }

        if (target != null && target.isAlive()) {
            target.connection.disconnect(MessageUtil.legacy(
                    "&c⛔ You have been banned!&r\\n&7Reason: &f" + parsed.reason + "\\n&8By: " + ctx.getSource().getTextName()));
        }

        String scope = parsed.ip ? " [IP]" : parsed.hw ? " [HW]" : "";
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fPlayer &e" + name + " &fhas been banned." + scope), false);
        return 1;
    }

    private int handleMute(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 4) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish mute <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]"));
            return 0;
        }

        PunishArgs parsed = parsePunishArgs(ctx, args, 3);
        if (parsed == null) return 0;

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        String uuid = target != null ? target.getUUID().toString() : "offline:" + targetName.toLowerCase();
        String name = target != null ? target.getName().getString() : targetName;

        String ip = null;
        String hwId = null;
        if (parsed.ip && target != null && target.connection != null) {
            ip = target.connection.getRemoteAddress().toString();
        }
        if (parsed.hw && target != null) {
            String targetIp = target.connection != null ? target.connection.getRemoteAddress().toString() : "0.0.0.0";
            hwId = PunishmentManager.computeHwId(targetIp, name);
        }

        boolean ok = PunishmentManager.punish(
                PunishmentManager.PunishType.MUTE, uuid, name, parsed.reason,
                ctx.getSource().getTextName(), parsed.expiresAt, ip, hwId);

        if (!ok) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Failed to mute &e" + name));
            return 0;
        }

        if (target != null && target.isAlive()) {
            PunishmentManager.PunishmentRecord muteRecord = PunishmentManager.getActiveMute(uuid, ip, hwId);
            if (muteRecord != null) {
                PunishJoinListener.addMuteCache(target, muteRecord);
            }
            target.sendSystemMessage(MessageUtil.legacy(
                    "&c🔇 You have been muted!\\n&7Reason: &f" + parsed.reason + "\\n&8By: " + ctx.getSource().getTextName()));
        }

        String scope = parsed.ip ? " [IP]" : parsed.hw ? " [HW]" : "";
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fPlayer &e" + name + " &fhas been muted." + scope), false);
        return 1;
    }

    private int handleKick(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 4) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish kick <player> <reason>"));
            return 0;
        }

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Player &e" + targetName + " &cnot found!"));
            return 0;
        }

        StringBuilder reason = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-ip") || args[i].equalsIgnoreCase("-hw")) continue;
            if (reason.length() > 0) reason.append(" ");
            reason.append(args[i]);
        }

        String reasonStr = reason.toString().trim();
        if (reasonStr.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ You must specify a reason!"));
            return 0;
        }

        PunishmentManager.kickPlayer(target, reasonStr, ctx.getSource().getTextName());
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fPlayer &e" + target.getName().getString() + " &fhas been kicked."), false);
        return 1;
    }

    private int handleWarn(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 4) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish warn <player> <reason> [-time:<N>s|m|h|d] [-permanent]"));
            return 0;
        }

        PunishArgs parsed = parsePunishArgs(ctx, args, 3);
        if (parsed == null) return 0;

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        String uuid = target != null ? target.getUUID().toString() : "offline:" + targetName.toLowerCase();
        String name = target != null ? target.getName().getString() : targetName;

        boolean ok = PunishmentManager.warn(uuid, name, parsed.reason,
                ctx.getSource().getTextName(), parsed.expiresAt);

        if (!ok) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Failed to warn &e" + name));
            return 0;
        }

        if (target != null && target.isAlive()) {
            target.sendSystemMessage(MessageUtil.legacy(
                    "&e⚠ You have been warned!\\n&7Reason: &f" + parsed.reason + "\\n&8By: " + ctx.getSource().getTextName()));
        }

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fPlayer &e" + name + " &fhas been warned."), false);
        return 1;
    }

    private int handleListWarns(CommandContext<CommandSourceStack> ctx, String[] args) {
        String targetName;
        if (args.length < 3) {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish listwarns <player>"));
                return 0;
            }
            targetName = player.getName().getString();
        } else {
            targetName = args[2];
        }

        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        String uuid = target != null ? target.getUUID().toString() : "offline:" + targetName.toLowerCase();

        List<PunishmentManager.WarnRecord> warns = PunishmentManager.getActiveWarns(uuid);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&8═══ &fWarns: &e" + targetName + " &8═══"), false);

        if (warns.isEmpty()) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy("  &8(no active warns)"), false);
        } else {
            for (PunishmentManager.WarnRecord w : warns) {
                ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                        "  &f#" + w.id + ". &7" + w.reason +
                        "\\n    &8By: " + w.warnedBy), false);
            }
        }
        return 1;
    }

    private int handleUnban(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish unban <player>"));
            return 0;
        }

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        String uuid = target != null ? target.getUUID().toString() : "offline:" + targetName.toLowerCase();

        boolean ok = PunishmentManager.unpunishByType(PunishmentManager.PunishType.BAN, uuid);
        if (ok) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + targetName + " &fhas been unbanned."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fNo active ban found for &e" + targetName), false);
        }
        return 1;
    }

    private int handleUnmute(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish unmute <player>"));
            return 0;
        }

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        String uuid = target != null ? target.getUUID().toString() : "offline:" + targetName.toLowerCase();

        if (target != null) {
            PunishJoinListener.removeMuteCache(target);
            target.sendSystemMessage(MessageUtil.legacy("&a🔊 You have been unmuted!"));
        }

        boolean ok = PunishmentManager.unpunishByType(PunishmentManager.PunishType.MUTE, uuid);
        if (ok) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + targetName + " &fhas been unmuted."), false);
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fNo active mute found for &e" + targetName), false);
        }
        return 1;
    }

    private int handleUnwarn(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 5) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish unwarn <player> <reason> <warnId>"));
            return 0;
        }

        String targetName = args[2];
        String reason = args[3];
        int warnId;
        try {
            warnId = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Invalid warn ID!"));
            return 0;
        }

        boolean ok = PunishmentManager.removeWarnById(warnId);
        if (ok) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fWarn #" + warnId + " for &e" + targetName + " &fremoved.\\n&7Reason: " + reason), false);

            var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
            if (target != null && target.isAlive()) {
                target.sendSystemMessage(MessageUtil.legacy(
                        "&a✔ &fYour warn #" + warnId + " has been removed.\\n&7Reason: " + reason));
            }
        } else {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&e⚠ &fNo active warn with ID &e" + warnId + " &ffound."), false);
        }
        return 1;
    }

    private int handleCrash(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga punish crash <player>"));
            return 0;
        }

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Player &e" + targetName + " &cnot found!"));
            return 0;
        }

        // Crash the player using excessive particles
        var level = target.serverLevel();
        var pos = target.position();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                pos.x, pos.y, pos.z, 999999, 0, 0, 0, 0);

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fPlayer &e" + target.getName().getString() + " &fhas been crashed."), false);
        return 1;
    }

    // =========================
    // FLAG PARSING
    // =========================

    private static class PunishArgs {
        String reason;
        long expiresAt;
        boolean isPermanent;
        boolean ip;
        boolean hw;
    }

    private PunishArgs parsePunishArgs(CommandContext<CommandSourceStack> ctx, String[] args, int startIndex) {
        PunishArgs result = new PunishArgs();
        StringBuilder reasonBuilder = new StringBuilder();
        boolean hasTime = false;
        boolean hasPermanent = false;
        boolean hasIp = false;
        boolean hasHw = false;

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];

            if (arg.toLowerCase().startsWith("-time:")) {
                String timeVal = arg.substring(6);
                if (timeVal.isEmpty()) {
                    ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ -time flag requires a value (e.g. -time:30m)"));
                    return null;
                }
                result.expiresAt = parseTime(timeVal);
                if (result.expiresAt == 0) {
                    ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Invalid time format! Use -time:<N>s|m|h|d"));
                    return null;
                }
                hasTime = true;
            } else if (arg.equalsIgnoreCase("-permanent")) {
                hasPermanent = true;
                result.expiresAt = 0;
            } else if (arg.equalsIgnoreCase("-ip")) {
                hasIp = true;
            } else if (arg.equalsIgnoreCase("-hw")) {
                hasHw = true;
            } else {
                if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
                reasonBuilder.append(arg);
            }
        }

        if (!hasTime && !hasPermanent) {
            ctx.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ You must specify either &f-time:<N>s|m|h|d &cor &f-permanent&c."));
            return null;
        }
        if (hasTime && hasPermanent) {
            ctx.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ Flags -time and -permanent cannot be used together!"));
            return null;
        }
        if (reasonBuilder.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ You must specify a reason!"));
            return null;
        }

        result.reason = reasonBuilder.toString().trim();
        result.ip = hasIp;
        result.hw = hasHw;
        result.isPermanent = hasPermanent;
        return result;
    }

    private long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        char unit = timeStr.charAt(timeStr.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
        } catch (NumberFormatException e) {
            return 0;
        }
        return switch (unit) {
            case 's' -> System.currentTimeMillis() + amount * 1000;
            case 'm' -> System.currentTimeMillis() + amount * 60 * 1000;
            case 'h' -> System.currentTimeMillis() + amount * 60 * 60 * 1000;
            case 'd' -> System.currentTimeMillis() + amount * 24 * 60 * 60 * 1000;
            default -> 0;
        };
    }

    private void sendUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(MessageUtil.legacy(
                "&c❌ Usage:\\n" +
                "&f/ga punish ban <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]\\n" +
                "&f/ga punish mute <player> <reason> [-time:<N>s|m|h|d] [-permanent] [-ip] [-hw]\\n" +
                "&f/ga punish kick <player> <reason>\\n" +
                "&f/ga punish warn <player> <reason> [-time:<N>s|m|h|d] [-permanent]\\n" +
                "&f/ga punish listwarns [player]\\n" +
                "&f/ga punish unban <player>\\n" +
                "&f/ga punish unmute <player>\\n" +
                "&f/ga punish unwarn <player> <reason> <warnId>"));
    }
}
