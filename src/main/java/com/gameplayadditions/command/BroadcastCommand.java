package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * BroadcastCommand — /ga bc <message> [-clean].
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.BroadcastSubcommand} из MC-Plugin.
 */
public class BroadcastCommand implements SubCommand {

    @Override
    public String getName() { return "bc"; }

    @Override
    public java.util.List<String> getAliases() {
        return java.util.List.of("broadcast", "say");
    }

    @Override
    public String getDescription() { return "Broadcast a message to all players."; }

    @Override
    public String getUsage() { return "/ga bc <message> [-clean]"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        if (args.length < 2) {
            context.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga bc <message> [-clean]"));
            return 0;
        }

        boolean clean = false;
        StringBuilder messageBuilder = new StringBuilder();

        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("-clean")) {
                clean = true;
                continue;
            }
            if (messageBuilder.length() > 0) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }

        String message = messageBuilder.toString().trim();
        if (message.isEmpty()) {
            context.getSource().sendFailure(MessageUtil.legacy("&c❌ Message cannot be empty!"));
            return 0;
        }

        String prefix = "&8[&fServer&8/&fInfo&8] &7";
        String fullMessage = clean ? message : prefix + message;
        var component = MessageUtil.legacy(fullMessage);

        // Broadcast to all players
        var server = context.getSource().getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }

        // Log to console
        server.sendSystemMessage(component);

        return 1;
    }
}
