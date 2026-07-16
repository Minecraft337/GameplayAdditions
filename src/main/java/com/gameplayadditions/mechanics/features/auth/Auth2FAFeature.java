package com.gameplayadditions.mechanics.features.auth;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth2FAFeature — порт {@code com.mcplugin.mechanics.security.auth.Auth2FA} из MC-Plugin.
 * <p>
 * 2FA через Telegram-бот (внешний HTTP-сервис). Хранит в {@code auth_2fa} таблице:
 * uuid, telegram_chat_id, enabled. Polling статуса подтверждения.
 */
public class Auth2FAFeature extends AbstractFeature {

    private static final String BOT_URL_DEFAULT = "http://localhost:3000";
    private static final long POLL_TIMEOUT_MS = 300_000L; // 5 minutes
    private static final long POLL_INTERVAL_MS = 1000L;   // 1 second

    private String botUrl = BOT_URL_DEFAULT;

    // UUID → {requestId, createdAt}
    private final ConcurrentHashMap<UUID, PendingConfirmation> pending = new ConcurrentHashMap<>();

    public Auth2FAFeature() {
    }

    @Override
    public String getName() {
        return "Auth2FA";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            initTable();
            ConsoleLogger.info("[Auth2FA] Initialized. Bot URL: " + botUrl);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT TABLE
    // ══════════════════════════════════════════════════════════════════════

    public void initTable() {
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS auth_2fa (
                    uuid TEXT PRIMARY KEY,
                    telegram_chat_id TEXT DEFAULT '',
                    enabled INTEGER DEFAULT 0
                )
            """);
        } catch (Exception e) {
            ConsoleLogger.error("[Auth2FA] initTable failed: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2FA STATE
    // ══════════════════════════════════════════════════════════════════════

    public boolean isEnabled(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT enabled FROM auth_2fa WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) != 0;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public String getChatId(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT telegram_chat_id FROM auth_2fa WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception ignored) {}
        return "";
    }

    public void setEnabled(UUID uuid, String chatId, boolean enabled) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO auth_2fa (uuid, telegram_chat_id, enabled) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, chatId != null ? chatId : "");
            ps.setInt(3, enabled ? 1 : 0);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Auth2FA] setEnabled failed: " + e.getMessage());
        }
    }

    public void remove(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM auth_2fa WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONFIRMATION FLOW
    // ══════════════════════════════════════════════════════════════════════

    public enum ConfirmationStatus {
        PENDING,
        APPROVED,
        DENIED,
        TIMEOUT,
        NOT_REQUESTED
    }

    // ─── Bounded executor для HTTP вызовов (избегаем Thread leak) ─────────
    private final java.util.concurrent.ExecutorService HTTP_EXECUTOR =
            java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "Auth2FA-HTTP");
                t.setDaemon(true);
                return t;
            });

    /**
     * Отправляет HTTP POST на bot URL, чтобы бот прислал игроку запрос в Telegram.
     */
    public void sendConfirmation(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        String reqId = "auth-" + uuid.toString().substring(0, 8) + "-" + now;
        pending.put(uuid, new PendingConfirmation(reqId, now));

        // Fire-and-forget HTTP POST (не блокируем server tick)
        String playerName = player.getScoreboardName();
        String chatId = getChatId(uuid);
        HTTP_EXECUTOR.submit(() -> {
            try {
                doSendConfirmation(reqId, uuid, playerName, chatId);
            } catch (Exception e) {
                ConsoleLogger.warn("[Auth2FA] Send task failed: " + e.getMessage());
            }
        });
    }

    private void doSendConfirmation(String reqId, UUID uuid, String name, String chatId) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(botUrl + "/send-confirm");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            String body = "{\"request_id\":\"" + escape(reqId) + "\"," +
                    "\"uuid\":\"" + uuid + "\"," +
                    "\"name\":\"" + escape(name) + "\"," +
                    "\"chat_id\":\"" + escape(chatId) + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int rc = conn.getResponseCode();
            // Drain response stream, чтобы HttpURLConnection мог вернуть сокет в пул
            try (java.io.InputStream es = (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream()) {
                if (es != null) es.readAllBytes();
            } catch (Exception ignored) {}
            ConsoleLogger.debug("[Auth2FA] sendConfirmation -> HTTP " + rc + " for " + name);
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth2FA] sendConfirmation HTTP failed: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Polling статуса подтверждения для игрока. Каждый tick вызывается из onServerTickPost.
     */
    public ConfirmationStatus checkConfirmation(UUID uuid) {
        PendingConfirmation p = pending.get(uuid);
        if (p == null) return ConfirmationStatus.NOT_REQUESTED;

        long now = System.currentTimeMillis();
        if ((now - p.createdAt) >= POLL_TIMEOUT_MS) {
            pending.remove(uuid);
            return ConfirmationStatus.TIMEOUT;
        }

        // Throttle HTTP call: раз в секунду
        if ((now - p.lastPollAt) < POLL_INTERVAL_MS) {
            return ConfirmationStatus.PENDING;
        }
        p.lastPollAt = now;

        try {
            URL url = new URL(botUrl + "/check-confirm?request_id=" + p.requestId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setInstanceFollowRedirects(false);
            int rc = conn.getResponseCode();
            java.io.InputStream stream = (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) return ConfirmationStatus.PENDING;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String response = br.readLine();
                if (response == null) return ConfirmationStatus.PENDING;

                if (response.contains("\"approved\":true") || response.contains("\"status\":\"approved\"")) {
                    pending.remove(uuid);
                    return ConfirmationStatus.APPROVED;
                }
                if (response.contains("\"denied\":true") || response.contains("\"status\":\"denied\"")) {
                    pending.remove(uuid);
                    return ConfirmationStatus.DENIED;
                }
            }
        } catch (Exception e) {
            ConsoleLogger.debug("[Auth2FA] checkConfirmation HTTP failed: " + e.getMessage());
        }
        return ConfirmationStatus.PENDING;
    }

    public void clearPending(UUID uuid) {
        pending.remove(uuid);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TICK — очистка зависших pending по таймауту
    // ══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (pending.isEmpty()) return;
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> (now - e.getValue().createdAt) >= POLL_TIMEOUT_MS);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UTIL
    // ══════════════════════════════════════════════════════════════════════

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void setBotUrl(String url) {
        if (url != null && !url.isEmpty()) this.botUrl = url;
    }

    /**
     * Хранит состояние ожидания подтверждения. Не record, т.к. lastPollAt
     * обновляется при каждом тике.
     */
    private static final class PendingConfirmation {
        final String requestId;
        final long createdAt;
        long lastPollAt;

        PendingConfirmation(String requestId, long createdAt) {
            this.requestId = requestId;
            this.createdAt = createdAt;
            this.lastPollAt = 0L;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LIFECYCLE — корректная остановка executor
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void onServerStop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        try {
            HTTP_EXECUTOR.shutdown();
            if (!HTTP_EXECUTOR.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                HTTP_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException ie) {
            HTTP_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        super.onServerStop(event);
    }
}
