package com.gameplayadditions.mechanics.radiation;

import com.gameplayadditions.core.AbstractFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * RadiationTaskFeature — периодический тик для {@link RadiationManager}.
 *
 * <p>Порт {@code com.mcplugin.mechanics.environment.radiation.RadiationTask} из MC-Plugin.
 * В Bukkit использовался {@code BukkitRunnable}; в NeoForge — {@link ServerTickEvent}.
 *
 * <ul>
 *   <li>каждые 20 тиков (1 сек) — {@code RadiationManager.tick()} (спад, биомы, debris, dosimeter)</li>
 *   <li>каждые 10 тиков (0.5 сек) — {@code RadiationManager.tickEffects()} (effects to players)</li>
 * </ul>
 *
 * <p>Серверная механика: public-only с явным {@code isClientSide} guard больше не нужен —
 * {@link ServerTickEvent} гарантированно серверный.
 */
public class RadiationTaskFeature extends AbstractFeature {

    private int tick = 0;

    @Override
    public String getName() {
        return "radiation_task";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        RadiationManager rad = RadiationManager.getInstance();
        if (rad == null) {
            // hot-reload safety: менеджер ещё не инициализирован
            return;
        }

        tick++;

        // Главный тик радиации: каждые 20 тиков = 1 сек
        if (tick % 20 == 0) {
            rad.tick();
        }

        // Эффекты радиации: каждые 10 тиков = 0.5 сек (как в датапаке)
        if (tick % 10 == 0) {
            rad.tickEffects();
        }
    }
}
