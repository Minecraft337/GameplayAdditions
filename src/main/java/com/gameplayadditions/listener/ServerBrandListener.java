package com.gameplayadditions.listener;

import com.gameplayadditions.GameplayAdditionsMod;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * ServerBrandListener — подмена brand сервера.
 * Портирован из MC-Plugin.
 */
public class ServerBrandListener {

    private static final String DEFAULT_SPOOFED_BRAND = "Paper";

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            scheduleBrandSpoof(player);
        }
    }

    private void scheduleBrandSpoof(ServerPlayer player) {
        if (player.hasPermissions(2)) return;
        try {
            ClientboundCustomPayloadPacket packet = new ClientboundCustomPayloadPacket(
                new BrandPayload(DEFAULT_SPOOFED_BRAND)
            );
            player.connection.send(packet);
        } catch (Exception e) {
            ConsoleLogger.warn("[BrandSpoof] Failed for " + player.getName().getString() + ": " + e.getMessage());
        }
    }
}
