package com.gameplayadditions.energy;

import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * @deprecated Cables no longer store energy — energy flows directly
 * from generators to batteries and from batteries to consumers through BFS.
 * Use BFS pathfinding via CableNetwork instead.
 */
@Deprecated
public class EnergyManager {

    public static void addEnergy(Level level, BlockPos pos, int amount) {
        if (amount <= 0) return;
        CableNode node = CableNetwork.getNode(level, pos);
        if (node == null) return;
        node.addEnergy(amount);
    }

    public static void removeEnergy(Level level, BlockPos pos, int amount) {
        if (amount <= 0) return;
        CableNode node = CableNetwork.getNode(level, pos);
        if (node == null) return;
        node.removeEnergy(amount);
    }

    public static void transfer(Level level, BlockPos from, BlockPos to, int amount) {
        if (amount <= 0) return;
        CableNode source = CableNetwork.getNode(level, from);
        CableNode target = CableNetwork.getNode(level, to);
        if (source == null || target == null) return;
        if (from.equals(to)) return;
        if (!source.getConnectionKeys().contains(LocationUtil.toKey(to))) return;
        if (!LocationUtil.isFullyConnected(from, to)) return;
        if (source.getType() == NodeType.BATTERY && target.getType() == NodeType.BATTERY) return;

        int available = source.getEnergy();
        if (available <= 0) return;
        int transfer = Math.min(available, amount);
        if (transfer <= 0) return;
        source.removeEnergy(transfer);
        target.addEnergy(transfer);
    }

    public static int getEnergy(Level level, BlockPos pos) {
        CableNode node = CableNetwork.getNode(level, pos);
        return node != null ? node.getEnergy() : 0;
    }

    public static boolean hasEnergy(Level level, BlockPos pos, int amount) {
        return getEnergy(level, pos) >= amount;
    }
}
