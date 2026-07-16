package com.gameplayadditions.module;

import com.gameplayadditions.energy.EnergyBalancer;
import com.gameplayadditions.energy.generation.reactor.ReactorListener;
import com.gameplayadditions.energy.generation.reactor.ReactorManager;
import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.util.ConsoleLogger;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * EnergyModule — модуль энергосистемы (кабели + реактор).
 * <p>
 * Управляет тиками балансировщика и реактора,
 * регистрирует слушатели блоков реактора.
 */
public class EnergyModule extends PluginModule {

    private boolean initialized = false;

    // Tick counters for periodic actions
    private int balancerTick;
    private int pressureTick;
    private int intensityDownTick;
    private int intensityUpTick;
    private int recipeTick;
    private int wearTick;
    private int soundTick;
    private int meltdownTick;
    private int displayTick;

    public EnergyModule() {
        super("Energy", true);
    }

    @Override
    protected void onInit() {
        CableNetwork.init();
        EnergyBalancer.configure(25, false);

        // Register reactor listener
        NeoForge.EVENT_BUS.register(new ReactorListener());

        // Register tick handler
        NeoForge.EVENT_BUS.addListener(this::onServerTick);

        initialized = true;
        ConsoleLogger.info("[Energy] Energy module initialized.");
    }

    @Override
    protected void onDisable() {
        if (initialized) {
            ReactorManager.saveAll();
            NeoForge.EVENT_BUS.unregister(this);
            initialized = false;
            ConsoleLogger.info("[Energy] Energy module disabled.");
        }
    }

    /**
     * Server tick — обработка тиков реактора и балансировщика.
     */
    private void onServerTick(ServerTickEvent.Pre event) {
        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null || !reactor.isValid()) return;

        // Every tick
        reactor.tick();
        reactor.tickSmoothDisplay();
        reactor.tickVisual();
        reactor.updateDisplays();

        balancerTick++;
        pressureTick++;
        intensityDownTick++;
        intensityUpTick++;
        recipeTick++;
        wearTick++;
        soundTick++;
        meltdownTick++;
        displayTick++;

        // Balancer every 40 ticks (2 seconds)
        if (balancerTick >= 40) {
            balancerTick = 0;
            EnergyBalancer.tick();
        }

        // Pressure every 100 ticks (5 seconds)
        if (pressureTick >= 100) {
            pressureTick = 0;
            reactor.tickPressure();
        }

        // Intensity decay every 20 ticks (1 second)
        if (intensityDownTick >= 20) {
            intensityDownTick = 0;
            reactor.tickIntensityDown();
        }

        // Intensity recovery every 60 ticks (3 seconds)
        if (intensityUpTick >= 60) {
            intensityUpTick = 0;
            reactor.tickIntensityUp();
        }

        // Recipe tick every 100 ticks (5 seconds)
        if (recipeTick >= 100) {
            recipeTick = 0;
            reactor.tickRecipe();
        }

        // Wear tick every 20 ticks (1 second)
        if (wearTick >= 20) {
            wearTick = 0;
            reactor.tickWear();
        }

        // Sound tick every 10 ticks
        if (soundTick >= 10) {
            soundTick = 0;
            reactor.tickSound();
        }

        // Meltdown countdown tick every 2 ticks
        if (meltdownTick >= 2) {
            meltdownTick = 0;
            reactor.tickMeltdownCountdown();
        }

        // Display update every 10 ticks
        if (displayTick >= 10) {
            displayTick = 0;
            reactor.updateDisplays();
        }
    }
}
