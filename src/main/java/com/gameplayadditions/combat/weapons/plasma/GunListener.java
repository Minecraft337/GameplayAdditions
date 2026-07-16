package com.gameplayadditions.combat.weapons.plasma;

import com.gameplayadditions.combat.weapons.core.WeaponResolver;
import com.gameplayadditions.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.UUID;

/**
 * GunListener — обработка стрельбы из плазменной пушки.
 * Портирован из MC-Plugin для NeoForge.
 */
public class GunListener {

    private static final long COOLDOWN_TICKS = 80;
    private final HashMap<UUID, Long> cooldown = new HashMap<>();

    @SubscribeEvent
    public void onUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!hand.is(Items.WARPED_FUNGUS_ON_A_STICK)) return;
        if (!WeaponResolver.isPlasma(hand)) return;

        // Ammo check
        ItemStack ammo = player.getOffhandItem();
        if (ammo.isEmpty() || !ammo.is(Items.ECHO_SHARD)) {
            player.sendSystemMessage(Component.literal("§7[§dPLASMA§7] §cNo ammo"));
            return;
        }

        int ammoCount = ammo.getCount();
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();

        if (cooldown.containsKey(id)) {
            long last = cooldown.get(id);
            long passedTicks = (now - last) / 50;
            if (passedTicks < COOLDOWN_TICKS) {
                double left = (COOLDOWN_TICKS - passedTicks) / 20.0;
                player.sendSystemMessage(Component.literal(
                    "§7[§6RELOAD§7] §c" + String.format("%.1f", left) + "s §8| §7Ammo: §e" + ammoCount));
                return;
            }
        }

        cooldown.put(id, now);

        // Consume ammo
        if (ammoCount <= 1) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        } else {
            ammo.shrink(1);
        }

        // Shoot visual
        player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.MASTER, 1.2f, 1.1f);
        player.sendSystemMessage(Component.literal("§7[§dPLASMA§7] §fShot §8| §7Ammo: §e" + (ammoCount - 1)));
    }
}
