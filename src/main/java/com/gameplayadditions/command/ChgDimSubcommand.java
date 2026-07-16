package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * ChgDimSubcommand — /ga chgdim <world> or /ga chgdim back.
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.ChgDimSubcommand} из MC-Plugin.
 * Использует существующие {@link ChgDimCommand} и {@link DimensionManager}.
 */
public class ChgDimSubcommand implements SubCommand {

    @Override
    public String getName() { return "chgdim"; }

    @Override
    public List<String> getAliases() {
        return List.of("world", "dim");
    }

    @Override
    public String getDescription() { return "Teleport between dimensions."; }

    @Override
    public String getUsage() { return "/ga chgdim <world> | /ga chgdim back"; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        var source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(MessageUtil.legacy("&c❌ Only players can use /ga chgdim!"));
            return 0;
        }

        if (args.length < 2) {
            source.sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga chgdim <world> | /ga chgdim back"));
            return 0;
        }

        String sub = args[1].toLowerCase();

        // /ga chgdim back — return to overworld
        if (sub.equals("back") || sub.equals("return")) {
            return handleTeleportBack(player);
        }

        // /ga chgdim <world> — list worlds or teleport to specific
        if (sub.equals("list")) {
            return handleListDimensions(player);
        }

        // Try to find target world
        ServerLevel targetLevel = findWorld(player, sub);
        if (targetLevel == null) {
            source.sendFailure(MessageUtil.legacy(
                    "&c❌ World &e" + sub + " &cnot found! Use &f/ga chgdim list"));
            return 0;
        }

        // Don't teleport to same world
        if (targetLevel.dimension().location().equals(player.serverLevel().dimension().location())) {
            source.sendFailure(MessageUtil.legacy("&c❌ You are already in that world!"));
            return 0;
        }

        return ChgDimCommand.teleport(player, targetLevel, sub) ? 1 : 0;
    }

    private int handleTeleportBack(ServerPlayer player) {
        if (ChgDimCommand.teleportBack(player)) {
            return 1;
        }
        return 0;
    }

    private int handleListDimensions(ServerPlayer player) {
        player.sendSystemMessage(MessageUtil.legacy(
                "&8═══ &fAvailable Dimensions &8═══"));

        for (var level : player.server.getAllLevels()) {
            String dimName = level.dimension().location().toString();
            String current = level.dimension().location().equals(player.serverLevel().dimension().location())
                    ? " &a<-- You are here" : "";
            String color = level.dimension().location().equals(Level.OVERWORLD.location()) ? "&a" :
                    level.dimension().location().equals(Level.NETHER.location()) ? "&c" :
                    level.dimension().location().equals(Level.END.location()) ? "&5" : "&e";
            player.sendSystemMessage(MessageUtil.legacy(
                    "  " + color + "● &f" + dimName + current));
        }

        player.sendSystemMessage(MessageUtil.legacy(
                "&8═══════════════════════════════════"));
        player.sendSystemMessage(MessageUtil.legacy(
                "&7Use &f/ga chgdim <name> &7to teleport."));
        return 1;
    }

    private ServerLevel findWorld(ServerPlayer player, String name) {
        String lower = name.toLowerCase();
        for (var level : player.server.getAllLevels()) {
            String dimName = level.dimension().location().toString().toLowerCase();
            if (dimName.equals(lower) || dimName.endsWith("/" + lower) || dimName.replace("minecraft:", "").equals(lower)) {
                return level;
            }
        }
        return null;
    }
}
