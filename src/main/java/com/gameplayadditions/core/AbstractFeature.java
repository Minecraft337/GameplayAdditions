package com.gameplayadditions.core;

import com.gameplayadditions.util.ConsoleLogger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * AbstractFeature — база для фичи. Переопределяйте только нужные методы.
 * <p>
 * Удобства:
 * <ul>
 *   <li>{@link #registerGameEvents()} — подписка на {@code NeoForge.EVENT_BUS}
 *       изнутри фичи без бойлерплейта;</li>
 *   <li>Безопасные дефолты: фазы no-op до переопределения;</li>
 *   <li>Единый лог-префикс {@code [Feature:<name>]}.</li>
 * </ul>
 * <p>
 * Каждая фаза логируется отдельно. Если какая-то фаза бросает исключение,
 * мод не падает целиком — фича просто отключается, остальные продолжают.
 */
public abstract class AbstractFeature implements IFeature {

    private boolean gameEventsRegistered = false;
    private boolean running = false;

    /**
     * Дефолт — no-op. Переопределите для регистрации блоков/предметов через
     * {@code DeferredRegister.create(...)}.
     */
    @Override
    public void register(IEventBus modEventBus) {
        // no-op
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        // no-op
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        running = true;
        ConsoleLogger.info("[Feature:" + getName() + "] Server start complete.");
    }

    @Override
    public void onServerStop(ServerStoppingEvent event) {
        running = false;
        ConsoleLogger.info("[Feature:" + getName() + "] Server stop complete.");
    }

    /**
     * Подписать текущий класс на игровые события NeoForge.
     * <p>
     * Идемпотентна — повторный вызов безопасен. Вызывайте из {@link #setup}
     * или из {@link #onServerStart}, если в классе есть методы с
     * {@code @SubscribeEvent}.
     */
    protected final void registerGameEvents() {
        if (gameEventsRegistered) return;
        NeoForge.EVENT_BUS.register(this);
        gameEventsRegistered = true;
        ConsoleLogger.info("[Feature:" + getName() + "] Subscribed to NeoForge.EVENT_BUS.");
    }

    /** Активна ли фича. */
    public boolean isRunning() {
        return running;
    }

    /** Логгер с префиксом фичи (для диагностики из подклассов). */
    protected final void logInfo(String message) {
        ConsoleLogger.info("[Feature:" + getName() + "] " + message);
    }

    protected final void logWarn(String message) {
        ConsoleLogger.warn("[Feature:" + getName() + "] " + message);
    }

    protected final void logError(String message) {
        ConsoleLogger.error("[Feature:" + getName() + "] " + message);
    }
}
