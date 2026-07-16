package com.gameplayadditions.listener;

import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.util.LocationUtil;
import com.gameplayadditions.util.MessageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * MultimeterListener — осмотр энергосети мультиметром.
 * Портирован из MC-Plugin.
 */
public class MultimeterListener {

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack item = player.getItemInHand(InteractionHand.MAIN_HAND);

        // Проверка на мультиметр (CLOCK)
        if (!item.is(Items.CLOCK)) return;

        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 1.0f, 1.0f);

        CableNode node = CableNetwork.getNode(level, pos);
        if (node == null) {
            player.sendSystemMessage(Component.literal("§6=== MULTIMETER ===\n§7No energy node at this block."));
            return;
        }

        player.sendSystemMessage(Component.literal(
            "§6=== MULTIMETER ===\n" +
            "§bType: " + node.getType().name() + "\n" +
            "§bEnergy: §f" + node.getEnergy() + "\n" +
            "§bConnections: §f" + node.getConnectionCount()
        ));
    }
}
