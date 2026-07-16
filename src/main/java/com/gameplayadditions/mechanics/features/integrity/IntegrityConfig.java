package com.gameplayadditions.mechanics.features.integrity;

import java.util.HashSet;
import java.util.Set;

/**
 * 🛡 Integrity Config — статический POJO с настройками системы целостности.
 * <p>
 * Заменяет {@code com.mcplugin.mechanics.features.integrity.IntegrityConfig}
 * (Bukkit, 207 строк, YAML loader).
 * <p>
 * MVP: skip TOML loader — все поля инициализируются значениями по умолчанию.
 * TODO: добавить {@code loadFromConfig(ModConfig)} через {@code ModConfigSpec.Builder}
 *       для горячей перезагрузки конфигурации.
 */
public final class IntegrityConfig {

    private IntegrityConfig() {}

    /** Версия системы целостности (для детекта миграции). V3 = процентная система. */
    public static final int INTEGRITY_VERSION = 3;

    // =========================
    // ОСНОВНЫЕ НАСТРОЙКИ
    // =========================
    public static boolean enabled = true;
    public static int intervalTicks = 10;
    public static double costMultiplier = 1.0;

    // =========================
    // HEX ГРАДИЕНТ (100% → тёмно-зелёный, 0% → тёмно-красный)
    // =========================
    public static int gradientRedHigh = 0x00;
    public static int gradientGreenHigh = 0x66;
    public static int gradientBlueHigh = 0x00;
    public static int gradientRedLow = 0x99;
    public static int gradientGreenLow = 0x00;
    public static int gradientBlueLow = 0x00;

    // Текст лора
    public static String loreText = "§7Целостность:";
    public static String bareLorePrefix = "Целостность:";

    // =========================
    // ПОВЕДЕНИЕ ПРИ ПОЛОМКЕ
    // =========================
    public static boolean breakPlaySound = true;
    public static boolean breakSendMessage = true;
    public static String breakMessage = "<dark_red>❌</dark_red> <red>Ваш предмет</red> <white>{item}</white> <red>сломался!</red>";
    public static String breakSoundName = "ITEM_BREAK";
    public static float breakSoundVolume = 1.0f;
    public static float breakSoundPitch = 1.0f;

    // =========================
    // ЛОГИРОВАНИЕ
    // =========================
    public static boolean logInit = false;
    public static boolean logBreak = true;
    public static boolean logErrors = false;

    // =========================
    // ФИЛЬТРЫ (по registry-name предмета, например "minecraft:diamond_sword")
    // =========================
    public static Set<String> blacklist = new HashSet<>();
    public static Set<String> whitelist = new HashSet<>();

    // =========================
    // XP → ЦЕЛОСТНОСТЬ (сбор опыта восстанавливает целостность)
    // =========================
    public static boolean xpIntegrityEnabled = true;
    public static double xpIntegrityPerXp = 0.1;
    public static String xpIntegrityMessage = "<green>✨</green> <white>Сбор опыта восстановил</white> <yellow>{amount}%</yellow> <white>целостности!</white>";

    // =========================
    // LOW INTEGRITY WARNING
    // =========================
    public static boolean lowIntegrityWarningEnabled = true;
    public static java.util.List<Integer> lowIntegrityThresholds = java.util.List.of(5, 10, 25, 50, 75);
    public static String lowIntegrityWarningMessage = "<yellow>⚠</yellow> <white>Ваш предмет</white> <yellow>{item}</yellow> <white>имеет</white> <red>{pct}%</red> <white>целостности!</white>";

    // =========================
    // РЕМОНТ В НАКОВАЛЬНЕ (⚠ MVP: пока не реализовано, см. TODO)
    // =========================
    public static boolean anvilRepairEnabled = true;
    public static double anvilRepairMultiplier = 0.25;
    public static boolean anvilCombineEnabled = true;
    public static double anvilCombineBonus = 0.1;

    public static boolean anvilMaterialCraftEnabled = true;
    public static double anvilMaterialCraftBonus = 10.0;
    public static String anvilMaterialCraftMessage = "<green>🔨</green> <white>Создан новый предмет! Целостность:</white> <yellow>{current}%</yellow> <white>(+{bonus}% за материалы)</white>";

    // =========================
    // XP + MENDING (бонус зачарования)
    // =========================
    public static boolean mendingXpEnabled = true;
    public static double mendingXpMultiplier = 0.5;
    public static String mendingMessage = "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>{amount}%</yellow> <white>целостности!</white>";

    // =========================
    // UNBREAKING
    // =========================
    public static boolean unbreakingEnabled = true;

    // =========================
    // PIERCING
    // =========================
    public static boolean piercingEnabled = true;
    public static double piercingExtraCost = 0.5;

    // =========================
    // КРАФТ / ОБЪЕДИНЕНИЕ
    // =========================
    public static boolean combineEnabled = true;
    public static double combineLossRate = 0.0;

    public static String anvilRepairMessage = "<green>🔧</green> <white>Целостность восстановлена до</white> <yellow>{current}%</yellow><white>!</white>";
    public static String anvilCombineMessage = "<green>🔗</green> <white>Предметы объединены! Целостность:</white> <yellow>{current}%</yellow><white></white>";

    // =========================
    // Загрузчик из конфига (TODO: ModConfigSpec)
    // При инициализации — оставляем дефолты; можно перегрузить через команду /config
    // =========================
    public static void loadFromConfig() {
        // No-op для MVP. TODO: прокинуть значения из TOML через ModConfigSpec.Builder.
    }
}
