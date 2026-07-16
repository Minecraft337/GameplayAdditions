package com.gameplayadditions.listener;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * ShulkerBulletListener — снятие левитации от пуль шалкера.
 * Портирован из MC-Plugin.
 */
public class ShulkerBulletListener {

    @SubscribeEvent
    public void onShulkerDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player &&
            event.getSource().getDirectEntity() instanceof ShulkerBullet) {
            // Schedule removal on next tick (effect is applied after this event)
            player.server.execute(() -> {
                if (player.isAlive()) {
                    player.removeEffect(MobEffects.LEVITATION);
                }
            });
        }
    }
}
