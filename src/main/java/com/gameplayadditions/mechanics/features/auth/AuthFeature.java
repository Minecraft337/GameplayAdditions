package com.gameplayadditions.mechanics.features.auth;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthFeature — порт {@code AuthManager + AuthConfig + AuthPlayerState + AuthRateLimiter + AuthTimeoutManager} из MC-Plugin.
 * <p>
 * Базовая аутентификация игроков: проверка при входе, таймаут, 
 * ограничение частоты запросов, отслеживание попыток.
 */
public class AuthFeature extends AbstractFeature {

    // ─── AuthConfig ────────────────────────────────────────────────────
    private boolean enabled = true;
    private boolean ipCheckEnabled = true;
    private boolean dupNameCheckEnabled = true;
    private int sessionDurationMinutes = 60;
    private int minPasswordLength = 1;
    private int maxPasswordLength = 32;
    private int loginTimeoutSeconds = 60;
    private int maxWrongAttempts = 5;
    private int requestCooldownSeconds = 5;
    private int maxAccountsPerIp = 3;

    // ─── AuthPlayerState ───────────────────────────────────────────────
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingAuth = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> wrongAttempts = new ConcurrentHashMap<>();

    // ─── AuthRateLimiter ────────────────────────────────────────────────
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();

    // ─── AuthTimeoutManager ─────────────────────────────────────────────
    private final Map<UUID, Long> loginTimeoutStart = new ConcurrentHashMap<>();

    public AuthFeature() {
    }

    @Override
    public String getName() {
        return "Auth";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[Auth] Feature initialized. enabled=" + enabled +
                ", timeout=" + loginTimeoutSeconds + "s, maxAttempts=" + maxWrongAttempts);
    }

    // ─════════════════════════════════════════════════════════════════════
    //  API: State management
    // ─════════════════════════════════════════════════════════════════════

    public boolean isAuthenticated(Player player) {
        if (!enabled) return true;
        return authenticated.contains(player.getUUID());
    }

    public boolean isPendingAuth(Player player) {
        return pendingAuth.contains(player.getUUID());
    }

    public boolean needsAuth(Player player) {
        return enabled && !authenticated.contains(player.getUUID());
    }

    public void setAuthenticated(Player player) {
        authenticated.add(player.getUUID());
        pendingAuth.remove(player.getUUID());
        loginTimeoutStart.remove(player.getUUID());
        requestCooldowns.remove(player.getUUID());
    }

    public void setPendingAuth(Player player) {
        pendingAuth.add(player.getUUID());
    }

    public void removePlayer(UUID uuid) {
        authenticated.remove(uuid);
        pendingAuth.remove(uuid);
        wrongAttempts.remove(uuid);
        requestCooldowns.remove(uuid);
        loginTimeoutStart.remove(uuid);
    }

    // ─── Wrong attempts ────────────────────────────────────────────────

    public int getWrongAttempts(Player player) {
        return wrongAttempts.getOrDefault(player.getUUID(), 0);
    }

    public int incrementWrongAttempts(ServerPlayer player) {
        int attempts = wrongAttempts.merge(player.getUUID(), 1, Integer::sum);
        if (attempts >= maxWrongAttempts) {
            player.connection.disconnect(
                    Component.literal("§cСлишком много неудачных попыток входа!"));
        }
        return attempts;
    }

    public void resetWrongAttempts(Player player) {
        wrongAttempts.remove(player.getUUID());
    }

    // ─── Rate limiter ──────────────────────────────────────────────────

    public boolean checkCooldown(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long lastRequest = requestCooldowns.get(player.getUUID());

        if (lastRequest != null && (now - lastRequest) < requestCooldownSeconds * 1000L) {
            player.sendSystemMessage(
                    Component.literal("§cПодождите перед следующим запросом!"));
            return false;
        }

        requestCooldowns.put(player.getUUID(), now);
        return true;
    }

    // ─════════════════════════════════════════════════════════════════════
    //  Events
    // ─════════════════════════════════════════════════════════════════════

    /**
     * При входе игрока — запускаем таймер аутентификации.
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!enabled) return;

        pendingAuth.add(player.getUUID());
        loginTimeoutStart.put(player.getUUID(), System.currentTimeMillis());

        player.sendSystemMessage(
                Component.literal("§eПожалуйста, авторизуйтесь с помощью /login <пароль>"));

        ConsoleLogger.debug("[Auth] " + player.getScoreboardName() + " requires authentication.");
    }

    /**
     * При выходе игрока — очищаем состояние.
     */
    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        removePlayer(event.getEntity().getUUID());
    }

    /**
     * Проверка таймаутов аутентификации (каждую секунду).
     */
    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        // Проверка таймаутов каждые 20 тиков (1 сек)
        long now = System.currentTimeMillis();

        for (var entry : loginTimeoutStart.entrySet()) {
            UUID uuid = entry.getKey();
            long startTime = entry.getValue();

            if ((now - startTime) >= loginTimeoutSeconds * 1000L) {
                var player = event.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && !authenticated.contains(uuid)) {
                    player.connection.disconnect(
                            Component.literal("§cВремя входа истекло!"));
                    ConsoleLogger.info("[Auth] " + player.getScoreboardName() + " kicked: login timeout.");
                }
                loginTimeoutStart.remove(uuid);
            }
        }
    }

    // ─── Config accessors ──────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public int getSessionDurationMinutes() { return sessionDurationMinutes; }
    public int getMinPasswordLength() { return minPasswordLength; }
    public int getMaxPasswordLength() { return maxPasswordLength; }
    public int getLoginTimeoutSeconds() { return loginTimeoutSeconds; }
    public int getMaxWrongAttempts() { return maxWrongAttempts; }
    public int getRequestCooldownSeconds() { return requestCooldownSeconds; }
    public int getMaxAccountsPerIp() { return maxAccountsPerIp; }
    public boolean isIpCheckEnabled() { return ipCheckEnabled; }
    public boolean isDupNameCheckEnabled() { return dupNameCheckEnabled; }
}
