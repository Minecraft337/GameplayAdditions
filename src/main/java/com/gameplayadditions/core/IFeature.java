package com.gameplayadditions.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * IFeature — единый интерфейс для всех независимых фич мода.
 * <p>
 * Архитектурный слой ПОВЕРХ существующего {@code module.PluginModule}, чтобы
 * новые портированные фичи (magnet, codepanel, integrity, glass-break, итд)
 * подключались единообразно без необходимости переписывать уже работающие
 * менеджеры (Radiation, Reactor, Vanish, Cable, etc.).
 * <p>
 * Жизненный цикл фичи в рамках IFeature:
 * <ol>
 *   <li>{@link #register(IEventBus)} — конструктор мода, DeferredRegister'ы</li>
 *   <li>{@link #setup(FMLCommonSetupEvent)} — внутри enqueueWork, БД/кеши</li>
 *   <li>{@link #onServerStart(ServerStartingEvent)} — доступ к Server</li>
 *   <li>{@link #onServerStop(ServerStoppingEvent)} — сохранение/тик-стоп</li>
 * </ol>
 */
public interface IFeature {

    /** Уникальное имя фичи (для логов, команд, конфигов). */
    String getName();

    /**
     * Фаза 1: регистрация DeferredRegister (блоки/предметы/сущности).
     * Вызывается в конструкторе {@code GameplayAdditionsMod}.
     */
    void register(IEventBus modEventBus);

    /**
     * Фаза 2: общая установка (БД, кеши, плагин-независимая конфигурация).
     * Вызывается внутри {@code FMLCommonSetupEvent.enqueueWork}.
     */
    void setup(FMLCommonSetupEvent event);

    /**
     * Фаза 3: старт сервера. Доступен объект {@code MinecraftServer}.
     */
    void onServerStart(ServerStartingEvent event);

    /**
     * Фаза 4: остановка сервера. Сохранение стейта, отписка от листенеров.
     */
    void onServerStop(ServerStoppingEvent event);
}
