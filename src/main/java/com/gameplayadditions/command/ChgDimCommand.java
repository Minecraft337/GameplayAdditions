package com.gameplayadditions.command;

import com.gameplayadditions.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.UUID;

/**
 * Обрабатывает команду /ga chgdim — телепортацию между мирами.
 */
public class ChgDimCommand {

    private static final HashMap<UUID, Long> cooldowns = new HashMap<>();

    public static boolean teleport(ServerPlayer player, ServerLevel targetLevel, String worldName) {
        UUID playerUuid = player.getUUID();
        long now = System.currentTimeMillis() / 1000;
        int cooldownSecs = 10;

        if (cooldowns.containsKey(playerUuid)) {
            long lastUse = cooldowns.get(playerUuid);
            long elapsed = now - lastUse;
            if (elapsed < cooldownSecs) {
                long remaining = cooldownSecs - elapsed;
                player.sendSystemMessage(Component.literal("§cPlease wait " + remaining + " seconds before using this again!"));
                return true;
            }
        }

        // Сохраняем текущую позицию
        DimensionManager.saveReturnLocation(player);

        BlockPos spawnPos = targetLevel.getSharedSpawnPos();
        player.teleportTo(targetLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
        cooldowns.put(playerUuid, now);

        player.sendSystemMessage(Component.literal("§a✔ §fTeleported to §e" + worldName));

        return true;
    }

    public static boolean teleportBack(ServerPlayer player) {
        if (!DimensionManager.hasReturnLocation(player)) {
            player.sendSystemMessage(Component.literal("§c❌ No saved return point!"));
            return true;
        }

        ServerLevel returnLevel = DimensionManager.getReturnLevel(player, player.server);
        if (returnLevel == null) {
            player.sendSystemMessage(Component.literal("§c❌ Error: Return point corrupted!"));
            DimensionManager.removeReturnLocation(player);
            return true;
        }

        BlockPos spawnPos = returnLevel.getSharedSpawnPos();
        player.teleportTo(returnLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
        DimensionManager.removeReturnLocation(player);
        player.sendSystemMessage(Component.literal("§a✔ §fYou have returned!"));

        return true;
    }

    public static void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
