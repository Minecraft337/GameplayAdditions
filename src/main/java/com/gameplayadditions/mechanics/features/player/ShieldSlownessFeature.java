package com.gameplayadditions.mechanics.features.player;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * ShieldSlownessFeature — замедление при блокировке щитом.
 *
 * <p>Порт {@code com.mcplugin.mechanics.features.player.ShieldSlownessManager} из MC-Plugin.
 * Bukkit: {@code EntityDamageByEntityEvent} с {@code player.isBlocking()} &rarr; {@code PotionEffect.SLOWNESS}.
 * NeoForge: {@link LivingDamageEvent.Pre} с {@code player.isBlocking()} &rarr; {@link MobEffectInstance#SLOWNESS}.
 *
 * <p>Конфигурация:
 * <ul>
 *   <li>{@code enabled=true} — вкл/выкл</li>
 *   <li>{@code slownessAmp=255} — амплитуда замедления</li>
 *   <li>{@code slownessDuration=20} — длительность в тиках (1 сек)</li>
 * </ul>
 */
public class ShieldSlownessFeature extends AbstractFeature {

    // TODO(config): перенести в ConfigManager
    private boolean enabled = true;
    private int slownessAmp = 255;
    private int slownessDuration = 20; // тиков

    @Override
    public String getName() {
        return "shield_slowness";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!enabled) return;
        if (event.getEntity().level().isClientSide()) return;

        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.isBlocking()) return;

        // Накладываем SLOWNESS на игрока, который блокирует щитом
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                slownessDuration,
                slownessAmp,
                false,
                true
        ));
    }
}
