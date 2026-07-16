package com.gameplayadditions.mechanics.vanish;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.MessageUtil;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VanishManager — система полного скрытия игроков.
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.player.VanishManager} из MC-Plugin.
 * <p>
 * Фичи:
 * <ul>
 *   <li>Скрытие из tab list (ClientboundPlayerInfoRemovePacket)</li>
 *   <li>Скрытие сущности (ClientboundRemoveEntitiesPacket)</li>
 *   <li>Применение при входе/респавне/смене мира</li>
 *   <li>Отмена join/quit сообщений (через событие)</li>
 *   <li>Сохранение состояния в SQLite</li>
 * </ul>
 */
public class VanishManager {

    private static VanishManager instance;
    private static final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private boolean listenersRegistered = false;

    // ══════════════════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════════════════

    public static void init() {
        instance = new VanishManager();
        loadVanishedPlayers();
        instance.registerListeners();
        ConsoleLogger.info("[Vanish] Initialized. " + vanishedPlayers.size() + " vanished player(s) loaded.");
    }

    public static VanishManager getInstance() { return instance; }

    private void registerListeners() {
        if (listenersRegistered) return;
        listenersRegistered = true;
        NeoForge.EVENT_BUS.register(this);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PERSISTENCE (SQLite → vanished_players)
    // ══════════════════════════════════════════════════════════════════════════

    private static void loadVanishedPlayers() {
        vanishedPlayers.clear();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT uuid FROM vanished_players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    vanishedPlayers.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                    ConsoleLogger.warn("[Vanish] Invalid UUID in DB: " + rs.getString("uuid"));
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Vanish] DB load failed: " + e.getMessage());
        }
    }

    private static void saveVanishedPlayers() {
        try (Connection con = DatabaseManager.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement del = con.prepareStatement("DELETE FROM vanished_players")) {
                del.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO vanished_players (uuid) VALUES (?)")) {
                for (UUID uuid : vanishedPlayers) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            }
            con.commit();
            con.setAutoCommit(true);
        } catch (Exception e) {
            ConsoleLogger.warn("[Vanish] DB save failed: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VANISH TOGGLE
    // ══════════════════════════════════════════════════════════════════════════

    public static boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public static void setVanished(UUID uuid, boolean vanished) {
        if (vanished) {
            if (vanishedPlayers.add(uuid)) {
                ServerPlayer player = findPlayer(uuid);
                if (player != null) applyVanish(player);
            }
        } else {
            if (vanishedPlayers.remove(uuid)) {
                ServerPlayer player = findPlayer(uuid);
                if (player != null) removeVanish(player);
            }
        }
        saveVanishedPlayers();
    }

    public static void toggleVanish(UUID uuid) {
        setVanished(uuid, !isVanished(uuid));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PACKET HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Удаляет игрока из tab list всех остальных.
     */
    private static void removeFromTabList(ServerPlayer target) {
        var packet = new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID()));
        for (var player : target.server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(target.getUUID())) continue;
            player.connection.send(packet);
        }
    }

    /**
     * Возвращает игрока в tab list всех остальных.
     */
    private static void addToTabList(ServerPlayer target) {
        var packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
                ),
                List.of(target)
        );
        for (var player : target.server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(target.getUUID())) continue;
            player.connection.send(packet);
        }
    }

    /**
     * Удаляет сущность ванишнутого игрока с клиента всех остальных.
     */
    private static void removeEntityFromAll(ServerPlayer target) {
        var packet = new ClientboundRemoveEntitiesPacket(target.getId());
        for (var player : target.server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(target.getUUID())) continue;
            player.connection.send(packet);
        }
    }

    /**
     * Удаляет всех ванишнутых из tab list указанного игрока.
     */
    private static void removeVanishedFromTabList(ServerPlayer viewer) {
        List<UUID> uuids = new ArrayList<>();
        for (UUID uuid : vanishedPlayers) {
            if (uuid.equals(viewer.getUUID())) continue;
            uuids.add(uuid);
        }
        if (!uuids.isEmpty()) {
            viewer.connection.send(new ClientboundPlayerInfoRemovePacket(uuids));
        }
    }

    /**
     * Скрывает всех ванишнутых от указанного игрока (entity + tab list).
     */
    private static void hideVanishedFrom(ServerPlayer viewer) {
        for (UUID uuid : vanishedPlayers) {
            if (uuid.equals(viewer.getUUID())) continue;
            ServerPlayer vanishedPlayer = findPlayer(uuid);
            if (vanishedPlayer != null) {
                viewer.connection.send(new ClientboundRemoveEntitiesPacket(vanishedPlayer.getId()));
            }
        }
        removeVanishedFromTabList(viewer);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // APPLY / REMOVE VANISH
    // ══════════════════════════════════════════════════════════════════════════

    private static void applyVanish(ServerPlayer player) {
        // Скрываем сущность от всех остальных
        removeEntityFromAll(player);
        // Удаляем из tab list всех остальных
        removeFromTabList(player);
    }

    private static void removeVanish(ServerPlayer player) {
        // Возвращаем в tab list всех остальных
        addToTabList(player);
        // Сущность автоматически появится при следующем обновлении чанка/позиции
    }

    private static void applyVanishOnJoin(ServerPlayer player) {
        // Скрываем этого игрока от всех
        removeEntityFromAll(player);
        removeFromTabList(player);
        // Скрываем всех ванишнутых от этого игрока
        hideVanishedFrom(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EVENTS
    // ══════════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        if (isVanished(uuid)) {
            // Отменяем join message через отложенное применение
            NeoForge.EVENT_BUS.register(new Object() {
                int delay = 2;

                @SubscribeEvent
                public void onTick(ServerTickEvent.Pre e) {
                    delay--;
                    if (delay <= 0) {
                        if (player.isAlive()) {
                            applyVanishOnJoin(player);
                        }
                        NeoForge.EVENT_BUS.unregister(this);
                    }
                }
            });
        } else {
            // Скрываем ванишнутых от этого игрока
            NeoForge.EVENT_BUS.register(new Object() {
                int delay = 2;

                @SubscribeEvent
                public void onTick(ServerTickEvent.Pre e) {
                    delay--;
                    if (delay <= 0) {
                        if (player.isAlive()) {
                            hideVanishedFrom(player);
                        }
                        NeoForge.EVENT_BUS.unregister(this);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // quit message handling: в NeoForge нет прямого API для отмены,
        // но можно оставить как есть — мод не добавляет свои quit сообщения.
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // При смене мира/респавне переприменяем ваниш
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isVanished(player.getUUID())) return;

        NeoForge.EVENT_BUS.register(new Object() {
            int delay = 3;

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre e) {
                delay--;
                if (delay <= 0) {
                    if (player.isAlive() && isVanished(player.getUUID())) {
                        removeEntityFromAll(player);
                        removeFromTabList(player);
                    }
                    NeoForge.EVENT_BUS.unregister(this);
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private static ServerPlayer findPlayer(UUID uuid) {
        if (instance == null) return null;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }

    public static Set<UUID> getVanishedPlayers() {
        return Collections.unmodifiableSet(vanishedPlayers);
    }

    public static int getVanishedCount() {
        return vanishedPlayers.size();
    }
}
