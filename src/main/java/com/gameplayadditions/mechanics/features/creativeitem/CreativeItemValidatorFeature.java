package com.gameplayadditions.mechanics.features.creativeitem;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 🎨 CreativeItemValidator Feature — anti-crash-item enforcement в creative-режиме.
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.creativeitem.CreativeItemValidator}
 * (Bukkit, ~260 строк). Защищает сервер от «краш-предметов» — ItemStack с
 * гигантским NBT (включая shulker-в-shulker-в-shulker...), которые при загрузке
 * забивают ОЗУ и лагают / крашат сервер.
 * <p>
 * Архитектурное отличие от Bukkit-оригинала:<br>
 * Bukkit ловил <b>клик в creative-инвентаре</b> через {@code InventoryCreativeEvent}
 * (priority=LOWEST) и блокировал его «cancel» на сервере. В NeoForge 1.21 такой
 * event с nuance-LOWEST приоритетом + per-packet interception НЕ доступен без
 * Mixin. Принятая стратегия: <b>Strategy B — post-tick inventory scan</b>.
 * <p>
 * Каждые {@code scanIntervalTicks} тиков обходим инвентари всех creative-mode
 * игроков. При нахождении «too-big» ItemStack — удаляем копию из инвентаря +
 * лог + сообщение игроку с cooldown-ом. Семантически эквивалентно «blockItem»
 * в Bukkit: после проверки в следующем-же тике игрок не имеет доступа к
 * «краш-предмету» в инвентаре.
 *
 * <p><b>Гарантия server-side:</b> {@code PlayerTickEvent.Post} срабатывает только
 * на логическом сервере + явный {@code isClientSide()} guard.</p>
 *
 * <p>Проверки (зеркальные Bukkit-имплементации):</p>
 * <ol>
 *   <li>Рекурсивный обход вложенных контейнеров (shulker / bundle)</li>
 *   <li>Глубина рекурсии ≤ maxRecursionDepth</li>
 *   <li>Размер сериализованного ItemStack CompoundTag в байтах</li>
 *   <li>Длина имени (displayName)</li>
 *   <li>Кол-во и суммарная длина строк lore</li>
 *   <li>Кол-во зачарований</li>
 *   <li>Кол-во PDC-ключей (CUSTOM_DATA tag)</li>
 * </ol>
 */
public class CreativeItemValidatorFeature extends AbstractFeature {

    /** Per-player cooldown для сообщений об отказе. */
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();

    /** Счётчик тиков от последнего сканирования. */
    private long tickCounter = 0L;

    /** Кэш registry access для парсинга inner ItemStack (нужен для ItemStack.parseOptional). */
    private HolderLookup.Provider registryAccess = null;

    @Override
    public String getName() {
        return "creative_item_validator";
    }

