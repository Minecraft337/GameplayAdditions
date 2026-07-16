package com.gameplayadditions.listener;

import com.gameplayadditions.energy.consumption.light.LightManager;
import com.gameplayadditions.energy.storage.battery.BatteryManager;
import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.energy.util.Materials;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * BlockPlaceListener — обработка установки блоков энергосистемы.
 * <p>
 * Порт {@code com.mcplugin.listener.BlockPlaceListener} из MC-Plugin.
 */
public class BlockPlaceListener {

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        Level level = (Level) e.getLevel();
        BlockPos pos = e.getPos();
        if (level == null || pos == null) return;

        var state = e.getState();

        // =========================
        // 🔋 BATTERY (hot expand)
        // =========================
        if (state.is(Materials.WAXED_COPPER_GRATE)) {
            BatteryManager.onBlockPlaced(level, pos);
            return;
        }

        // =========================
        // 💡 LIGHT (hot expand)
        // =========================
        if (state.is(Blocks.REDSTONE_LAMP) || state.is(Blocks.WAXED_COPPER_BULB)) {
            if (!LightManager.isActive(level, pos)) {
                LightManager.assemble(level, pos);
            }
            return;
        }

        // =========================
        // ⚡ CABLE (WAXED_LIGHTNING_ROD / WAXED_CHISELED_COPPER)
        // =========================
        if (state.is(Blocks.LIGHTNING_ROD) || state.is(Materials.WAXED_CHISELED_COPPER)) {
            CableNetwork.addNode(level, pos);
            CableNode node = CableNetwork.getNode(level, pos);

            if (node != null) {
                // Set node type
                if (state.is(Materials.WAXED_COPPER_GRATE)) {
                    node.setType(NodeType.BATTERY);
                } else {
                    node.setType(NodeType.CABLE);
                }

                // Auto-connect to neighbors
                for (BlockPos nearby : LocationUtil.getNeighbors(pos)) {
                    CableNode neighbor = CableNetwork.getNode(level, nearby);
                    if (neighbor == null) continue;

                    // No battery ↔ battery connections
                    if (node.getType() == NodeType.BATTERY && neighbor.getType() == NodeType.BATTERY) continue;

                    if (!LocationUtil.isFullyConnected(pos, nearby)) continue;

                    node.connectKey(LocationUtil.toKey(nearby));
                    neighbor.connectKey(LocationUtil.toKey(pos));
                }

                CableNetwork.saveNode(node);
            }
        }

        // =========================
        // 🛠 CRAFTER (register as workbench)
        // =========================
        if (state.is(Blocks.CRAFTER)) {
            // Workbenches are registered via assembler interaction, not automatic
        }
    }
}
