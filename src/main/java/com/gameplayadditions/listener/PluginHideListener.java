package com.gameplayadditions.listener;

import com.gameplayadditions.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * PluginHideListener — скрытие плагинов от игроков без прав.
 * Портирован из MC-Plugin.
 */
public class PluginHideListener {

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        String command = event.getParseResults().getReader().getString().toLowerCase().trim();
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) return;

        // Block /pl, /plugins, /version, /ver, /help, /? etc.
        if (command.startsWith("/pl ") || command.equals("/pl") ||
            command.startsWith("/plugins ") || command.equals("/plugins") ||
            command.startsWith("/ver ") || command.equals("/ver") ||
            command.startsWith("/version ") || command.equals("/version") ||
            command.startsWith("/help ") || command.equals("/help")) {

            if (!player.hasPermissions(2)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cError: You don't have permission!"));
            }
        }
    }
}
