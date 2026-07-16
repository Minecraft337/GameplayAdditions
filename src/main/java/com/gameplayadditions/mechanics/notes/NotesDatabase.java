package com.gameplayadditions.mechanics.notes;

import com.gameplayadditions.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * NotesDatabase — работа с таблицей notes в SQLite.
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.items.NotesDatabase} из MC-Plugin.
 * Таблица уже создаётся в {@link com.gameplayadditions.database.DatabaseInit}.
 */
public final class NotesDatabase {

    private static final long SAVE_COOLDOWN_MS = 5000;
    private static final Map<UUID, Long> lastSaveTimes = new HashMap<>();

    private NotesDatabase() {}

    /**
     * Загружает заметку игрока.
     * @return содержимое заметки или null если нет
     */
    public static String loadNote(UUID playerUuid, int noteNumber) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT content FROM notes WHERE player_uuid = ? AND slot_number = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, noteNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("content");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Сохраняет заметку игрока. С кулдауном 5 секунд.
     * @return true если сохранено, false если кулдаун
     */
    public static boolean saveNote(UUID playerUuid, int noteNumber, String content) {
        long now = System.currentTimeMillis();
        Long last = lastSaveTimes.get(playerUuid);
        if (last != null && (now - last) < SAVE_COOLDOWN_MS) {
            return false;
        }
        lastSaveTimes.put(playerUuid, now);

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO notes (player_uuid, slot_number, content) VALUES (?, ?, ?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, noteNumber);
            ps.setString(3, content != null ? content : "");
            ps.executeUpdate();
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Возвращает список номеров существующих заметок игрока.
     */
    public static List<Integer> getNoteSlots(UUID playerUuid) {
        List<Integer> slots = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT slot_number FROM notes WHERE player_uuid = ? ORDER BY slot_number")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slots.add(rs.getInt("slot_number"));
                }
            }
        } catch (Exception ignored) {}
        return slots;
    }
}
