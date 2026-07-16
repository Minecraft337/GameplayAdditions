package com.gameplayadditions.mechanics.features.integrity;

import com.gameplayadditions.core.AbstractFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 🛡 Integrity Core Feature — обновляет целостность предметов в инвентарях
 * всех онлайн-игроков по расписанию (каждые {@code IntegrityConfig.intervalTicks} тиков).
 * <p>
 * Заменяет MC-Plugin IntegrityListener + scheduled BukkitRunnable (IntegrityManager.run).
 * <p>
 * MVP: PlayerItemDamageEvent suppression не подключён — синхронизация vanilla damage→0
 * происходит в {@link IntegritySystem#syncVanillaDamage}, что покрывает весь визуальный
 * износ предмета.
 */
public class IntegrityCoreFeature extends AbstractFeature {

    private long tickCounter = 0L;

    public IntegrityCoreFeature() {
        super();
    }

    @Override
    public String getName() {
        return "integrity_core";
    }

    @Override
    public void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        logInfo("setup — applying defaults");
        IntegrityConfig.loadFromConfig();
    }

    @Override
    public void onServerStart(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        super.onServerStart(event);
        logInfo("onServerStart — system enabled=" + IntegrityConfig.enabled
                + " (interval=" + IntegrityConfig.intervalTicks + " ticks)");
        IntegrityConfig.loadFromConfig();
    }

    @SubscribeEvent
    public void onServerTickPre(ServerTickEvent.Pre event) {
        if (!isRunning()) return;
        if (!IntegritySystem.isEnabled()) return;

        tickCounter++;
        if (tickCounter < IntegrityConfig.intervalTicks) return;
        tickCounter = 0;

        var server = event.getServer();
        var players = new ArrayList<>(server.getPlayerList().getPlayers());
        try {
            IntegritySystem.processInventoryForAllPlayers(players);
        } catch (Exception e) {
            logError("processInventoryForAllPlayers failed: " + e.getMessage());
        }
    }

    // Suppress unused-import warning on java.util.List
    @SuppressWarnings("unused")
    private static List<Integer> _placeholder() { return List.of(); }
}
