package com.gameplayadditions.punish;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 🛡 PunishmentManager — система наказаний (бан/мут/кик/варн).
 */
public class PunishmentManager {

    private static final String HW_SALT = "GameplayAdditions-HW-FINGERPRINT";

    public static String computeHwId(String ip, String playerName) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = (ip != null ? ip : "0.0.0.0") + "|"
                    + (playerName != null ? playerName.toLowerCase() : "") + "|"
                    + HW_SALT;
            byte[] hash = md.digest(raw.getBytes());
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            ConsoleLogger.warn("[Punish] SHA-256 not available!");
            return ip != null ? ip : "unknown";
        }
    }

    public enum PunishType {
        BAN, MUTE, KICK, WARN
    }

    public static boolean punish(PunishType type, String targetUuid, String targetName,
                                  String reason, String punisher, long expiresAt,
                                  String ip, String hwId) {
        long now = System.currentTimeMillis();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     INSERT INTO punishments (type, player_uuid, player_name, reason,
                         ip_address, hw_id, punished_by, punished_at, expires_at, active)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                     """)) {
            st.setString(1, type.name().toLowerCase());
            st.setString(2, targetUuid);
            st.setString(3, targetName);
            st.setString(4, reason);
            st.setString(5, ip != null ? ip : "");
            st.setString(6, hwId != null ? hwId : "");
            st.setString(7, punisher);
            st.setLong(8, now);
            st.setLong(9, expiresAt);
            st.executeUpdate();
            ConsoleLogger.info("[Punish] " + type.name() + " " + targetName + " by " + punisher + " reason: " + reason);
            return true;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Punish] Failed to punish " + targetName + ": " + e.getMessage());
            return false;
        }
    }

    public static PunishmentRecord getActivePunishment(PunishType type, String uuid,
                                                        String ip, String hwId) {
        long now = System.currentTimeMillis();
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM punishments
                WHERE type = ? AND active = 1
                AND (expires_at = 0 OR expires_at > ?)
                AND (player_uuid = ?
                """);
        if (ip != null && !ip.isEmpty()) {
            sql.append(" OR ip_address = ?");
        }
        if (hwId != null && !hwId.isEmpty()) {
            sql.append(" OR hw_id = ?");
        }
        sql.append(") ORDER BY id DESC LIMIT 1");

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql.toString())) {
            st.setString(1, type.name().toLowerCase());
            st.setLong(2, now);
            st.setString(3, uuid);
            int idx = 4;
            if (ip != null && !ip.isEmpty()) {
                st.setString(idx++, ip);
            }
            if (hwId != null && !hwId.isEmpty()) {
                st.setString(idx, hwId);
            }
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return mapRecord(rs);
                }
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[Punish] Check error for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public static boolean unpunishById(int id) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "UPDATE punishments SET active = 0 WHERE id = ?")) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Punish] Failed to unpunish id=" + id + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean unpunishByType(PunishType type, String uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "UPDATE punishments SET active = 0 WHERE type = ? AND player_uuid = ? AND active = 1")) {
            st.setString(1, type.name().toLowerCase());
            st.setString(2, uuid);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Punish] Failed to unpunish type=" + type + " uuid=" + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean warn(String targetUuid, String targetName, String reason,
                                String warner, long expiresAt) {
        long now = System.currentTimeMillis();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     INSERT INTO warns (player_uuid, player_name, reason,
                         warned_by, warned_at, expires_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            st.setString(1, targetUuid);
            st.setString(2, targetName);
            st.setString(3, reason);
            st.setString(4, warner);
            st.setLong(5, now);
            st.setLong(6, expiresAt);
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Punish] Failed to warn " + targetName + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean removeWarnById(int id) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "UPDATE warns SET expires_at = 1 WHERE id = ?")) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[Punish] Failed to remove warn id=" + id + ": " + e.getMessage());
            return false;
        }
    }

    public static List<WarnRecord> getActiveWarns(String uuid) {
        List<WarnRecord> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     SELECT * FROM warns
                     WHERE player_uuid = ?
                     AND (expires_at = 0 OR expires_at > ?)
                     ORDER BY warned_at DESC
                     """)) {
            st.setString(1, uuid);
            st.setLong(2, now);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(new WarnRecord(
                            rs.getInt("id"),
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getString("reason"),
                            rs.getString("warned_by"),
                            rs.getLong("warned_at"),
                            rs.getLong("expires_at")
                    ));
                }
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[Punish] Failed to list warns for " + uuid + ": " + e.getMessage());
        }
        return result;
    }

    public static void kickPlayer(ServerPlayer player, String reason, String kicker) {
        player.connection.disconnect(
                net.minecraft.network.chat.Component.literal("Kicked: " + reason + " by " + kicker));
        punish(PunishType.KICK, player.getUUID().toString(), player.getName().getString(),
                reason, kicker, 0, null, null);
    }

    public static PunishmentRecord getActiveBan(String uuid, String ip, String hwId) {
        return getActivePunishment(PunishType.BAN, uuid, ip, hwId);
    }

    public static PunishmentRecord getActiveMute(String uuid, String ip, String hwId) {
        return getActivePunishment(PunishType.MUTE, uuid, ip, hwId);
    }

    private static PunishmentRecord mapRecord(ResultSet rs) throws SQLException {
        return new PunishmentRecord(
                rs.getInt("id"),
                PunishType.valueOf(rs.getString("type").toUpperCase()),
                rs.getString("player_uuid"),
                rs.getString("player_name"),
                rs.getString("reason"),
                rs.getString("ip_address"),
                rs.getString("hw_id"),
                rs.getString("punished_by"),
                rs.getLong("punished_at"),
                rs.getLong("expires_at"),
                rs.getInt("active") == 1
        );
    }

    public static class PunishmentRecord {
        public final int id;
        public final PunishType type;
        public final String playerUuid;
        public final String playerName;
        public final String reason;
        public final String ipAddress;
        public final String hwId;
        public final String punishedBy;
        public final long punishedAt;
        public final long expiresAt;
        public final boolean active;

        public PunishmentRecord(int id, PunishType type, String playerUuid, String playerName,
                                 String reason, String ipAddress, String hwId,
                                 String punishedBy, long punishedAt, long expiresAt, boolean active) {
            this.id = id;
            this.type = type;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.reason = reason;
            this.ipAddress = ipAddress;
            this.hwId = hwId;
            this.punishedBy = punishedBy;
            this.punishedAt = punishedAt;
            this.expiresAt = expiresAt;
            this.active = active;
        }

        public boolean isPermanent() { return expiresAt == 0; }
        public boolean isExpired() { return expiresAt > 0 && System.currentTimeMillis() > expiresAt; }
        public long getRemainingMs() {
            if (isPermanent()) return -1;
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }
    }

    public static class WarnRecord {
        public final int id;
        public final String playerUuid;
        public final String playerName;
        public final String reason;
        public final String warnedBy;
        public final long warnedAt;
        public final long expiresAt;

        public WarnRecord(int id, String playerUuid, String playerName, String reason,
                           String warnedBy, long warnedAt, long expiresAt) {
            this.id = id;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.reason = reason;
            this.warnedBy = warnedBy;
            this.warnedAt = warnedAt;
            this.expiresAt = expiresAt;
        }

        public boolean isPermanent() { return expiresAt == 0; }
    }
}
