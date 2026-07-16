package com.gameplayadditions.mechanics.features.integrity;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 🛡 Integrity System — портированное ядро системы целостности.
 * <p>
 * Заменяет {@code com.mcplugin.mechanics.features.integrity.IntegrityManager}
 * (Bukkit, ~1003 LOC, extends BukkitRunnable). Все методы — static, вызываются из фич.
 * <p>
 * MVP simplifications vs source:
 * <ul>
 *   <li>Lore rendering — отключён (TODO: перевод на Minecraft DataComponents.LORE API в
 *       релизе, когда прояснится пакетный импорт 1.21).</li>
 *   <li>Vanilla UNBREAKABLE check — отключён (только custom PDE тег "unbreakable").</li>
 *   <li>Enchantment lookups — через {@link EnchantmentHelper#getEnchantments(ItemStack)}
 *       + {@code Holder.unwrapKey()}, без {@link net.minecraft.core.registries.BuiltInRegistries#ENCHANTMENT}.</li>
 * </ul>
 */
public final class IntegritySystem {

    private static final Logger LOGGER = LoggerFactory.getLogger("GameplayAdditions/integrity");
    private static final DecimalFormat PCT_FMT = new DecimalFormat("0.000");
    private static final Pattern HEX_PATTERN = Pattern.compile("#?([0-9a-fA-F]{6})");

    /** Корневой CompoundTag бьющий все integrity-поля внутри {@link DataComponents#CUSTOM_DATA}. */
    private static final String ROOT_TAG = "integrity";
    private static final String K_TAG = "tag";
    private static final String K_VERSION = "version";
    private static final String K_MAX = "max";
    private static final String K_CURRENT = "current";
    private static final String K_UNBREAKABLE = "unbreakable";
    private static final String K_LAST_SEEN = "last_seen";
    private static final String K_WARN_FLAGS = "warn_flags";

    // === PIERCING flag (consumed in decreaseIntegrity) ===
    // BUG-FIX (item 2026): замена голого boolean на AtomicBoolean.
    // 1) Без volatile/атомарности возникала visibility-проблема: тик-логика в ServerTick
    //    могла прочитать stale true когда DamageEvent уже откатил флаг.
    // 2) "Last-writer-wins" для нескольких damage events в одном тике все еще
    //    существует (сохраняем Bukkit-семантику) — это известное ограничение,
    //    будущее TODO — передавать piercingLevel как параметр из DamageEvent напрямую.
    private static final AtomicBoolean piercingActive = new AtomicBoolean(false);

    public static boolean isPiercingActive() { return piercingActive.get(); }
    public static void setPiercingActive(boolean active) { piercingActive.set(active); }

    private IntegritySystem() {}

    // =====================================================================
    //  ВЕРХНЕ-УРОВНЕВЫЙ API
    // =====================================================================

    public static boolean isEnabled() { return IntegrityConfig.enabled; }

    /** Tick-booster: для каждого игрока проходится по инвентарю и прогоняет {@link #processItem}. */
    public static void processInventoryForAllPlayers(List<ServerPlayer> players) {
        if (!IntegrityConfig.enabled) return;
        piercingActive.set(false);
        for (ServerPlayer player : players) {
            try {
                processPlayerInventory(player);
            } catch (Exception e) {
                if (IntegrityConfig.logErrors) {
                    LOGGER.warn("[INTEGRITY] Error processing inventory for {}: {}",
                            player.getName().getString(), e.getMessage());
                }
            }
        }
    }

    private static void processPlayerInventory(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i <= 40; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            try {
                processItem(stack);
                checkLowIntegrityWarning(stack, player);
            } catch (Exception e) {
                if (IntegrityConfig.logErrors) {
                    LOGGER.warn("[INTEGRITY] Error slot {}: {}", i, e.getMessage());
                }
            }
        }
    }

    /**
     * Инициализация предмета + миграция со старых версий. Возвращает true, если были
     * изменения (для отладки/логирования — в tick-loop игнорируется).
     */
    public static boolean processItem(ItemStack item) {
        if (isUnbreakable(item)) {
            ensureIntegrityAt(item, 100.0);
            return true;
        }
        if (getMaxDurability(item) <= 0) return false;
        if (!isItemApplicable(item)) return false;

        CompoundTag tag = readCustomDataTag(item);
        boolean isTagged = tag.contains(K_TAG);
        int storedVersion = tag.contains(K_VERSION) ? tag.getInt(K_VERSION) : 0;
        boolean migrated = false;

        // === МИГРАЦИЯ V1/V2 → V3 ===
        if (isTagged && storedVersion < IntegrityConfig.INTEGRITY_VERSION) {
            double oldMax = tag.contains(K_MAX) ? tag.getDouble(K_MAX) : 0.0;
            double oldCurrent = tag.contains(K_CURRENT) ? tag.getDouble(K_CURRENT) : 0.0;
            double newCurrent;
            if (oldMax == 100.0) {
                newCurrent = clampPct(oldCurrent);
            } else if (oldMax > 0) {
                newCurrent = clampPct((oldCurrent / oldMax) * 100.0);
            } else {
                newCurrent = 100.0;
            }
            writeCustomDataTag(item, buildTag(IntegrityConfig.INTEGRITY_VERSION, 100.0, newCurrent, 0));
            migrated = true;
            if (IntegrityConfig.logInit) {
                LOGGER.info("[INTEGRITY] Migrated {} → V3 (current={}%)",
                        registryId(item), formatPct(newCurrent));
            }
        }

        if (!isTagged) {
            writeCustomDataTag(item, buildTag(IntegrityConfig.INTEGRITY_VERSION, 100.0, 100.0, 0));
            if (IntegrityConfig.logInit) {
                LOGGER.info("[INTEGRITY] Initialized {} (current=100.0%)", registryId(item));
            }
        }

        return migrated;
    }

    public static boolean hasIntegrity(ItemStack item) {
        if (item.isEmpty()) return false;
        CompoundTag tag = readCustomDataTag(item);
        return tag.contains(K_TAG) && tag.contains(K_MAX);
    }

    public static double getCurrentIntegrity(ItemStack item) {
        if (!hasIntegrity(item)) return -1.0;
        return readCustomDataTag(item).getDouble(K_CURRENT);
    }

    public static double getMaxIntegrity() { return 100.0; }

    /**
     * MVP: только custom PDE тег "unbreakable" внутри ROOT_TAG. Vanilla unbreakable игнорируется.
     * Документация на custom override:
     * {@code item.getOrDefault(DataComponents.CUSTOM_DATA, ...).copyTag()
     *        .getCompound("integrity").getBoolean("unbreakable")}
     */
    public static boolean isUnbreakable(ItemStack item) {
        if (item.isEmpty()) return false;
        return readCustomDataTag(item).getBoolean(K_UNBREAKABLE);
    }

    public static void setUnbreakable(ItemStack item, boolean flag) {
        if (item.isEmpty()) return;
        CompoundTag tag = readCustomDataTag(item);
        tag.putBoolean(K_UNBREAKABLE, flag);
        writeCustomDataTag(item, tag);
    }

    public static void setCurrentIntegrity(ItemStack item, double value) {
        if (!isItemApplicable(item)) return;
        if (!hasIntegrity(item)) return;
        CompoundTag tag = readCustomDataTag(item);
        tag.putDouble(K_CURRENT, clampPct(value));
        writeCustomDataTag(item, tag);
        syncVanillaDamage(item);
    }

    public static void increaseIntegrity(ItemStack item, double amount) {
        if (!isItemApplicable(item)) return;
        if (!hasIntegrity(item)) return;
        CompoundTag tag = readCustomDataTag(item);
        double current = tag.getDouble(K_CURRENT);
        if (current >= 100.0) return;
        double next = Math.min(100.0, current + amount);
        tag.putDouble(K_CURRENT, next);
        writeCustomDataTag(item, tag);
        syncVanillaDamage(item);
    }

    public static void decreaseIntegrity(ItemStack item, double amount, Player owner) {
        if (isUnbreakable(item)) return;
        if (item.isEmpty()) return;
        if (!isItemApplicable(item)) return;

        CompoundTag tag;
        if (!hasIntegrity(item)) {
            writeCustomDataTag(item, buildTag(IntegrityConfig.INTEGRITY_VERSION, 100.0, 100.0, 0));
            tag = readCustomDataTag(item);
        } else {
            tag = readCustomDataTag(item);
        }

        double current = tag.getDouble(K_CURRENT);
        if (current <= 0) return;

        int maxDura = getMaxDurability(item);
        if (maxDura <= 0) return;

        double cost = (amount / (double) maxDura) * 100.0 * IntegrityConfig.costMultiplier * amount;

        if (IntegrityConfig.piercingEnabled && isPiercingActive()) {
            cost += IntegrityConfig.piercingExtraCost;
        }

        if (IntegrityConfig.unbreakingEnabled) {
            int unbreakingLevel = getEnchantmentLevelByKey(item, "minecraft:unbreaking");
            if (unbreakingLevel > 0 && Math.random() > 1.0 / (unbreakingLevel + 1.0)) {
                writeCustomDataTag(item, tag);
                return;
            }
        }

        double newVal = Math.max(0.0, current - cost);
        tag.putDouble(K_CURRENT, newVal);
        writeCustomDataTag(item, tag);
        syncVanillaDamage(item);

        if (newVal <= 0) {
            breakItem(item, owner);
        } else {
            checkLowIntegrityWarningFromTag(item, owner, tag);
        }
    }

    public static boolean isItemApplicable(ItemStack item) {
        if (item.isEmpty()) return false;
        if (getMaxDurability(item) <= 0) return false;
        String id = registryId(item);
        if (!IntegrityConfig.whitelist.isEmpty() && !IntegrityConfig.whitelist.contains(id)) return false;
        return !IntegrityConfig.blacklist.contains(id);
    }

    public static int getMaxDurability(ItemStack item) {
        if (item.isEmpty()) return 0;
        return item.getOrDefault(DataComponents.MAX_DAMAGE, 0);
    }

    public static void restoreMendingXp(Player player, int xpAmount) {
        if (!IntegrityConfig.mendingXpEnabled || xpAmount <= 0) return;
        double totalPct = IntegrityConfig.mendingXpMultiplier * xpAmount;
        if (totalPct <= 0) return;

        var inv = player.getInventory();
        boolean any = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !hasIntegrity(stack)) continue;
            if (getEnchantmentLevelByKey(stack, "minecraft:mending") <= 0) continue;
            increaseIntegrity(stack, totalPct);
            any = true;
        }
        if (any && IntegrityConfig.breakSendMessage) {
            String msg = IntegrityConfig.mendingMessage.replace("{amount}", formatPct(totalPct));
            // simple text — color tags via stripTags() → Component.literal.
            player.displayClientMessage(Component.literal(stripTags(msg)), false);
        }
    }

    public static int getPiercingLevelForItem(ItemStack stack) {
        return getEnchantmentLevelByKey(stack, "minecraft:piercing");
    }

    // =====================================================================
    //  WAL helpers (PDE → CompoundTag reads / writes)
    // =====================================================================

    private static CompoundTag readCustomDataTag(ItemStack item) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (data != null) ? data.copyTag() : new CompoundTag();
        if (!root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            root.put(ROOT_TAG, new CompoundTag());
        }
        return root.getCompound(ROOT_TAG);
    }

    private static void writeCustomDataTag(ItemStack item, CompoundTag integrityTag) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (data != null) ? data.copyTag() : new CompoundTag();
        root.put(ROOT_TAG, integrityTag);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static CompoundTag buildTag(int version, double max, double current, int warnFlags) {
        CompoundTag t = new CompoundTag();
        t.putByte(K_TAG, (byte) 1);
        t.putInt(K_VERSION, version);
        t.putDouble(K_MAX, max);
        t.putDouble(K_CURRENT, current);
        t.putInt(K_WARN_FLAGS, warnFlags);
        return t;
    }

    private static void ensureIntegrityAt(ItemStack item, double pct) {
        CompoundTag tag = readCustomDataTag(item);
        if (!tag.contains(K_TAG)) {
            tag.putByte(K_TAG, (byte) 1);
            tag.putInt(K_VERSION, IntegrityConfig.INTEGRITY_VERSION);
            tag.putDouble(K_MAX, 100.0);
            tag.putDouble(K_CURRENT, pct);
            writeCustomDataTag(item, tag);
        }
        syncVanillaDamage(item);
    }

    /**
     * Зеркалим vanilla damage = 0. Вся логика износа теперь живёт в IntegritySystem.
     * Если предмет не имеет прочности, setDamageValue просто игнорируется.
     */
    private static void syncVanillaDamage(ItemStack item) {
        item.setDamageValue(0);
    }

    private static void breakItem(ItemStack item, Player owner) {
        String itemName = getItemName(item);
        item.setCount(0);
        if (owner == null) return;

        if (IntegrityConfig.breakPlaySound) {
            SoundEvent sound = lookupSound(IntegrityConfig.breakSoundName, SoundEvents.ITEM_BREAK);
            owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    sound, owner.getSoundSource(),
                    IntegrityConfig.breakSoundVolume, IntegrityConfig.breakSoundPitch);
        }
        if (IntegrityConfig.breakSendMessage) {
            String msg = IntegrityConfig.breakMessage.replace("{item}", itemName);
            owner.displayClientMessage(Component.literal(stripTags(msg)), false);
        }
        if (IntegrityConfig.logBreak) {
            ConsoleLogger.info("[INTEGRITY] " + owner.getName().getString() + "'s " + itemName + " broke!");
        }
    }

    private static void checkLowIntegrityWarning(ItemStack item, Player player) {
        if (!IntegrityConfig.lowIntegrityWarningEnabled) return;
        if (item.isEmpty() || getMaxDurability(item) <= 0) return;
        if (!hasIntegrity(item)) return;
        CompoundTag tag = readCustomDataTag(item);
        checkLowIntegrityWarningFromTag(item, player, tag);
    }

    private static void checkLowIntegrityWarningFromTag(ItemStack item, Player player, CompoundTag tag) {
        if (item.isEmpty() || getMaxDurability(item) <= 0) return;
        if (!tag.contains(K_TAG)) return;
        int oldFlags = tag.contains(K_WARN_FLAGS) ? tag.getInt(K_WARN_FLAGS) : 0;
        double current = tag.getDouble(K_CURRENT);
        double max = tag.getDouble(K_MAX);
        if (max <= 0) return;
        double pct = clampPct((current / max) * 100.0);

        int warnFlags = oldFlags;
        boolean warned = false;
        boolean wasZero = (oldFlags == 0);

        for (int i = 0; i < IntegrityConfig.lowIntegrityThresholds.size(); i++) {
            int threshold = IntegrityConfig.lowIntegrityThresholds.get(i);
            int bit = 1 << i;

            if (pct <= threshold && (warnFlags & bit) == 0) {
                warnFlags |= bit;
                warned = true;
            }
            if (pct > threshold && (warnFlags & bit) != 0) {
                warnFlags &= ~bit;
            }
        }
        if (warned) {
            tag.putInt(K_WARN_FLAGS, warnFlags);
            writeCustomDataTag(item, tag);
            if (!wasZero) {
                String itemName = getItemName(item);
                String msg = IntegrityConfig.lowIntegrityWarningMessage
                        .replace("{item}", itemName)
                        .replace("{pct}", formatPct(pct));
                player.displayClientMessage(Component.literal(stripTags(msg)), false);
            }
        }
    }

    // =====================================================================
    //  Enchantment lookups (PIERCING / UNBREAKING / MENDING)
    // =====================================================================

    private static int getEnchantmentLevelByKey(ItemStack item, String keyStr) {
        ResourceLocation key = ResourceLocation.tryParse(keyStr);
        if (key == null) return 0;
        ItemEnchantments enchs = item.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchs.entrySet()) {
            ResourceKey<Enchantment> rk = entry.getKey().unwrapKey().orElse(null);
            if (rk != null && rk.location().equals(key)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    // =====================================================================
    //  Утилиты
    // =====================================================================

    private static double clampPct(double v) { return Math.max(0.0, Math.min(100.0, v)); }
    private static int clampByte(int v) { return Math.max(0, Math.min(0xFF, v)); }
    private static String formatPct(double v) { return PCT_FMT.format(v); }

    private static String registryId(ItemStack item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(item.getItem()).toString();
    }

    private static String getItemName(ItemStack item) {
        String s = item.getHoverName().getString();
        if (!s.isEmpty()) return s;
        return registryId(item);
    }

    /** Удаляет §-цвета из Component-строки. */
    private static String stripStyle(Object input) {
        if (input instanceof Component c) return c.getString().replaceAll("§.", "");
        return String.valueOf(input).replaceAll("§.", "");
    }

    /** Конвертирует Bukkit-цветовые тэги (<dark_red>, <green>, ...) в §-коды. */
    private static String stripTags(String input) {
        return input
                .replaceAll("<dark_red>", "§4")
                .replaceAll("<red>", "§c")
                .replaceAll("<gold>", "§6")
                .replaceAll("<yellow>", "§e")
                .replaceAll("<green>", "§a")
                .replaceAll("<aqua>", "§b")
                .replaceAll("<blue>", "§9")
                .replaceAll("<white>", "§f")
                .replaceAll("<black>", "§0")
                .replaceAll("<gray>", "§7")
                .replaceAll("<dark_gray>", "§8")
                .replaceAll("<.*?>", "");
    }

    private static SoundEvent lookupSound(String name, SoundEvent fallback) {
        return switch (name) {
            case "ITEM_BREAK", "ENTITY_ITEM_BREAK" -> SoundEvents.ITEM_BREAK;
            default -> fallback;
        };
    }

    public static String formatPercent(double pct) { return formatPct(pct); }

    /** MVP: HEX → RGB linear gradient, exposed для будущего lore-rendering. */
    public static int[] parseHexColor(String hex) {
        if (hex == null) return null;
        var m = HEX_PATTERN.matcher(hex.trim());
        if (!m.matches()) return null;
        String clean = m.group(1);
        try {
            return new int[]{
                    Integer.parseInt(clean.substring(0, 2), 16),
                    Integer.parseInt(clean.substring(2, 4), 16),
                    Integer.parseInt(clean.substring(4, 6), 16)
            };
        } catch (Exception e) { return null; }
    }
}
