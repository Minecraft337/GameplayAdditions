package com.gameplayadditions.mechanics.features.keyauth;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.core.FeatureRegistry;
import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.mechanics.features.auth.AuthFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * KeyAuthFeature — альтернативная авторизация через локальный .key файл на клиенте.
 * <p>
 * Логика:
 * <ol>
 *   <li>Игрок вводит {@code /keyauth} → сервер шлёт {@link KeyAuthPayloads.OpenGuiPayload} клиенту.</li>
 *   <li>Клиент открывает {@code KeyAuthScreen}: {@code Продолжить} генерирует SecureRandom
 *       hex-ключ (256-bit), пишет в файл по выбранной директории и шлёт на сервер.</li>
 *   <li>Сервер хеширует ключ (SHA-256), сохраняет в БД ({@code auth.key_hash}).</li>
 *   <li>При следующем логине сервер шлёт OpenGui с {@code alreadyRegistered=true} → клиент читает
 *       файл, отправляет ключ → сервер сравнивает хеш, авторизует.</li>
 * </ol>
 * <p>
 * Не зависит от {@code AuthAuthenticatorFeature}: работает параллельно как
 * второй способ авторизации.
 */
public class KeyAuthFeature extends AbstractFeature {

    public KeyAuthFeature() {
    }

    @Override
    public String getName() {
        return "KeyAuth";
    }

