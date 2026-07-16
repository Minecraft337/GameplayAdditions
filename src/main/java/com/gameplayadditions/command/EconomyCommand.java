package com.gameplayadditions.command;

import com.gameplayadditions.economy.EconomyManager;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

/**
 * EconomyCommand — /ga money (give|list|remove|set) <player> [currency] [amount]
 * <p>
 * Порт {@code com.mcplugin.command.subcommands.EconomySubcommand} из MC-Plugin.
 */
public class EconomyCommand implements SubCommand {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.##");

    @Override
    public String getName() { return "money"; }

    @Override
    public List<String> getAliases() {
        return List.of("pay", "balance", "bal");
    }

    @Override
    public String getDescription() {
        return "Manage player balances.";
    }

    @Override
    public String getUsage() {
        return "/ga money give|list|remove|set <player> [currency] [amount] | /ga pay <player> <amount>";
    }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        if (args.length < 2) {
            // Если игрок — показать свой баланс
            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                return showBalances(context, player.getUUID(), player.getName().getString());
            }
            context.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f" + getUsage()));
            return 0;
        }

        String sub = args[0].toLowerCase();

        // /ga pay <player> <amount>
        if (sub.equals("pay")) {
            return handlePay(context, args);
        }
        // /ga balance [player]
        if (sub.equals("balance") || sub.equals("bal")) {
            return handleBalance(context, args);
        }

        // /ga money give|list|remove|set
        if (args.length < 2) {
            context.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f" + getUsage()));
            return 0;
        }

        String action = args[1].toLowerCase();
        return switch (action) {
            case "give" -> handleGive(context, args);
            case "list" -> handleList(context, args);
            case "remove" -> handleRemove(context, args);
            case "set" -> handleSet(context, args);
            default -> {
                context.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f" + getUsage()));
                yield 0;
            }
        };
    }

    private int handlePay(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sender)) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Only players can use /ga pay!"));
            return 0;
        }

        if (args.length < 3) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga pay <player> <amount>"));
            return 0;
        }

        String targetName = args[1];
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Invalid amount!"));
            return 0;
        }

        if (amount <= 0) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Amount must be positive!"));
            return 0;
        }

        var server = ctx.getSource().getServer();
        var target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Player &e" + targetName + " &cnot found!"));
            return 0;
        }

        UUID senderUuid = sender.getUUID();
        if (!EconomyManager.getInstance().removeBalance(senderUuid, amount)) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ You don't have enough money!"));
            return 0;
        }

        EconomyManager.getInstance().addBalance(target.getUUID(), amount);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fYou paid &e" + FMT.format(amount) + " &fto &e" + target.getName().getString()), false);
        target.sendSystemMessage(MessageUtil.legacy(
                "&a+ &e" + FMT.format(amount) + " &freceived from &e" + sender.getName().getString()));
        return 1;
    }

    private int handleBalance(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Only players can check balance!"));
            return 0;
        }

        UUID uuid = player.getUUID();
        String name = player.getName().getString();

        if (args.length >= 2) {
            var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(args[1]);
            if (target != null) {
                uuid = target.getUUID();
                name = target.getName().getString();
            }
        }

        return showBalances(ctx, uuid, name);
    }

    private int showBalances(CommandContext<CommandSourceStack> ctx, UUID uuid, String name) {
        var balances = EconomyManager.getInstance().getAllBalances(uuid);

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(""), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy("  &6💰 &e" + name + " &7— Balances"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy("  &8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);

        if (balances.isEmpty()) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy("  &7No balances found."), false);
        } else {
            for (var entry : balances.entrySet()) {
                ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                        "  &7• &f" + entry.getKey() + "&7: &6" + FMT.format(entry.getValue())), false);
            }
        }

        ctx.getSource().sendSuccess(() -> MessageUtil.legacy("  &8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
        return 1;
    }

    private int handleGive(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 4) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga money give <player> [currency] <amount>"));
            return 0;
        }

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Player &e" + targetName + " &cnot found!"));
            return 0;
        }

        UUID uuid = target.getUUID();
        double amount;
        String currency;

        try {
            amount = Double.parseDouble(args[3]);
            currency = EconomyManager.getInstance().getPrimaryCurrency();
        } catch (NumberFormatException e) {
            currency = args[3].toLowerCase();
            if (args.length < 5) {
                ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga money give <player> <currency> <amount>"));
                return 0;
            }
            try {
                amount = Double.parseDouble(args[4]);
            } catch (NumberFormatException ex) {
                ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Invalid amount!"));
                return 0;
            }
        }

        if (amount <= 0) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Amount must be positive!"));
            return 0;
        }

        EconomyManager.getInstance().addBalance(uuid, currency, amount);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fGave &e" + FMT.format(amount) + " " + currency + " &fto &e" + target.getName().getString()), false);
        target.sendSystemMessage(MessageUtil.legacy(
                "&a+ &e" + FMT.format(amount) + " " + currency + " &freceived!"));
        return 1;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 3) {
            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                return showBalances(ctx, player.getUUID(), player.getName().getString());
            }
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga money list <player>"));
            return 0;
        }

        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(args[2]);
        if (target == null) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Player &e" + args[2] + " &cnot found!"));
            return 0;
        }

        return showBalances(ctx, target.getUUID(), target.getName().getString());
    }

    private int handleRemove(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 4) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga money remove <player> [currency] <amount>"));
            return 0;
        }

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Player &e" + targetName + " &cnot found!"));
            return 0;
        }

        UUID uuid = target.getUUID();
        double amount;
        String currency;

        try {
            amount = Double.parseDouble(args[3]);
            currency = EconomyManager.getInstance().getPrimaryCurrency();
        } catch (NumberFormatException e) {
            currency = args[3].toLowerCase();
            if (args.length < 5) {
                ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga money remove <player> <currency> <amount>"));
                return 0;
            }
            try {
                amount = Double.parseDouble(args[4]);
            } catch (NumberFormatException ex) {
                ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Invalid amount!"));
                return 0;
            }
        }

        if (amount <= 0) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Amount must be positive!"));
            return 0;
        }

        boolean success = EconomyManager.getInstance().removeBalance(uuid, currency, amount);
        if (success) {
            ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                    "&a✔ &fRemoved &e" + FMT.format(amount) + " " + currency + " &ffrom &e" + target.getName().getString()), false);
        } else {
            double current = EconomyManager.getInstance().getBalance(uuid, currency);
            ctx.getSource().sendFailure(MessageUtil.legacy(
                    "&c❌ &e" + target.getName().getString() + " &conly has &6" + FMT.format(current) + " " + currency));
        }
        return 1;
    }

    private int handleSet(CommandContext<CommandSourceStack> ctx, String[] args) {
        if (args.length < 4) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga money set <player> [currency] <amount>"));
            return 0;
        }

        String targetName = args[2];
        var target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Player &e" + targetName + " &cnot found!"));
            return 0;
        }

        UUID uuid = target.getUUID();
        double amount;
        String currency;

        try {
            amount = Double.parseDouble(args[3]);
            currency = EconomyManager.getInstance().getPrimaryCurrency();
        } catch (NumberFormatException e) {
            currency = args[3].toLowerCase();
            if (args.length < 5) {
                ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Usage: &f/ga money set <player> <currency> <amount>"));
                return 0;
            }
            try {
                amount = Double.parseDouble(args[4]);
            } catch (NumberFormatException ex) {
                ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Invalid amount!"));
                return 0;
            }
        }

        if (amount < 0) {
            ctx.getSource().sendFailure(MessageUtil.legacy("&c❌ Amount cannot be negative!"));
            return 0;
        }

        EconomyManager.getInstance().setBalance(uuid, currency, amount);
        ctx.getSource().sendSuccess(() -> MessageUtil.legacy(
                "&a✔ &fSet &e" + target.getName().getString() + "&f's &6" + currency + " &fbalance to &e" + FMT.format(amount)), false);
        return 1;
    }
}
