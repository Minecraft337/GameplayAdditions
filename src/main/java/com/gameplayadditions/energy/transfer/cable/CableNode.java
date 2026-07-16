package com.gameplayadditions.energy.transfer.cable;

import com.gameplayadditions.util.LocationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CableNode — узел кабельной сети.
 * <p>
 * Порт {@code com.mcplugin.energy.transfer.cable.CableNode} из MC-Plugin.
 * Адаптирован для NeoForge: использует BlockPos и Level вместо Bukkit Location/World.
 */
public class CableNode {

    private final long key;
    private final Level level;
    private final int x, y, z;
    private BlockPos pos;

    private final Set<Long> connections = ConcurrentHashMap.newKeySet();

    private int energy;
    private NodeType type = NodeType.CABLE;
    private int maxEnergy = 0;

    public CableNode(Level level, BlockPos pos) {
        this.level = level;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.key = LocationUtil.toKey(x, y, z);
        this.pos = pos;
    }

    public BlockPos getPos() {
        if (pos == null) {
            pos = new BlockPos(x, y, z);
        }
        return pos;
    }

    public long getKey() { return key; }
    public Level getLevel() { return level; }
    public int getBlockX() { return x; }
    public int getBlockY() { return y; }
    public int getBlockZ() { return z; }

    public int getEnergy() { return energy; }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(energy, maxEnergy));
    }

    public void addEnergy(int amount) {
        if (amount <= 0) return;
        if (type == NodeType.CABLE) return;
        this.energy = Math.max(0, Math.min(this.energy + amount, maxEnergy));
    }

    public void setMaxEnergy(int maxEnergy) {
        if (maxEnergy > 0) {
            this.maxEnergy = maxEnergy;
            if (this.energy > maxEnergy) this.energy = maxEnergy;
        }
    }

    public int getMaxEnergy() { return maxEnergy; }

    public void removeEnergy(int amount) {
        if (amount <= 0) return;
        if (type == NodeType.CABLE) return;
        this.energy -= amount;
        if (this.energy < 0) this.energy = 0;
    }

    public boolean hasEnergy() { return type != NodeType.CABLE && energy > 0; }

    public void connectKey(long targetKey) {
        if (targetKey == key) return;
        if (connections.contains(targetKey)) return;
        if (!LocationUtil.isFullyConnected(this.key, targetKey)) return;
        connections.add(targetKey);
    }

    public void disconnectKey(long targetKey) { connections.remove(targetKey); }

    public boolean isConnectedTo(long targetKey) { return connections.contains(targetKey); }

    public void clearConnections() { connections.clear(); }

    public Set<Long> getConnectionKeys() { return Collections.unmodifiableSet(connections); }

    /**
     * Internal: add connection without validation (used during DB load).
     */
    void addConnectionKey(long targetKey) {
        connections.add(targetKey);
    }

    public int getConnectionCount() { return connections.size(); }

    public NodeType getType() { return type; }

    public void setType(NodeType type) {
        if (type == null) return;
        this.type = type;
    }

    @Override
    public String toString() {
        return "CableNode{loc=" + x + "," + y + "," + z +
                ", energy=" + energy + "/" + maxEnergy +
                ", type=" + type +
                ", connections=" + connections.size() + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CableNode other)) return false;
        return this.key == other.key && Objects.equals(this.level, other.level);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level.dimension().location().toString(), x, y, z);
    }
}
