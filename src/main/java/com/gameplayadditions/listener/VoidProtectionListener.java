package com.gameplayadditions.listener;

import com.gameplayadditions.util.MessageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * VoidProtectionListener — защита от падения в пустоту.
 * Портирован из MC-Plugin.
 */
public class VoidProtectionListener {

    @SubscribeEvent
    public void onVoidDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;

        event.setCanceled(true);

        ServerLevel level = player.serverLevel();
        BlockPos spawnPos = level.getSharedSpawnPos();

        player.teleportTo(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);

        // Particles
        level.sendParticles(ParticleTypes.END_ROD, spawnPos.getX(), spawnPos.getY() + 1, spawnPos.getZ(),
                80, 0.5, 1.0, 0.5, 0.15);
        level.sendParticles(ParticleTypes.PORTAL, spawnPos.getX(), spawnPos.getY() + 1, spawnPos.getZ(),
                60, 0.5, 1.0, 0.5, 0.3);

        level.playSound(null, spawnPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.MASTER, 1.0f, 1.0f);

        player.sendSystemMessage(Component.literal("§a✔ §fYou were saved from the void!"));
    }
}
