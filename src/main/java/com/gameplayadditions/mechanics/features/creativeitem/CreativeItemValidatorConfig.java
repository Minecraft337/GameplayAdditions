package com.gameplayadditions.mechanics.features.creativeitem;

/**
 * CreativeItemValidator Config — статический POJO с настройками валидатора.
 * <p>
 * Порт секции {@code features.creative_item_validator} из MC-Plugin {@code config.yml}.
 * <p>
 * Содержит лимиты, при превышении которых ItemStack считается краш-предметом
 * (вызывающим чрезмерный расход ОЗУ / лаг-спайки / OOM) и удаляется из инвентаря
 * игрока в creative-режиме.
 * <p>
 * MVP: ModConfigSpec загрузчик не реализован, поля инициализируются значениями
 * по умолчанию, совпадающими с MC-Plugin defaults.
 */
public final class CreativeItemValidatorConfig {

    private CreativeItemValidatorConfig() {}

    // =========================
    // ОСНОВНЫЕ НАСТРОЙКИ
    // =========================
    /** Глобальный master-switch фичи. */
    public static boolean enabled = true;

    /** Размер сериализованного одного предмета в байтах (Bukkit «serializeAsBytes»). */
    public static int maxItemBytes = 8192;          // 8 KB

    /** Суммарный NBT-размер по всему дереву вложенных контейнеров. */
    public static int maxTotalNbtSize = 32768;      // 32 KB

    /** Максимальная глубина рекурсии (shulker-в-shulker-...). */
    public static int maxRecursionDepth = 8;

    /** Максимум ключей в PDC (Bukkit PersistentDataContainer / 1.21+ DataComponents.CUSTOM_DATA). */
    public static int maxPdcKeys = 30;

    /** Максимум строк lore. */
    public static int maxLoreLines = 50;

    /** Суммарная длина всех строк lore (UTF-16 кодовые единицы). */
    public static int maxLoreChars = 1000;

    /** Длина имени предмета (displayName). */
    public static int maxNameChars = 200;

    /** Количество зачарований. */
    public static int maxEnchantments = 40;

    /** Уровень permission (op-level), дающий bypass валидатора. 2 = стандартный op. */
    public static int bypassPermissionLevel = 2;

    /** Как часто сканировать creative-mode игроков (тиков). 20 = раз в секунду. */
    public static int scanIntervalTicks = 20;

    /** Cooldown между сообщениями игроку (миллисекунды). */
    public static long messageCooldownMs = 2000L;

    /**
     * Bukkit эвивалент: <code>&lt;red&gt;Этот предмет содержит слишком много данных!&lt;/red&gt;</code>
     * <p>
     * В vanilla 1.21 поддерживается MiniMessage в чате через серверные теги.
     */
    public static String denyMessage =
            "<red>Этот предмет содержит слишком много данных!</red>";

    public static void loadFromConfig() {
        // No-op для MVP. Поля при необходимости перегружаются через /config команду.
    }
}