    @Override
    public void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        CreativeItemValidatorConfig.loadFromConfig();
        logInfo("setup complete (defaults loaded; modulo ModConfigSpec hot-reload).");
    }

    @Override
    public void onServerStart(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        super.onServerStart(event);
        MinecraftServer server = event.getServer();
        this.registryAccess = server.registryAccess();
        registerGameEvents();
        logInfo("subscribed to PlayerTickEvent.Post; scanInterval="
                + CreativeItemValidatorConfig.scanIntervalTicks + " ticks.");
    }

    @Override
    public void onServerStop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        logInfo("scan counters cleared (last players=" + lastMessageTime.size() + " entries).");
        super.onServerStop(event);
    }

    /**
     * Post-tick срабатывает ПОСЛЕ игровой логики тика. Здесь мы безопасно
     * читаем слоты и удаляем нарушителей.
     */
    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        // 🚫 Server-only enforcement. У NeoForge PlayerTickEvent fires server-side,
        //    но isClientSide() guard — defense-in-depth от регрессий.
        if (event.getEntity().level().isClientSide()) return;
        if (!CreativeItemValidatorConfig.enabled) return;
        if (!isRunning()) return;
        if (registryAccess == null) return; // Инвалидация до ServerStartingEvent — пропускаем

        tickCounter++;
        if (tickCounter < CreativeItemValidatorConfig.scanIntervalTicks) return;
        tickCounter = 0L;

        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        if (!sp.gameMode.isCreative()) return;        // ТОЛЬКО creative-mode
        if (sp.hasPermissions(CreativeItemValidatorConfig.bypassPermissionLevel)) return;

        validatePlayerInventory(sp);
    }

    // =========================================================
    // SCAN HELPERS
    // =========================================================

    /** Walk main + armor + offhand slots. Stop after first match to avoid spam. */
    private void validatePlayerInventory(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().items.size(); i++) {
            if (blockAndRemoveIfIll(sp, sp.getInventory().items.get(i))) return;
        }
        for (int i = 0; i < sp.getInventory().armor.size(); i++) {
            if (blockAndRemoveIfIll(sp, sp.getInventory().armor.get(i))) return;
        }
        if (blockAndRemoveIfIll(sp, sp.getInventory().offhand.get(0))) return;
    }

    // =========================================================
    // VALIDATION CORE (mirrors Bukkit 6-method order)
    // =========================================================

    /**
     * @return true если найден и удалён «плохой» ItemStack (→ stop scanning).
     */
    private boolean blockAndRemoveIfIll(ServerPlayer sp, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // ─── Шаг 1: рекурсивный обход вложенных контейнеров ──────────────
        NbtStats stats = countNbtRecursive(stack, 0);
        if (stats.exceeded) {
            return veto(sp, stack, "recursiveNbt=" + stats.totalBytes + "/"
                    + CreativeItemValidatorConfig.maxTotalNbtSize
                    + " depth=" + stats.maxDepth + "/"
                    + CreativeItemValidatorConfig.maxRecursionDepth
                    + " containers=" + stats.containerCount);
        }

        // ─── Шаг 2: размер сериализованного предмета ─────────────────────
        int byteSize = estimateSerializedBytes(stack);
        if (byteSize > CreativeItemValidatorConfig.maxItemBytes) {
            return veto(sp, stack, "bytes=" + byteSize
                    + "/" + CreativeItemValidatorConfig.maxItemBytes);
        }

        // ─── Шаг 3: длина имени (CUSTOM_NAME → Component) ───────────────
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            int nameLen = customName.getString().length();
            if (nameLen > CreativeItemValidatorConfig.maxNameChars) {
                return veto(sp, stack, "name=" + nameLen
                        + "/" + CreativeItemValidatorConfig.maxNameChars);
            }
        }

        // ─── Шаг 4: lore (lines + chars sum) ─────────────────────────────
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            int lines = lore.lines().size();
            if (lines > CreativeItemValidatorConfig.maxLoreLines) {
                return veto(sp, stack, "loreLines=" + lines
                        + "/" + CreativeItemValidatorConfig.maxLoreLines);
            }
            int chars = 0;
            for (Component line : lore.lines()) chars += line.getString().length();
            if (chars > CreativeItemValidatorConfig.maxLoreChars) {
                return veto(sp, stack, "loreChars=" + chars
                        + "/" + CreativeItemValidatorConfig.maxLoreChars);
            }
        }

        // ─── Шаг 5: кол-во зачарований (ItemEnchantments.size) ───────────
        ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
        if (ench != null) {
            int enchCount = ench.size();
            if (enchCount > CreativeItemValidatorConfig.maxEnchantments) {
                return veto(sp, stack, "enchants=" + enchCount
                        + "/" + CreativeItemValidatorConfig.maxEnchantments);
            }
        }

        // ─── Шаг 6: кол-во PDC-ключей (CustomData.size() в MC 1.21.143) ──
        // В 1.21 CompoundTag keySet() не публично exposed, поэтому используем
        // CustomData.size() — sibling-only integer accessor (vanilla API).
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            int keys = customData.size();
            if (keys > CreativeItemValidatorConfig.maxPdcKeys) {
                return veto(sp, stack, "pdcKeys=" + keys
                        + "/" + CreativeItemValidatorConfig.maxPdcKeys);
            }
        }

        return false;
    }

    // =========================================================
    // RECURSIVE WALK (shulker-in-shulker, bundle-in-bundle ...)
    // =========================================================

    /** Plain DTO mirroring Bukkit's {@code NbtStats}. */
    private static class NbtStats {
        int totalBytes;
        int maxDepth;
        int containerCount;
        boolean exceeded;

        void add(int bytes, int depth) {
            totalBytes += bytes;
            maxDepth = Math.max(maxDepth, depth);
        }
    }

    private NbtStats countNbtRecursive(ItemStack stack, int depth) {
        NbtStats stats = new NbtStats();
        if (stack == null || stack.isEmpty()) return stats;

        if (depth > CreativeItemValidatorConfig.maxRecursionDepth) {
            stats.exceeded = true;
            return stats;
        }

        stats.add(estimateSerializedBytes(stack), depth);

        if (stats.totalBytes > CreativeItemValidatorConfig.maxTotalNbtSize) {
            stats.exceeded = true;
            return stats;
        }

        // ─── Shulker box recursion (via BLOCK_ENTITY_DATA) ─────────────
        CustomData blockEntity = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntity != null) {
            CompoundTag blockTag = blockEntity.copyTag();
            if (blockTag.contains("Items", Tag.TAG_LIST)) {
                ListTag items = blockTag.getList("Items", Tag.TAG_COMPOUND);
                if (!items.isEmpty()) {
                    stats.containerCount++;
                    for (int i = 0; i < items.size(); i++) {
                        if (!(items.get(i) instanceof CompoundTag innerTag)) continue;
                        ItemStack inner = ItemStack.parseOptional(registryAccess, innerTag);
                        if (inner.isEmpty()) continue;

                        NbtStats child = countNbtRecursive(inner, depth + 1);
                        stats.totalBytes += child.totalBytes;
                        stats.maxDepth = Math.max(stats.maxDepth, child.maxDepth);
                        stats.containerCount += child.containerCount;
                        if (child.exceeded) { stats.exceeded = true; return stats; }
                        if (stats.totalBytes > CreativeItemValidatorConfig.maxTotalNbtSize) {
                            stats.exceeded = true;
                            return stats;
                        }
                    }
                }
            }
        }

        // ─── Bundle recursion (via BUNDLE_CONTENTS) ────────────────────
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) {
            stats.containerCount++;
            for (ItemStack inner : bundle.items()) {
                if (inner == null || inner.isEmpty()) continue;
                NbtStats child = countNbtRecursive(inner, depth + 1);
                stats.totalBytes += child.totalBytes;
                stats.maxDepth = Math.max(stats.maxDepth, child.maxDepth);
                stats.containerCount += child.containerCount;
                if (child.exceeded) { stats.exceeded = true; return stats; }
                if (stats.totalBytes > CreativeItemValidatorConfig.maxTotalNbtSize) {
                    stats.exceeded = true;
                    return stats;
                }
            }
        }

        return stats;
    }

    /**
     * Best-effort byte-size estimate of a serialized ItemStack NBT.
     * <p>
     * В MC 1.21.143 {@code ItemStack.save()} возвращает абстрактный
     * {@link Tag} (конкретно CompoundTag at runtime). Метод {@link Tag#sizeInBytes()}
     * доступен на интерфейсе Tag — поэтому работаем через Tag-тип, без приведения.
     * <p>
     * Семантика совпадает с Bukkit «serializeAsBytes()» с поправкой на codec overhead.
     */
    private int estimateSerializedBytes(ItemStack stack) {
        Tag tag = stack.save(registryAccess);
        return tag != null ? tag.sizeInBytes() : 0;
    }

    // =========================================================
    // VETO: log + cooled message + remove from inventory
    // =========================================================

    /**
     * @return true → caller should stop scanning current inventory (только одно
     *         удаление за тик, чтобы не читерить с TPS).
     */
    private boolean veto(ServerPlayer sp, ItemStack stack, String reason) {

        // Remove from inventory: walk slots and replace matching items.
        for (int i = 0; i < sp.getInventory().items.size(); i++) {
            if (ItemStack.isSameItemSameComponents(sp.getInventory().items.get(i), stack)) {
                sp.getInventory().items.set(i, ItemStack.EMPTY);
                break;
            }
        }
        for (int i = 0; i < sp.getInventory().armor.size(); i++) {
            if (ItemStack.isSameItemSameComponents(sp.getInventory().armor.get(i), stack)) {
                sp.getInventory().armor.set(i, ItemStack.EMPTY);
                break;
            }
        }
        if (ItemStack.isSameItemSameComponents(sp.getInventory().offhand.get(0), stack)) {
            sp.getInventory().offhand.set(0, ItemStack.EMPTY);
        }

        ConsoleLogger.warn("[CreativeItem] BLOCKED " + stack.getItem().toString()
                + " (x" + stack.getCount() + ")"
                + " for " + sp.getGameProfile().getName()
                + " reason=" + reason);

        long now = System.currentTimeMillis();
        Long lastSent = lastMessageTime.get(sp.getUUID());
        if (lastSent == null || (now - lastSent) > CreativeItemValidatorConfig.messageCooldownMs) {
            sp.displayClientMessage(buildDenyMessage(), /*actionBar=*/true);
            lastMessageTime.put(sp.getUUID(), now);
        }
        return true;
    }

    /**
     * Минимальный перевод MiniMessage → Component для deny-сообщения.
     * MVP: §-стиль (ChatFormatting.RED + plain text — MiniMessage-парсер не
     * подключён в MVP).
     */
    private Component buildDenyMessage() {
        return Component.literal("Этот предмет содержит слишком много данных!")
                .withStyle(ChatFormatting.RED);
    }

    // ==========================================================================
    // UNUSED-IMPORT SUPPRESSION (avoid javac warnings; keep API surface open)
    // ==========================================================================
    @SuppressWarnings("unused")
    private static final Tool _toolRef = null;
}
