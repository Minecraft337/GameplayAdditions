package com.gameplayadditions.economy;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ядро валютной системы.
 * Поддерживает несколько валют (coins, gems, tokens и т.д.).
 */
public final class EconomyManager {

    private static EconomyManager instance;

    private static final String TABLE = "economy_balances";
    private static final String PRIMARY_CURRENCY = "coins";
    private static final double DEFAULT_BALANCE = 100.0;

    private EconomyManager() {}

    public static void init() {
        instance = new EconomyManager();
        createTable();
        ConsoleLogger.info("[Economy] Initialized. Default: " + DEFAULT_BALANCE + " " + PRIMARY_CURRENCY);
    }

    public static EconomyManager getInstance() {
        return instance;
    }

    private static void createTable() {
        try (Connection con = DatabaseManager.getConnection();
             var st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS economy_balances (
                    uuid TEXT NOT NULL,
                    currency TEXT NOT NULL DEFAULT 'coins',
                    balance REAL DEFAULT 0,
                    PRIMARY KEY (uuid, currency)
                );
            """);
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] Failed to create table: " + e.getMessage());
        }
    }

    public double getBalance(UUID uuid, String currency) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT balance FROM " + TABLE + " WHERE uuid = ? AND currency = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] getBalance error: " + e.getMessage());
        }
        return 0.0;
    }

    public double getBalance(UUID uuid) {
        return getBalance(uuid, PRIMARY_CURRENCY);
    }

    public void setBalance(UUID uuid, String currency, double amount) {
        if (amount < 0) amount = 0;
        String curr = currency.toLowerCase();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO " + TABLE + " (uuid, currency, balance) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, curr);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] setBalance error: " + e.getMessage());
        }
    }

    public void setBalance(UUID uuid, double amount) {
        setBalance(uuid, PRIMARY_CURRENCY, amount);
    }

    public void addBalance(UUID uuid, String currency, double amount) {
        if (amount <= 0) return;
        double current = getBalance(uuid, currency);
        setBalance(uuid, currency, current + amount);
    }

    public void addBalance(UUID uuid, double amount) {
        addBalance(uuid, PRIMARY_CURRENCY, amount);
    }

    public boolean removeBalance(UUID uuid, String currency, double amount) {
        if (amount <= 0) return true;
        double current = getBalance(uuid, currency);
        if (current < amount) return false;
        setBalance(uuid, currency, current - amount);
        return true;
    }

    public boolean removeBalance(UUID uuid, double amount) {
        return removeBalance(uuid, PRIMARY_CURRENCY, amount);
    }

    public boolean has(UUID uuid, String currency, double amount) {
        return getBalance(uuid, currency) >= amount;
    }

    public boolean has(UUID uuid, double amount) {
        return has(uuid, PRIMARY_CURRENCY, amount);
    }

    public boolean ensureDefaultBalance(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT balance FROM " + TABLE + " WHERE uuid = ? AND currency = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, PRIMARY_CURRENCY);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return false;
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] ensureDefaultBalance check error: " + e.getMessage());
        }
        setBalance(uuid, PRIMARY_CURRENCY, DEFAULT_BALANCE);
        ConsoleLogger.info("[Economy] Created default balance (" + DEFAULT_BALANCE + ") for " + uuid);
        return true;
    }

    public Map<String, Double> getAllBalances(UUID uuid) {
        Map<String, Double> result = new LinkedHashMap<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT currency, balance FROM " + TABLE + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("currency"), rs.getDouble("balance"));
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] getAllBalances error: " + e.getMessage());
        }
        return result;
    }

    public String getPrimaryCurrency() {
        return PRIMARY_CURRENCY;
    }

    public double getDefaultBalance() {
        return DEFAULT_BALANCE;
    }

    public void resetBalance(UUID uuid, String currency) {
        setBalance(uuid, currency, 0.0);
    }

    public void resetBalance(UUID uuid) {
        resetBalance(uuid, PRIMARY_CURRENCY);
    }
}
