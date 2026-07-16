package com.gameplayadditions.mechanics.features.auth;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AuthDatabaseFeature — порт {@code com.mcplugin.mechanics.security.auth.AuthDatabase} из MC-Plugin.
 * <p>
 * Хранение паролей (PBKDF2WithHmacSHA256 — fallback вместо Argon2, т.к. нет argon2-jvm),
 * сессии по IP, миграция {@code last_login} колонки.
 * <p>
 * Все операции с паролями выполняются асинхронно, чтобы не морозить серверный поток.
 */
public class AuthDatabaseFeature extends AbstractFeature {

    // ─── PBKDF2 параметры (fallback вместо Argon2id) ─────────────────────
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_LENGTH = 256; // bits
    private static final int SALT_LENGTH = 16;        // bytes

    // PBKDF2 префикс (для будущей миграции на Argon2 при добавлении библиотеки)
    private static final String PBKDF2_PREFIX = "PBKDF2$";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ExecutorService HASH_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "Auth-Hash-Worker");
                t.setDaemon(true);
                return t;
            });

    public AuthDatabaseFeature() {
    }

    @Override
    public String getName() {
        return "AuthDatabase";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::initTable);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT TABLE — миграция auth: +last_login +ip_address
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Добавляет недостающие колонки в существующую таблицу auth.
     * Идемпотентно — можно вызывать многократно.
     */
    public void initTable() {
        try (Connection con = ensureConnection();
             Statement st = con.createStatement()) {

            // last_login (Unix seconds) — для сессий
            try {
                st.executeUpdate("ALTER TABLE auth ADD COLUMN last_login INTEGER DEFAULT 0");
                ConsoleLogger.info("[AuthDB] Migration: added 'last_login' column.");
            } catch (Exception ignore) {
                // Колонка уже есть
            }

            try {
                st.executeUpdate("ALTER TABLE auth ADD COLUMN ip_address TEXT DEFAULT ''");
                ConsoleLogger.info("[AuthDB] Migration: added 'ip_address' column.");
            } catch (Exception ignore) {
                // Колонка уже есть
            }

        } catch (Exception e) {
            ConsoleLogger.error("[AuthDB] initTable failed: " + e.getMessage());
        }
    }

    private Connection ensureConnection() throws Exception {
        Connection con = DatabaseManager.getConnection();
        if (con == null) {
            throw new IllegalStateException("DatabaseManager returned null connection");
        }
        return con;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PASSWORD HASHING (asynchronous)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Асинхронно хеширует пароль с новой salt и возвращает префиксную строку
     * формата {@code PBKDF2$<iterations>$<salt_b64>$<hash_b64>}.
     */
    public CompletableFuture<String> hashPasswordAsync(char[] password) {
        return CompletableFuture.supplyAsync(() -> hashPassword(password), HASH_EXECUTOR);
    }

    /**
     * Синхронный хеш (для вызовов из уже-async контекста).
     * Очищает переданный массив {@code password} после хеширования.
     */
    public String hashPassword(char[] password) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            SECURE_RANDOM.nextBytes(salt);

            PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();

            String result = PBKDF2_PREFIX + PBKDF2_ITERATIONS + "$" +
                    Base64.getEncoder().encodeToString(salt) + "$" +
                    Base64.getEncoder().encodeToString(hash);
            Arrays.fill(password, '\0');
            return result;
        } catch (Exception e) {
            ConsoleLogger.error("[AuthDB] hashPassword failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Асинхронная верификация пароля.
     */
    public CompletableFuture<Boolean> verifyPasswordAsync(UUID uuid, char[] input) {
        return CompletableFuture.supplyAsync(() -> verifyPassword(uuid, input), HASH_EXECUTOR);
    }

    /**
     * Синхронная верификация.
     */
    public boolean verifyPassword(UUID uuid, char[] input) {
        String stored = getPasswordHash(uuid);
        if (stored == null) return false;
        boolean ok = checkRaw(stored, input);
        Arrays.fill(input, '\0');
        return ok;
    }

    private String getPasswordHash(UUID uuid) {
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT password_hash FROM auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean checkRaw(String stored, char[] input) {
        if (!stored.startsWith(PBKDF2_PREFIX)) return false;
        try {
            String[] parts = stored.split("\\$");
            // PBKDF2$<iterations>$<salt>$<hash>
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);

            PBEKeySpec spec = new PBEKeySpec(input, salt, iterations, expectedHash.length * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] actualHash = skf.generateSecret(spec).getEncoded();

            return constantTimeEquals(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REGISTRATION / PASSWORD MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Регистрирует нового игрока. Возвращает true при успехе.
     */
    public boolean register(UUID uuid, String hashedPassword, String ip) {
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO auth (uuid, password_hash, salt, ip_address, last_login) " +
                             "VALUES (?, ?, '', ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, hashedPassword);
            ps.setString(3, ip != null ? ip : "");
            ps.setLong(4, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            ConsoleLogger.error("[AuthDB] register failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Админский сброс пароля. Обновляет хеш.
     */
    public boolean adminResetPassword(UUID uuid, String newHashedPassword) {
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET password_hash = ? WHERE uuid = ?")) {
            ps.setString(1, newHashedPassword);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (Exception ignored) {}
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SESSIONS / IP
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Обновляет last_login для игрока. Вызывается при успешной авторизации.
     */
    public void updateLastLogin(UUID uuid) {
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET last_login = ? WHERE uuid = ?")) {
            ps.setLong(1, System.currentTimeMillis() / 1000L);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    /**
     * Проверяет валидность сессии для IP. У игрока должна быть запись auth.last_login
     * не старше {@code sessionDurationMinutes} и совпадать ip_address (если ipCheck включён).
     */
    public boolean hasValidSession(UUID uuid, String currentIp, int sessionDurationMinutes, boolean ipCheck) {
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT last_login, ip_address FROM auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                long lastLogin = rs.getLong(1);
                String storedIp = rs.getString(2);

                if (lastLogin <= 0) return false;
                long nowSec = System.currentTimeMillis() / 1000L;
                long ageMinutes = (nowSec - lastLogin) / 60L;
                if (ageMinutes > sessionDurationMinutes) return false;

                if (ipCheck && currentIp != null && !currentIp.isEmpty()
                        && !storedIp.isEmpty() && !storedIp.equals(currentIp)) {
                    return false;
                }
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Возвращает ранее сохранённый IP для игрока (или пустая строка).
     */
    public String getStoredIp(UUID uuid) {
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT ip_address FROM auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * Сохраняет текущий IP игрока (при успешной авторизации).
     */
    public void setStoredIp(UUID uuid, String ip) {
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE auth SET ip_address = ? WHERE uuid = ?")) {
            ps.setString(1, ip != null ? ip : "");
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    /**
     * Подсчитывает количество аккаунтов, привязанных к IP.
     */
    public int countAccountsByIp(String ip) {
        if (ip == null || ip.isEmpty()) return 0;
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM auth WHERE ip_address = ?")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * Возвращает список всех зарегистрированных UUID.
     */
    public List<UUID> getAllRegisteredUuids() {
        List<UUID> result = new ArrayList<>();
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT uuid FROM auth");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    result.add(UUID.fromString(rs.getString(1)));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Проверяет наличие регистрации.
     */
    public boolean isRegistered(UUID uuid) {
        try (Connection con = ensureConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void onServerStop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        super.onServerStop(event);
        HASH_EXECUTOR.shutdown();
    }
}
