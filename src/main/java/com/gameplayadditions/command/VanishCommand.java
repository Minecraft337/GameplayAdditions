package com.gameplayadditions.command;

import com.gameplayadditions.mechanics.vanish.VanishManager;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * VanishCommand — /ga vanish <nick>.
 * <p>
 * Порт {@code MiscSubcommand.vanish()} из MC-Plugin.
 * Требует OP 2 (permission level).
 */
public class VanishCommand implements SubCommand {

    @Override
    public String getName() { return "vanish"; }

    @Override
    public String getDescription() { return "Toggle vanish for a player."; }

    @Override
    public String getUsage() { return "/ga vanish <nick>"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        var source = context.getSource();

        if (args.length < 2) {
            source.sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga vanish <nick>"));
            return 0;
        }

        String targetName = args[1];
        var server = source.getServer();

        // Ищем игрока онлайн
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            // Если не онлайн — пробуем найти по имени (оффлайн)
            source.sendFailure(MessageUtil.legacy("&c❌ Player &e" + targetName + " &cnot found or offline!"));
            source.sendSuccess(() -> MessageUtil.legacy("&7Vanish for offline players will be supported later."), false);
            return 0;
        }

        UUID uuid = target.getUUID();
        boolean wasVanished = VanishManager.isVanished(uuid);
        VanishManager.toggleVanish(uuid);
        boolean isVanished = VanishManager.isVanished(uuid);

        if (isVanished) {
            source.sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + targetName + " &fis now hidden (vanished)."), false);
        } else {
            source.sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fPlayer &e" + targetName + " &fis no longer hidden."), false);
        }

        return 1;
    }
}
