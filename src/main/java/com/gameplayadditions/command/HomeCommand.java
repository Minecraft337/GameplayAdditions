package com.gameplayadditions.command;

import com.gameplayadditions.mechanics.homes.HomeDatabase;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * HomeCommand — обрабатывает /ga home/sethome/delhome/listhomes.
 * <p>
 * Порт {@code com.mcplugin.command.home.HomeCommand} из MC-Plugin.
 * Работает в "legit" режиме (показывает координаты вместо телепорта).
 */
public class HomeCommand implements SubCommand {

    @Override
    public String getName() { return "home"; }

    @Override
    public List<String> getAliases() {
        return List.of("sethome", "delhome", "listhomes");
    }

    @Override
    public String getDescription() { return "Manage your home points."; }

    @Override
    public String getUsage() { return "/ga home <name> | /ga sethome <name> | /ga delhome <name> | /ga listhomes"; }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        if (args.length < 1) return 0;
        var source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(MessageUtil.legacy("&c❌ Only players can use home commands!"));
            return 0;
        }

        String sub = args[0].toLowerCase();
        UUID uuid = player.getUUID();

        return switch (sub) {
            case "sethome" -> executeSetHome(player, uuid, args);
            case "delhome" -> executeDelHome(player, uuid, args);
            case "listhomes" -> executeListHomes(player, uuid);
            default -> executeGetHome(player, uuid, args);
        };
    }

    private int executeSetHome(ServerPlayer player, UUID uuid, String[] args) {
        if (args.length < 2) {
            sourceSend(player, "&c❌ Usage: &f/ga sethome <name>");
            return 0;
        }

        String name = args[1].trim();
        if (name.length() < HomeDatabase.getNameMin() || name.length() > HomeDatabase.getNameMax()) {
            sourceSend(player, "&c❌ Home name must be " + HomeDatabase.getNameMin() + "-" + HomeDatabase.getNameMax() + " chars!");
            return 0;
        }

        if (!HomeDatabase.homeExists(uuid, name) && HomeDatabase.countHomes(uuid) >= HomeDatabase.getMaxHomes()) {
            sourceSend(player, "&c❌ You've reached the limit of " + HomeDatabase.getMaxHomes() + " homes!");
            return 0;
        }

        var pos = player.blockPosition();
        var level = player.serverLevel();

        if (HomeDatabase.saveHome(uuid, name, level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(), player.getYRot(), player.getXRot())) {
            sourceSend(player, "&a✔ &fHome &e" + name + " &fsaved!");
            sourceSend(player, "&7World: &f" + level.dimension().location());
            sourceSend(player, "&7X: &f" + pos.getX() + " &7Y: &f" + pos.getY() + " &7Z: &f" + pos.getZ());
        } else {
            sourceSend(player, "&c❌ Error saving home!");
        }
        return 1;
    }

    private int executeGetHome(ServerPlayer player, UUID uuid, String[] args) {
        if (args.length < 2) {
            sourceSend(player, "&c❌ Usage: &f/ga home <name>");
            return 0;
        }

        String name = args[1].trim();
        var home = HomeDatabase.getHome(uuid, name);

        if (home == null) {
            sourceSend(player, "&eℹ Home &e" + name + " &fnot found.");
            return 0;
        }

        sourceSend(player, "&6═══════════════════════════════════");
        sourceSend(player, "&6  &e✦ &fHome: &e" + home.homeName());
        sourceSend(player, "&6═══════════════════════════════════");
        sourceSend(player, "&7World: &f" + home.world());
        sourceSend(player, "&7X: &f" + String.format("%.1f", home.x()));
        sourceSend(player, "&7Y: &f" + String.format("%.1f", home.y()));
        sourceSend(player, "&7Z: &f" + String.format("%.1f", home.z()));
        sourceSend(player, "&6═══════════════════════════════════");
        sourceSend(player, "&e⚠ &7Legit mode — no teleport. Travel manually.");
        return 1;
    }

    private int executeDelHome(ServerPlayer player, UUID uuid, String[] args) {
        if (args.length < 2) {
            sourceSend(player, "&c❌ Usage: &f/ga delhome <name>");
            return 0;
        }

        String name = args[1].trim();
        if (!HomeDatabase.homeExists(uuid, name)) {
            sourceSend(player, "&c❌ Home &e" + name + " &cnot found!");
            return 0;
        }

        if (HomeDatabase.deleteHome(uuid, name)) {
            sourceSend(player, "&a✔ &fHome &e" + name + " &fdeleted.");
        } else {
            sourceSend(player, "&c❌ Error deleting home!");
        }
        return 1;
    }

    private int executeListHomes(ServerPlayer player, UUID uuid) {
        var homes = HomeDatabase.listHomes(uuid);

        if (homes.isEmpty()) {
            sourceSend(player, "&eℹ &fYou have no saved homes. Use &e/ga sethome <name>");
            return 1;
        }

        int used = homes.size();
        sourceSend(player, "&6═══════════════════════════════════");
        sourceSend(player, "&6  &e✦ &fYour Homes &7(" + used + "/" + HomeDatabase.getMaxHomes() + ")");
        sourceSend(player, "&6═══════════════════════════════════");

        for (int i = 0; i < homes.size(); i++) {
            var h = homes.get(i);
            sourceSend(player, "&7┌─ &e" + (i + 1) + ". &f" + h.homeName());
            sourceSend(player, "&7│ &7World: &f" + h.world());
            sourceSend(player, "&7│ &7X: &f" + String.format("%.1f", h.x()) +
                    " &7Y: &f" + String.format("%.1f", h.y()) +
                    " &7Z: &f" + String.format("%.1f", h.z()));
            if (i < homes.size() - 1) sourceSend(player, "&7│");
        }

        sourceSend(player, "&6═══════════════════════════════════");
        return 1;
    }

    private void sourceSend(ServerPlayer player, String msg) {
        player.sendSystemMessage(MessageUtil.legacy(msg));
    }
}
