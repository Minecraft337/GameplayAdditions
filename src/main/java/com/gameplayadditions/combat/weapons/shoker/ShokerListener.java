package com.gameplayadditions.combat.weapons.shoker;

import com.gameplayadditions.combat.weapons.core.WeaponResolver;
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
 * ShokerListener — обработка стрельбы из электро-шокера.
 * Портирован из MC-Plugin для NeoForge.
 */
public class ShokerListener {

    private static final long COOLDOWN_TICKS = 80;
    private static final HashMap<UUID, Long> cooldown = new HashMap<>();

    @SubscribeEvent
    public void onUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!hand.is(Items.WARPED_FUNGUS_ON_A_STICK)) return;
        if (!WeaponResolver.isShocker(hand)) return;

        ItemStack ammo = player.getOffhandItem();
        if (ammo.isEmpty() || !ammo.is(Items.BREEZE_ROD)) {
            player.sendSystemMessage(Component.literal("§7[§4SHOCKER§7] §cNo ammo"));
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

        if (ammoCount <= 1) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        } else {
            ammo.shrink(1);
        }

        player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.MASTER, 1.0f, 1.0f);
        player.sendSystemMessage(Component.literal("§7[§bSHOCKER§7] §fShot §8| §7Ammo: §e" + (ammoCount - 1)));
    }
}
