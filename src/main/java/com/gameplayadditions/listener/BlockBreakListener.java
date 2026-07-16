package com.gameplayadditions.listener;

import com.gameplayadditions.energy.consumption.light.LightManager;
import com.gameplayadditions.energy.generation.basic.GeneratorManager;
import com.gameplayadditions.energy.machines.workbench.EnergyWorkbenchManager;
import com.gameplayadditions.energy.storage.battery.BatteryManager;
import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.util.Materials;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;
import com.gameplayadditions.util.MessageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Set;

/**
 * BlockBreakListener — обработка разрушения блоков энергосистемы.
 * <p>
 * Порт {@code com.mcplugin.listener.BlockBreakListener} из MC-Plugin.
 */
public class BlockBreakListener {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent e) {
        Level level = (Level) e.getLevel();
        BlockPos pos = e.getPos();
        if (level == null || pos == null) return;

        ServerPlayer player = e.getPlayer() instanceof ServerPlayer sp ? sp : null;

        // =========================
        // 🔥 ГЕНЕРАТОР (BLAST_FURNACE)
        // =========================
        if (level.getBlockState(pos).is(Materials.BLAST_FURNACE)) {
            if (GeneratorManager.disassemble(level, pos)) {
                if (player != null) {
                    player.sendSystemMessage(MessageUtil.legacy("§e⚡ Генератор разобран!"));
                }
                return;
            }
        }

        // =========================
        // 🔋 BATTERY (WAXED_COPPER_GRATE)
        // =========================
        if (level.getBlockState(pos).is(Materials.WAXED_COPPER_GRATE)) {
            BatteryManager.onBlockBroken(level, pos);
            return;
        }

        // =========================
        // 💡 LIGHT (REDSTONE_LAMP / WAXED_COPPER_BULB)
        // =========================
        if (level.getBlockState(pos).is(Blocks.REDSTONE_LAMP) || level.getBlockState(pos).is(Blocks.WAXED_COPPER_BULB)) {
            if (LightManager.isActive(level, pos)) {
                LightManager.disassemble(level, pos);
            }
            return;
        }

        // =========================
        // ⚡ CABLE NODE (WAXED_LIGHTNING_ROD / WAXED_CHISELED_COPPER)
        // =========================
        if (level.getBlockState(pos).is(Blocks.LIGHTNING_ROD) || level.getBlockState(pos).is(Materials.WAXED_CHISELED_COPPER)) {
            CableNode node = CableNetwork.getNode(level, pos);
            if (node != null) {
                // Disconnect all neighbors
                for (long connKey : Set.copyOf(node.getConnectionKeys())) {
                    CableNode neighbor = CableNetwork.getNodeByKey(LocationUtil.worldKey(level), connKey);
                    if (neighbor != null) {
                        neighbor.disconnectKey(LocationUtil.toKey(pos));
                    }
                }
                CableNetwork.removeNode(level, pos);
            }
        }

        // =========================
        // 🛠 CRAFTER (assembler)
        // =========================
        if (level.getBlockState(pos).is(Blocks.CRAFTER)) {
            EnergyWorkbenchManager.remove(pos);
        }
    }
}
