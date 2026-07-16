package com.gameplayadditions.mechanics.features.anticheat;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

/**
 * AntiCheatFeature — порт {@code AutoClickerCheck + FastBowCheck + PortalInventoryCheck + FoodSprintCheck + NoFallCheck} из MC-Plugin.
 * <p>
 * Базовая античит-система: обнаруживает авто-кликеры, быстрые луки,
 * инвентарь в портале, спринт во время еды и NoFall.
 */
public class AntiCheatFeature extends AbstractFeature {

    // ─── AutoClicker ───────────────────────────────────────────────────────
    private static final double MAX_CPS = 20.0;
    private static final long CLICK_WINDOW_MS = 1000L;
    private final Map<UUID, ClickTracker> clickData = new HashMap<>();

    // ─── FastBow ───────────────────────────────────────────────────────────
    private static final long MIN_BOW_INTERVAL_MS = 1100L;
    private final Map<UUID, Long> lastBowShot = new HashMap<>();

    // ─── NoFall ────────────────────────────────────────────────────────────
    private static final double MIN_FALL_DISTANCE = 3.0;

    private record ClickTracker(LinkedList<Long> timestamps) {}

    public AntiCheatFeature() {
    }

    @Override
    public String getName() {
        return "AntiCheat";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[AntiCheat] Feature initialized.");
    }

    private void flag(ServerPlayer player, String check, double vl, String details) {
        ConsoleLogger.warn("[AntiCheat] " + player.getScoreboardName() + " flagged for " + check + " (VL=" + vl + ") | " + details);
        var server = player.server;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.hasPermissions(2)) {
                online.sendSystemMessage(
                        Component.literal(
                                "§c[AC] §f" + player.getScoreboardName() + " §7→ §e" + check + " §7(VL: " + String.format("%.1f", vl) + ")"
                        )
                );
            }
        }
    }

    // ─══════════════════════════════════════════════════════════════════════
    //  1. AutoClickerCheck — обнаружение авто-кликера по CPS
    // ─══════════════════════════════════════════════════════════════════════

    private void processClick(ServerPlayer player) {
        long now = System.currentTimeMillis();
        ClickTracker tracker = clickData.computeIfAbsent(player.getUUID(), k -> new ClickTracker(new LinkedList<>()));

        var timestamps = tracker.timestamps();
        timestamps.addLast(now);

        while (!timestamps.isEmpty() && timestamps.peekFirst() < now - CLICK_WINDOW_MS) {
            timestamps.removeFirst();
        }

        double cps = timestamps.size();
        if (cps > MAX_CPS) {
            double vl = Math.min(5.0, (cps - MAX_CPS) * 0.5);
            flag(player, "AutoClicker", vl, "CPS: " + String.format("%.1f", cps));
        }
    }

    @SubscribeEvent
    public void onClickerCheckEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            processClick(player);
        }
    }

    @SubscribeEvent
    public void onClickerCheckBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            processClick(player);
        }
    }

    // ─══════════════════════════════════════════════════════════════════════
    //  2. FastBowCheck — слишком быстрая стрельба из лука
    // ─══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onBowShoot(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        long now = System.currentTimeMillis();
        Long lastShot = lastBowShot.get(player.getUUID());

        if (lastShot != null && (now - lastShot) < MIN_BOW_INTERVAL_MS) {
            flag(player, "FastBow", 3.0, "Interval: " + (now - lastShot) + "ms (min: " + MIN_BOW_INTERVAL_MS + "ms)");
        }

        lastBowShot.put(player.getUUID(), now);
    }

    // ─══════════════════════════════════════════════════════════════════════
    //  3. PortalInventoryCheck — инвентарь в портале
    // ─══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var blockState = player.level().getBlockState(player.blockPosition());
        if (blockState.is(Blocks.NETHER_PORTAL)) {
            flag(player, "PortalInventory", 2.0, "Opened container while in nether portal");
        }
    }

    // ─══════════════════════════════════════════════════════════════════════
    //  4. FoodSprintCheck — спринт во время еды
    // ─══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // FoodSprint: проверка на спринт во время еды
        if (player.isUsingItem() && player.isSprinting()) {
            var useItem = player.getUseItem();
            if (useItem.getItem().getFoodProperties(useItem, player) != null) {
                flag(player, "FoodSprint", 2.0, "Sprinting while eating " + useItem.getHoverName().getString());
            }
        }

        // NoFall: проверка на отсутствие урона от падения
        if (!player.isCreative() && !player.isSpectator()) {
            if (player.onGround() && player.fallDistance > MIN_FALL_DISTANCE) {
                flag(player, "NoFall", 2.0, "Fall distance: " + String.format("%.1f", player.fallDistance));
            }
        }
    }
}
