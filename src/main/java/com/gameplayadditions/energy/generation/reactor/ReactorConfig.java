package com.gameplayadditions.energy.generation.reactor;

/**
 * ReactorConfig — конфигурация реактора (хардкодные значения).
 * <p>
 * Порт {@code com.mcplugin.energy.generation.reactor.ReactorConfig} из MC-Plugin.
 * Вместо Bukkit config.yml использует константы (можно заменить на ConfigManager позже).
 */
public class ReactorConfig {

    // =========================
    // SINGLETON
    // =========================
    private static ReactorConfig instance;

    public static ReactorConfig getInstance() {
        return instance;
    }

    public static void init() {
        instance = new ReactorConfig();
    }

    // =========================
    // CONFIG FIELDS
    // =========================
    private final boolean enabled = true;
    private final int tempDecayRate = 1;
    private final int heatRate = 3;
    private final int coolRate = 3;
    private final int coreTempMax = 6000;
    private final int coreTempMin = -272;
    private final int coreTempCoolMin = -270;
    private final int corePressReduceRate = 1;
    private final int caseTempHeatRate = 2;
    private final int caseTempMax = 8000;
    private final int caseTempCoolRate = 2;
    private final int caseTempCoolMin = -271;
    private final int caseTempDecayRate = 1;
    private final int casePressHeatRate = 4;
    private final int casePressMax = 10000;
    private final int casePressDecayRate = 1;
    private final int shIntDecayTempThreshold = 5000;
    private final int shellIntDecayRate = 1;
    private final int shellIntRecoveryTempMax = 4999;
    private final int shellIntRecoveryRate = 1;
    private final int caseIntDecayPressThreshold = 7000;
    private final int caseIntDecayTempThreshold = 7000;
    private final int caseIntDecayPressRate = 1;
    private final int caseIntDecayTempRate = 1;
    private final int caseIntRecoveryPressMax = 7000;
    private final int caseIntRecoveryTempMax = 4999;
    private final int caseIntRecoveryRate = 1;
    private final boolean wearEnabled = true;
    private final int wearIntervalNormal = 1200;
    private final int wearIntervalDegradation = 20;
    private final int wearChatCountdown = 30;
    private final int wearFinalMeltdownAt = 11;
    private final int wearFinalMeltdownDuration = 10;
    private final int meltdownExplosionRadius = 128;
    private final int recipeTimeMax = 100;

    private ReactorConfig() {}

    // =========================
    // GETTERS
    // =========================
    public boolean isEnabled() { return enabled; }
    public int getTempDecayRate() { return tempDecayRate; }
    public int getHeatRate() { return heatRate; }
    public int getCoolRate() { return coolRate; }
    public int getCoreTempMax() { return coreTempMax; }
    public int getCoreTempMin() { return coreTempMin; }
    public int getCoreTempCoolMin() { return coreTempCoolMin; }
    public int getCorePressReduceRate() { return corePressReduceRate; }
    public int getCaseTempHeatRate() { return caseTempHeatRate; }
    public int getCaseTempMax() { return caseTempMax; }
    public int getCaseTempCoolRate() { return caseTempCoolRate; }
    public int getCaseTempCoolMin() { return caseTempCoolMin; }
    public int getCaseTempDecayRate() { return caseTempDecayRate; }
    public int getCasePressHeatRate() { return casePressHeatRate; }
    public int getCasePressMax() { return casePressMax; }
    public int getCasePressDecayRate() { return casePressDecayRate; }
    public int getShIntDecayTempThreshold() { return shIntDecayTempThreshold; }
    public int getShellIntDecayRate() { return shellIntDecayRate; }
    public int getShellIntRecoveryTempMax() { return shellIntRecoveryTempMax; }
    public int getShellIntRecoveryRate() { return shellIntRecoveryRate; }
    public int getCaseIntDecayPressThreshold() { return caseIntDecayPressThreshold; }
    public int getCaseIntDecayTempThreshold() { return caseIntDecayTempThreshold; }
    public int getCaseIntDecayPressRate() { return caseIntDecayPressRate; }
    public int getCaseIntDecayTempRate() { return caseIntDecayTempRate; }
    public int getCaseIntRecoveryPressMax() { return caseIntRecoveryPressMax; }
    public int getCaseIntRecoveryTempMax() { return caseIntRecoveryTempMax; }
    public int getCaseIntRecoveryRate() { return caseIntRecoveryRate; }
    public boolean isWearEnabled() { return wearEnabled; }
    public int getWearIntervalNormal() { return wearIntervalNormal; }
    public int getWearIntervalDegradation() { return wearIntervalDegradation; }
    public int getWearChatCountdown() { return wearChatCountdown; }
    public int getWearFinalMeltdownAt() { return wearFinalMeltdownAt; }
    public int getWearFinalMeltdownDuration() { return wearFinalMeltdownDuration; }
    public int getMeltdownExplosionRadius() { return meltdownExplosionRadius; }
    public int getRecipeTimeMax() { return recipeTimeMax; }
}