    // ─── Phase 1: REGISTRATION ─────────────────────────────────────────────
    // Регистрация на modEventBus (NeoForge события регистрации запускаются
    // на mod bus, а не на game bus).
    @Override
    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegisterPayloadHandlers);
        ConsoleLogger.info("[Feature:KeyAuth] subscribed to RegisterPayloadHandlersEvent.");
    }

    // ─── Phase 2: SETUP (DB migration) ────────────────────────────────────
    @Override
    public void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::migrateAuthTable);
    }

    /**
     * Добавляет колонку key_hash в существующую таблицу auth (идемпотентно).
     * Миграционный лог пишется ТОЛЬКО когда колонка реально добавляется.
     */
    private void migrateAuthTable() {
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {
            boolean added = false;
            try {
                st.executeUpdate("ALTER TABLE auth ADD COLUMN key_hash TEXT DEFAULT ''");
                added = true;
            } catch (Exception ignore) {
                // Колонка уже есть — пропускаем.
            }
            if (added) {
                ConsoleLogger.info("[KeyAuth] Migration: added 'key_hash' column to auth.");
            } else {
                ConsoleLogger.debug("[KeyAuth] Migration: 'key_hash' column already exists.");
            }
        } catch (Exception e) {
            ConsoleLogger.error("[KeyAuth] migrateAuthTable failed: " + e.getMessage());
        }
    }

    /**
     * Кросс-платформенный путь по умолчанию для каталога .key файлов:
     * {@code $HOME/GameplayAdditions}. Работает на Linux/macOS/Windows.
     */
    public static Path defaultKeyPath() {
        String home = System.getProperty("user.home");
        return Paths.get(home, "GameplayAdditions");
    }

    // ─── Phase 3: SERVER START ────────────────────────────────────────────
    @Override
    public void onServerStart(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        super.onServerStart(event);
        registerGameEvents();
    }

    // ─── COMMAND: /keyauth ─────────────────────────────────────────────────
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        d.register(net.minecraft.commands.Commands.literal("keyauth")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    openKeyAuthGui(player);
                    return 1;
                })
                .then(net.minecraft.commands.Commands.literal("logout")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                            deleteKeyHash(player.getUUID());
                            player.displayClientMessage(
                                    Component.literal("§eKeyAuth сброшен. Используйте §f/keyauth§e снова для регистрации."), false);
                            return 1;
                        })));
        ConsoleLogger.info("[KeyAuth] Command registered: /keyauth [/logout].");
    }

    // ─── NETWORK: payload registration ─────────────────────────────────────
    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar("gameplayadditions");
        // C2S — клиент шлёт ключ для авторизации (registration или login).
        // UUID в payload НЕ trust-bound; реальный отправитель берётся из ctx.player().
        reg.playToServer(KeyAuthPayloads.LoginPayload.TYPE,
                        KeyAuthPayloads.LoginPayload.STREAM_CODEC,
                        new net.neoforged.neoforge.network.handling.IPayloadHandler<KeyAuthPayloads.LoginPayload>() {
                            @Override
                            public void handle(KeyAuthPayloads.LoginPayload payload,
                                               net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
                                // Передаём ctx как ServerPayloadContext, чтобы достать реального отправителя.
                                if (ctx instanceof net.neoforged.neoforge.network.handling.ServerPayloadContext serverCtx) {
                                    ctx.enqueueWork(() -> handleLoginServer(payload, serverCtx.player()));
                                }
                            }
                        });
        ConsoleLogger.info("[KeyAuth] Network payloads registered.");
    }

    /**
     * Обработка C2S LoginPayload: реальный UUID берётся из network context
     * (UUID из payload используется только для логирования / защиты от spoofing).
     * <p>
     * Если key_hash не существует — это первый вход; регистрируем и авторизуем.
     */
    private void handleLoginServer(KeyAuthPayloads.LoginPayload payload, ServerPlayer sender) {
        if (sender == null || sender.isRemoved()) return;

        // Защита от UUID spoofing: если payload UUID не совпадает с реальным отправителем — игнорируем.
        if (payload.playerUuid() != null && !payload.playerUuid().equals(sender.getUUID())) {
            ConsoleLogger.warn("[KeyAuth] Spoof attempt: payload UUID " + payload.playerUuid()
                    + " != sender " + sender.getUUID());
            sender.displayClientMessage(
                    Component.literal("§cОшибка авторизации."), false);
            return;
        }

        UUID realUuid = sender.getUUID();

        String storedHash = getKeyHash(realUuid);
        if (storedHash == null || storedHash.isEmpty()) {
            // Первый раз — регистрируем и сразу авторизуем.
            saveKeyHash(realUuid, payload.key());
            storedHash = sha256Hex(payload.key());
            ConsoleLogger.info("[KeyAuth] " + sender.getScoreboardName() + " registered new .key.");
        }

        String givenHash = sha256Hex(payload.key());
        boolean match = (storedHash.length() == givenHash.length()) && MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                givenHash.getBytes(StandardCharsets.UTF_8));

        AuthFeature auth = FeatureRegistry.get(AuthFeature.class);
        if (match) {
            if (auth != null && auth.needsAuth(sender)) {
                auth.setAuthenticated(sender);
                sender.displayClientMessage(Component.literal("§aКлюч верный! Вы авторизованы."), false);
                ConsoleLogger.info("[KeyAuth] " + sender.getScoreboardName() + " authenticated by .key file.");
            }
        } else {
            sender.displayClientMessage(
                    Component.literal("§cНеверный ключ! Авторизация отклонена."), false);
            if (auth != null) auth.incrementWrongAttempts(sender);
        }
    }

    // ─── DB HELPERS ────────────────────────────────────────────────────────
    /**
     * Безопасный апдейт: НЕ удаляет существующие password_hash / salt / last_login.
     * <p>
     * Сначала пробует UPDATE первичного ключа; если строки ещё нет (новый KeyAuth-only
     * игрок без /register) — создаёт строку с uuid + key_hash и пустыми прочими колонками.
     */
    private void saveKeyHash(UUID uuid, String key) {
        try (Connection con = DatabaseManager.getConnection()) {
            int updated;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE auth SET key_hash = ? WHERE uuid = ?")) {
                ps.setString(1, sha256Hex(key));
                ps.setString(2, uuid.toString());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO auth (uuid, password_hash, salt, ip_address, last_login, key_hash) " +
                                "VALUES (?, '', '', '', 0, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, sha256Hex(key));
                    ps.executeUpdate();
                    ConsoleLogger.info("[KeyAuth] New auth row created for " + uuid + " (KeyAuth-only).");
                }
            }
        } catch (Exception e) {
            ConsoleLogger.error("[KeyAuth] saveKeyHash failed: " + e.getMessage());
        }
    }

    private String getKeyHash(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT key_hash FROM auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void deleteKeyHash(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET key_hash = '' WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private boolean isRegistered(UUID uuid) {
        String h = getKeyHash(uuid);
        return h != null && !h.isEmpty();
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ─── GUI INVITATION ────────────────────────────────────────────────────
    /**
     * Сервер отправляет клиенту запрос открыть KeyAuth GUI.
     * Путь по умолчанию: {@code $HOME/GameplayAdditions} (кросс-платформенно).
     */
    public void openKeyAuthGui(ServerPlayer player) {
        KeyAuthPayloads.OpenGuiPayload payload = new KeyAuthPayloads.OpenGuiPayload(
                isRegistered(player.getUUID()),
                defaultKeyPath().toString()
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    // ─── Auto-trigger: при заходе на сервер ────────────────────────────────
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isRegistered(player.getUUID())) return;
        // Сервер посылает приглашение открыть GUI; клиент сам решит читать ли файл.
        openKeyAuthGui(player);
    }

}
