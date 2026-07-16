package com.gameplayadditions.mechanics.environment.magnet;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * MagnetFeature — фича-магнит, порт
 * {@code com.mcplugin.mechanics.environment.magnet.MagnetManager} из MC-Plugin.
 * <p>
 * Жизненный цикл через {@link com.gameplayadditions.core.AbstractFeature}:
 * <ol>
 *   <li>{@link #setup} — конфиг + загрузка центров из БД;</li>
 *   <li>{@link #onServerStart} — подписка на {@link ServerTickEvent};</li>
 *   <li>{@link #onServerStop} — сохранение центров в БД.</li>
 * </ol>
 * <p>
 * Tick-pull вызывается на {@link ServerTickEvent.Pre} — старт логики мода,
 * поэтому избегаем гонок с другими слушателями тика.
 */
public class MagnetFeature extends AbstractFeature {

    @Override
    public String getName() {
        return "magnet";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        MagnetConfig.reload();
        MagnetManager.get().loadFromDatabase();
        logInfo("setup complete.");
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        logInfo("subscribed to ServerTickEvent.Pre for AABB pull.");
        super.onServerStart(event);
    }

    @Override
    public void onServerStop(ServerStoppingEvent event) {
        MagnetManager.get().persistToDatabase();
        logInfo("centers persisted to DB.");
        super.onServerStop(event);
    }

    /**
     * Pull-сила на каждый тик. Обработка линейна по кол-ву центров × ItemEntity.
     *
     * <p><b>BUG-FIX (item 2026):</b> Раньше тянул только {@code server.overworld()}.
     * Магниты, поставленные в Nether / End, были мертвы — тик-цикл их не
     * обслуживал. Теперь итерируем все уровни через {@code server.getAllLevels()}
     * (Minecraft 1.21 API), что покрывает overworld + nether + end +
     * любые модовые измерения.</p>
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Pre event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            MagnetManager.get().tickPull(level);
        }
    }
}
