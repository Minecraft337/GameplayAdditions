package com.gameplayadditions.report;

import com.gameplayadditions.GameplayAdditionsMod;
import com.gameplayadditions.config.MessagesManager;
import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Система репортов на игроков.
 */
public class ReportManager {

    private static ReportManager instance;
    private static final Map<UUID, ModerationSession> modSessions = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> removeConfirmations = new ConcurrentHashMap<>();

    public static class ModerationSession {
        public int reportId;
        public String modName;
        public String conclusion = "";
        public Step step = Step.CONCLUSION;
        public enum Step { CONCLUSION, VERDICT }
    }

    public static class ReportData {
        public int id;
        public String reporterUuid;
        public String reportedUuid;
        public String reason;
        public String status;
        public long createdAt;
        public long expiresAt;
        public String reporterName = "";
        public String reportedName = "";
        public String verdictOption = "";
        public String verdict = "";
        public String moderatorName = "";
    }

    public static void init() {
        instance = new ReportManager();
    }

    public static ReportManager getInstance() { return instance; }

    public static void trackPlayerVisit(ServerPlayer player) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO player_visits (uuid, name, first_join, last_join) VALUES (?, ?, strftime('%s','now'), strftime('%s','now')) " +
                     "ON CONFLICT(uuid) DO UPDATE SET name = ?, last_join = strftime('%s','now')")) {
            ps.setString(1, player.getUUID().toString());
            ps.setString(2, player.getName().getString());
            ps.setString(3, player.getName().getString());
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Reports] Failed to track player visit: " + e.getMessage());
        }
    }

    public static boolean hasEverJoined(String playerName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT uuid FROM player_visits WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    public static String getUuidByName(String playerName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT uuid FROM player_visits WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("uuid");
        } catch (Exception ignored) {}
        return null;
    }

    public static String createReport(UUID reporterUuid, String reportedName, String reason) {
        String reporterUuidStr = reporterUuid.toString();
        if (hasPendingReport(reporterUuidStr)) {
            return "You already have an active report!";
        }

        String reportedUuid = getUuidByName(reportedName);
        if (reportedUuid == null) {
            return "Player " + reportedName + " has never joined the server!";
        }

        if (reporterUuidStr.equals(reportedUuid)) {
            return "You cannot report yourself!";
        }

        long now = System.currentTimeMillis() / 1000;
        long expiresAt = now + (3 * 86400); // 3 days default

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO reports (reporter_uuid, reported_uuid, reason, status, created_at, expires_at) " +
                     "VALUES (?, ?, ?, 'pending', ?, ?)")) {
            ps.setString(1, reporterUuidStr);
            ps.setString(2, reportedUuid);
            ps.setString(3, reason);
            ps.setLong(4, now);
            ps.setLong(5, expiresAt);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Reports] Failed to create report: " + e.getMessage());
            return "Database error creating report!";
        }
        return null;
    }

    public static boolean hasPendingReport(String uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id FROM reports WHERE reporter_uuid = ? AND status = 'pending'")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    public static List<ReportData> getAllReports() {
        List<ReportData> list = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT r.*, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reporter_uuid), '?') as reporter_name, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reported_uuid), '?') as reported_name, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.moderator_uuid), '?') as moderator_name " +
                     "FROM reports r ORDER BY r.created_at DESC LIMIT 100")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ReportData data = new ReportData();
                data.id = rs.getInt("id");
                data.reporterUuid = rs.getString("reporter_uuid");
                data.reportedUuid = rs.getString("reported_uuid");
                data.reason = rs.getString("reason");
                data.status = rs.getString("status");
                data.createdAt = rs.getLong("created_at");
                data.expiresAt = rs.getLong("expires_at");
                data.verdictOption = rs.getString("verdict_option");
                data.verdict = rs.getString("verdict");
                data.reporterName = rs.getString("reporter_name");
                data.reportedName = rs.getString("reported_name");
                data.moderatorName = rs.getString("moderator_name");
                list.add(data);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static boolean isInModeration(ServerPlayer player) {
        return modSessions.containsKey(player.getUUID());
    }
}
