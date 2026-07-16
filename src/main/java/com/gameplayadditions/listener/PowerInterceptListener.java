package com.gameplayadditions.listener;

import com.gameplayadditions.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * PowerInterceptListener — перехват /stop и /restart.
 * Портирован из MC-Plugin.
 */
public class PowerInterceptListener {

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        String command = event.getParseResults().getReader().getString().toLowerCase().trim();
        CommandSourceStack source = event.getParseResults().getContext().getSource();

        boolean isStop = command.equals("/stop") || command.startsWith("/stop ");
        boolean isRestart = command.equals("/restart") || command.startsWith("/restart ") ||
                          command.equals("/minecraft:restart") || command.startsWith("/minecraft:restart ");

        if (isStop) {
            event.setCanceled(true);
            source.sendFailure(Component.literal("§cUse /ga power off instead of /stop"));
        } else if (isRestart) {
            event.setCanceled(true);
            source.sendFailure(Component.literal("§cUse /ga power reboot instead of /restart"));
        }
    }
}
