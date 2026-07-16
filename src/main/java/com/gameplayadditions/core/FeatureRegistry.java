package com.gameplayadditions.core;

import com.gameplayadditions.util.ConsoleLogger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * FeatureRegistry — реестр новых фич мода.
 * <p>
 * Жизненный цикл НЕ зависит от существующего {@code ModuleManager}.
 * Вызывается напрямую из конструктора {@code GameplayAdditionsMod}.
 * <p>
 * Контракт:
 * <ol>
 *   <li>{@link #init(IEventBus)} вызывается один раз в конструкторе мода.</li>
 *   <li>В {@code init()} регистрируются все фичи через {@link #register}.</li>
 *   <li>Все 4 фазы жизненного цикла роутятся автоматически через event bus.</li>
 * </ol>
 * <p>
 * Если фича падает в одной из фаз — другие продолжают работать (graceful degradation).
 */
public final class FeatureRegistry {

    private static final List<IFeature> FEATURES = new ArrayList<>();
    private static boolean initialized = false;

    private FeatureRegistry() {}

    /**
     * Регистрация новой фичи. Должна быть вызвана ДО {@link #init(IEventBus)}.
     */
    public static void register(IFeature feature) {
        if (initialized) {
            ConsoleLogger.warn("[FeatureRegistry] Cannot register '" + feature.getName()
                    + "' AFTER init(). Add it before the mod constructor returns.");
            return;
        }
        if (FEATURES.stream().anyMatch(f -> f.getName().equalsIgnoreCase(feature.getName()))) {
            ConsoleLogger.warn("[FeatureRegistry] Duplicate feature name: "
                    + feature.getName() + " — ignored.");
            return;
        }
        FEATURES.add(feature);
    }

    /**
     * Один раз вызывается из конструктора {@code GameplayAdditionsMod}.
     */
    public static void init(IEventBus modEventBus) {
        if (initialized) return;
        initialized = true;

        ConsoleLogger.info("[FeatureRegistry] Wiring " + FEATURES.size() + " features...");

        // Phase 1 — register (вызывается прямо здесь, т.к. конструктор мода = это фаза register)
        for (IFeature feature : FEATURES) {
            try {
                feature.register(modEventBus);
                ConsoleLogger.info("[FeatureRegistry]   ✓ register: " + feature.getName());
            } catch (Throwable t) {
                ConsoleLogger.error("[FeatureRegistry]   ✗ register failed: "
                        + feature.getName() + " — " + t.getMessage());
            }
        }

        // Phases 2..4 — проксируем через event bus мода/сервера
        modEventBus.addListener(FeatureRegistry::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(FeatureRegistry::onServerStarting);
        NeoForge.EVENT_BUS.addListener(FeatureRegistry::onServerStopping);

        ConsoleLogger.info("[FeatureRegistry] Event routing online.");
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (IFeature feature : FEATURES) {
                try {
                    feature.setup(event);
                } catch (Throwable t) {
                    ConsoleLogger.error("[FeatureRegistry] setup failed: "
                            + feature.getName() + " — " + t.getMessage());
                }
            }
        });
    }

    private static void onServerStarting(final ServerStartingEvent event) {
        for (IFeature feature : FEATURES) {
            try {
                feature.onServerStart(event);
            } catch (Throwable t) {
                ConsoleLogger.error("[FeatureRegistry] start failed: "
                        + feature.getName() + " — " + t.getMessage());
            }
        }
    }

    private static void onServerStopping(final ServerStoppingEvent event) {
        for (IFeature feature : FEATURES) {
            try {
                feature.onServerStop(event);
            } catch (Throwable t) {
                ConsoleLogger.error("[FeatureRegistry] stop failed: "
                        + feature.getName() + " — " + t.getMessage());
            }
        }
    }

    /** Только для отладки/тестов. */
    public static List<IFeature> getFeatures() {
        return new ArrayList<>(FEATURES);
    }

    /**
     * Возвращает фичу по её типу (прямой lookup) или {@code null}, если не зарегистрирована.
     * Используется для взаимодействия между фичами (например, AuthAuthenticator → AuthDatabase).
     */
    @SuppressWarnings("unchecked")
    public static <T extends IFeature> T get(Class<T> featureClass) {
        if (featureClass == null) return null;
        for (IFeature f : FEATURES) {
            if (featureClass.isInstance(f)) {
                return (T) f;
            }
        }
        ConsoleLogger.warn("[FeatureRegistry.get] Feature not found: " + featureClass.getSimpleName());
        return null;
    }
}
