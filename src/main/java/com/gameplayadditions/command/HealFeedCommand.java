package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import java.util.List;

/**
 * HealFeedCommand — /ga heal <player> and /ga feed <player>.
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.HealFeedSubcommand} из MC-Plugin.
 */
public class HealFeedCommand implements SubCommand {

    @Override
    public String getName() { return "heal"; }

    @Override
    public List<String> getAliases() {
        return List.of("feed");
    }

    @Override
    public String getDescription() { return "Heal or feed a player."; }

    @Override
    public String getUsage() { return "/ga heal <player> | /ga feed <player>"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        if (args.length < 2) {
            context.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga heal <player> | /ga feed <player>"));
            return 0;
        }

        String sub = args[0].toLowerCase();
        String targetName = args[1];

        var target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            context.getSource().sendFailure(MessageUtil.legacy("&c❌ Player &e" + targetName + " &cnot found!"));
            return 0;
        }

        if (sub.equals("heal")) {
            return handleHeal(context, target);
        } else if (sub.equals("feed")) {
            return handleFeed(context, target);
        }

        context.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga heal <player> | /ga feed <player>"));
        return 0;
    }

    private int handleHeal(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        target.setHealth(target.getMaxHealth());

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fPlayer &e" + target.getName().getString() + " &fhas been healed."), false);

        if (ctx.getSource().getEntity() != target) {
            target.sendSystemMessage(MessageUtil.legacy(
                    "&a✔ &fYou have been healed by &e" + ctx.getSource().getTextName() + "&f."));
        }
        return 1;
    }

    private int handleFeed(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        FoodData foodData = target.getFoodData();
        foodData.setFoodLevel(20);
        foodData.setSaturation(20);
        foodData.setExhaustion(0);

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fPlayer &e" + target.getName().getString() + " &fhas been fed."), false);

        if (ctx.getSource().getEntity() != target) {
            target.sendSystemMessage(MessageUtil.legacy(
                    "&a✔ &fYou have been fed by &e" + ctx.getSource().getTextName() + "&f."));
        }
        return 1;
    }
}
