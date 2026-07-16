package com.gameplayadditions.display;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * ScoreboardManager — кастомный скорборд (sideBar).
 * Портирован из MC-Plugin для NeoForge.
 * Упрощённая версия без ScoreHolder conflicts.
 */
public class ScoreboardManager {

    private static ScoreboardManager instance;
    private boolean enabled;
    private int intervalTicks;
    private int tickCounter = 0;

    public static void init() {
        instance = new ScoreboardManager();
        instance.enabled = true;
        instance.intervalTicks = 20;
        ConsoleLogger.info("[Scoreboard] Initialized.");
    }

    public static void shutdown() {
        instance = null;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!enabled) return;
        tickCounter++;
        if (tickCounter < intervalTicks) return;
        tickCounter = 0;

        try {
            MinecraftServer server = event.getServer();
            if (server == null) return;

            Scoreboard board = server.getScoreboard();
            Objective obj = board.getObjective("ga_info");
            if (obj == null) {
                obj = board.addObjective(
                    "ga_info",
                    ObjectiveCriteria.DUMMY,
                    Component.literal("§6✦ Gameplay Additions"),
                    ObjectiveCriteria.DUMMY.getDefaultRenderType(),
                    false,
                    null
                );
                board.setDisplayObjective(DisplaySlot.SIDEBAR, obj);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Scoreboard] Error: " + e.getMessage());
        }
    }
}
