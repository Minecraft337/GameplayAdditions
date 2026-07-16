package com.gameplayadditions.display;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * BossBarManager — кастомный босс-бар для всех игроков.
 * Портирован из MC-Plugin для NeoForge.
 */
public class BossBarManager {

    private static BossBarManager instance;
    private CustomBossEvent bossEvent;
    private boolean enabled;
    private int intervalTicks;
    private int tickCounter = 0;
    private double progress = 0.5;
    private boolean progressIncreasing = true;

    public static void init() {
        instance = new BossBarManager();
        instance.enabled = true;
        instance.intervalTicks = 20;
        ConsoleLogger.info("[BossBar] Initialized (waiting for server).");
    }

    public static void shutdown() {
        if (instance != null && instance.bossEvent != null) {
            instance.bossEvent.setVisible(false);
        }
        instance = null;
    }

    public static BossBarManager getInstance() { return instance; }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ResourceLocation id = ResourceLocation.parse("gameplayadditions:main_bossbar");
        bossEvent = event.getServer().getCustomBossEvents().create(
            id,
            Component.literal("§6Gameplay Additions")
        );
        if (bossEvent != null) {
            bossEvent.setVisible(true);
            bossEvent.setMax(100);
            bossEvent.setValue(50);
            // Add all online players
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                bossEvent.addPlayer(player);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!enabled || bossEvent == null) return;
        tickCounter++;
        if (tickCounter < intervalTicks) return;
        tickCounter = 0;

        if (progressIncreasing) {
            progress += 0.01;
            if (progress >= 1.0) { progress = 1.0; progressIncreasing = false; }
        } else {
            progress -= 0.01;
            if (progress <= 0.0) { progress = 0.0; progressIncreasing = true; }
        }

        bossEvent.setValue((int)(progress * 100));
        bossEvent.setName(Component.literal("§6✦ Gameplay Additions §7[" + (int)(progress * 100) + "%]"));
    }
}
