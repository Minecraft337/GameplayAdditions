package com.gameplayadditions.chat;

import com.gameplayadditions.GameplayAdditionsMod;
import com.gameplayadditions.config.MessagesManager;
import com.gameplayadditions.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Кастомная система чата.
 * Перехватывает ServerChatEvent и форматирует сообщение.
 */
public class ChatManager {

    private static ChatManager instance;
    private boolean enabled;

    public static void init() {
        instance = new ChatManager();
        instance.reloadConfig();
    }

    public static void shutdown() {
        instance = null;
    }

    public static void reload() {
        if (instance != null) {
            instance.reloadConfig();
        }
    }

    private void reloadConfig() {
        this.enabled = com.gameplayadditions.config.ConfigManager.getInstance().getBoolean("chat.enabled", false);
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!enabled) return;
        ServerPlayer player = event.getPlayer();
        Component message = event.getMessage();

        String format = "§7[§f" + player.getName().getString() + "§7] §f" + message.getString();
        event.setMessage(MessageUtil.legacy(format));
    }
}
