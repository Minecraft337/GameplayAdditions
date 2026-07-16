package com.gameplayadditions.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * PlayerResolver — утилита для безопасного поиска игроков на сервере.
 * <p>
 * Заменяет паттерн "Bukkit.getPlayer(uuid)" в портированных фичах.
 * Использует внутренние O(1) Map-структуры списка игроков MinecraftServer.
 * <p>
 * Все методы потокобезопасны и возвращают {@code null}, если либо сервер ещё
 * не запущен, либо игрок оффлайн. Исключений не бросают.
 */
public final class PlayerResolver {

    private PlayerResolver() {}

    /** Текущий запущенный сервер или {@code null}, если сервер ещё/уже не работает. */
    public static MinecraftServer currentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** Есть ли активный сервер (без NPE при доступе). */
    public static boolean isServerUp() {
        return currentServer() != null;
    }

    /**
     * Найти онлайн-игрока по его UUID.
     *
     * @return {@link ServerPlayer} или {@code null}, если игрок оффлайн/сервер не запущен.
     */
    public static ServerPlayer getPlayer(UUID uuid) {
        if (uuid == null) return null;
        MinecraftServer server = currentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }

    /**
     * Найти онлайн-игрока по точному нику.
     * Нечувствителен к регистру НЕ делается — Minecraft ищет 1:1.
     */
    public static ServerPlayer getPlayerByName(String name) {
        if (name == null || name.isBlank()) return null;
        MinecraftServer server = currentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayerByName(name);
    }

    /** Все онлайн-игроки (immutable snapshot). */
    public static java.util.List<ServerPlayer> allOnline() {
        MinecraftServer server = currentServer();
        if (server == null) return java.util.Collections.emptyList();
        return java.util.Collections.unmodifiableList(server.getPlayerList().getPlayers());
    }
}
