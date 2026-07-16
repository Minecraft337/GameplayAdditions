package com.gameplayadditions.mechanics.features.items;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;

/**
 * 🗑 ItemKillFeature — анти-лаг очистка дропнутых предметов в мире.
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.items.ItemKillManager} (Bukkit, 2.2 KB).
 * <p>
 * Семантика Bukkit:
 * <ol>
 *   <li>Каждые 20 тиков — обход всех миров и подсчёт дропнутых {@code ItemEntity}.</li>
 *   <li>Если total больше {@code limit} (default 6400) — широковещание операторам
 *       с permission {@code mcplugin.admin} ИЛИ OP-статусом, плюс удаление
 *       ВСЕХ дропнутых items во ВСЕХ мирах для снятия лаг-spike.</li>
 * </ol>
 *
 * <p><b>АРХИТЕКТУРНЫЕ ОТЛИЧИЯ:</b></p>
 * <ul>
 *   <li>Bukkit использует {@code BukkitRunnable} — мы используем
 *       {@link ServerTickEvent.Post} каждые 20 тиков (через internal tickCounter).</li>
 *   <li>Список операторов — {@code server.getPlayerList().getPlayers()} фильтруем
 *       по {@code server.getPlayerList().isOp(...)} или через {@code player.hasPermissions(2)}.</li>
 * </ul>
 *
 * <p><b>Server-only:</b> {@link ServerTickEvent.Post} по API канонически server-side;
 * добавлен явный {@code isClientSide()} guard для defense-in-depth.</p>
 */
public class ItemKillFeature extends AbstractFeature {

    // =========================
    // CONFIG (defaults из MC-Plugin)
    // =========================
    /** Интервал проверки (тиков). Bukkit: периодический BukkitRunnable каждые 20 тиков (1 сек). */
    public static int intervalTicks = 20;
    /** Максимум дропнутых items перед триггером очистки. Bukkit default: 6400. */
    public static int itemLimit = 6400;
    /** Уровень permission для warn. Bukkit: «mcplugin.admin» — эквивалент OP (permission 2). */
    public static int adminPermissionLevel = 2;
    /** Broadcast warning перед удалением. */
    public static boolean notifyOps = true;

    private long tickCounter = 0L;

    @Override
    public String getName() {
        return "item_kill";
    }

    @Override
    public void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        logInfo("setup complete. interval=" + intervalTicks
                + "ticks, limit=" + itemLimit
                + ", notifyOps=" + notifyOps);
    }

    @Override
    public void onServerStart(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        super.onServerStart(event);
        registerGameEvents();
        logInfo("subscribed to ServerTickEvent.Post (interval=" + intervalTicks + " ticks).");
    }

    /**
     * Периодическая проверка (каждые {@code intervalTicks} тиков).
     * <p>
     * ServerTickEvent.Post по API канонически server-side; isClientSide-redundant
     * guard удалён (overworld() всегда server, BUG-FIX review 2026).
     */
    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (!isRunning()) return;

        tickCounter++;
        if (tickCounter < intervalTicks) return;
        tickCounter = 0L;

        // Walk all levels (overworld / nether / end / mod-added) — same as Magnet fix
        int totalDropped = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level == null) continue;
            totalDropped += countDroppedItemsIn(level);
        }

        if (totalDropped > itemLimit) {
            killExcessItems(event, totalDropped);
        }
    }

    // =========================================================
    // SCAN + CLEANUP LOGIC
    // =========================================================

    /** Walk all loaded entities in level — count of {@link ItemEntity}. */
    private int countDroppedItemsIn(ServerLevel level) {
        if (level == null) return 0;
        int count = 0;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity) count++;
        }
        return count;
    }

    /** Warn ops + remove ALL dropped items on every level. */
    private void killExcessItems(ServerTickEvent.Post event, int totalDropped) {
        if (notifyOps) {
            Component warn = Component.literal("[ItemKill] ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("Dropped item count ")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(String.valueOf(totalDropped))
                            .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" > limit ")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(String.valueOf(itemLimit))
                            .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" — clearing all dropped items.")
                            .withStyle(ChatFormatting.RED));
            // Отправляем только операторам (permissions level ≥ adminPermissionLevel)
            for (ServerPlayer op : event.getServer().getPlayerList().getPlayers()) {
                if (op.hasPermissions(adminPermissionLevel)) {
                    op.displayClientMessage(warn, false);
                }
            }
        }

        int removed = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level == null) continue;
            // BUG-FIX (compile iter 2026-final): Level.getAllEntities() возвращает Iterable<Entity>,
            //    а ArrayList(Collection<T>) требует Collection, не Iterable — явный cast не помогает.
            //    Ручной copy через for-loop самый надёжный. Защита от CME при discard() внутри —
            //    snapshot сохраняет REFERENCES на entity, которые остаются valid пока жив discard().
            java.util.ArrayList<Entity> snapshot = new java.util.ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                snapshot.add(entity);
            }
            for (Entity entity : snapshot) {
                if (entity instanceof ItemEntity) {
                    entity.discard();
                    removed++;
                }
            }
        }

        ConsoleLogger.info("[ItemKill] Cleaned " + removed
                + " dropped items (was " + totalDropped + ", limit=" + itemLimit + ")");
        logInfo("dropped-item cleanup executed (-" + removed + " entities).");
    }
}
