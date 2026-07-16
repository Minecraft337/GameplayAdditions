package com.gameplayadditions.punish;

import com.gameplayadditions.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Проверяет баны/муты при входе игрока.
 */
public class PunishJoinListener {

    private static final Map<UUID, PunishmentManager.PunishmentRecord> mutedPlayers = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String uuid = player.getUUID().toString();
        String name = player.getName().getString();

        PunishmentManager.PunishmentRecord ban = PunishmentManager.getActiveBan(uuid, "", "");
        if (ban != null) {
            player.connection.disconnect(net.minecraft.network.chat.Component.literal("You are banned!"));
            return;
        }

        PunishmentManager.PunishmentRecord mute = PunishmentManager.getActiveMute(uuid, "", "");
        if (mute != null) {
            if (mute.isExpired()) {
                PunishmentManager.unpunishById(mute.id);
                mutedPlayers.remove(player.getUUID());
            } else {
                mutedPlayers.put(player.getUUID(), mute);
            }
        } else {
            mutedPlayers.remove(player.getUUID());
        }
    }

    public static boolean isMuted(ServerPlayer player) {
        PunishmentManager.PunishmentRecord record = mutedPlayers.get(player.getUUID());
        if (record == null) return false;
        if (record.isExpired()) {
            PunishmentManager.unpunishById(record.id);
            mutedPlayers.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static PunishmentManager.PunishmentRecord getMuteRecord(ServerPlayer player) {
        return mutedPlayers.get(player.getUUID());
    }

    public static void addMuteCache(ServerPlayer player, PunishmentManager.PunishmentRecord record) {
        mutedPlayers.put(player.getUUID(), record);
    }

    public static void removeMuteCache(ServerPlayer player) {
        mutedPlayers.remove(player.getUUID());
    }
}
