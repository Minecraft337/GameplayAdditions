package com.gameplayadditions.energy.storage.battery;

import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.util.ConsoleLogger;

import java.util.*;

/**
 * BatteryDrainTask — разряд энергии от одной батареи к другим в сети.
 * <p>
 * Порт {@code com.mcplugin.energy.storage.battery.BatteryDrainTask} из MC-Plugin.
 * Выполняет BFS от каждой батареи, передавая энергию другим батареям в сети
 * с плавной зарядкой (smooth charge).
 */
public class BatteryDrainTask {

    private static boolean enabled = true;
    private static int maxBatteryEnergy = 100000;
    private static int dischargeAmount = 10;
    private static boolean smoothEnabled = true;
    private static double dischargeMultiplier = 0.5;
    private static boolean logEnabled = false;

    private BatteryDrainTask() {}

    public static void configure(int maxEnergy, int discharge, boolean smooth, double multiplier, boolean log) {
        maxBatteryEnergy = maxEnergy;
        dischargeAmount = discharge;
        smoothEnabled = smooth;
        dischargeMultiplier = multiplier;
        logEnabled = log;
    }

    /**
     * Выполняет один цикл разряда. Вызывать раз в ~20 тиков.
     */
    public static void tick() {
        if (!enabled) return;

        List<CableNode> batteries = new ArrayList<>();
        CableNetwork.forEachNode(node -> {
            if (node != null && node.getType() == NodeType.BATTERY) {
                batteries.add(node);
            }
        });

        for (CableNode battery : batteries) {
            if (battery == null || battery.getType() != NodeType.BATTERY) continue;

            battery.setMaxEnergy(maxBatteryEnergy);

            double fillRatio = (double) battery.getEnergy() / Math.max(maxBatteryEnergy, 1);

            boolean canDischarge = battery.getEnergy() > 0;

            if (canDischarge && battery.getEnergy() > 0) {
                int dynamicDischarge = dischargeAmount;
                if (smoothEnabled) {
                    double factor = dischargeMultiplier + (1.0 - dischargeMultiplier) * fillRatio;
                    dynamicDischarge = Math.max(1, (int) (dischargeAmount * factor));
                }

                // BFS to find other batteries
                String worldKey = battery.getLevel() != null
                        ? battery.getLevel().dimension().location().toString()
                        : null;
                if (worldKey == null) continue;

                Set<Long> visited = new HashSet<>();
                Queue<CableNode> queue = new LinkedList<>();
                queue.add(battery);
                visited.add(battery.getKey());

                int remaining = dynamicDischarge;

                while (!queue.isEmpty() && remaining > 0) {
                    CableNode node = queue.poll();
                    if (node == null) continue;

                    if (node.getType() == NodeType.CABLE) {
                        CableNetwork.markFlowing(worldKey, node.getKey());
                    }

                    if (node != battery && node.getType() == NodeType.BATTERY) {
                        int space = maxBatteryEnergy - node.getEnergy();
                        if (space > 0) {
                            int transfer = Math.min(remaining, space);
                            if (transfer > 0) {
                                battery.removeEnergy(transfer);
                                node.addEnergy(transfer);
                                remaining -= transfer;

                                if (logEnabled) {
                                    ConsoleLogger.info("[Battery] Discharged " + transfer);
                                }
                            }
                        }
                    }

                    if (remaining <= 0) break;

                    for (long connKey : node.getConnectionKeys()) {
                        if (visited.contains(connKey)) continue;
                        CableNode next = CableNetwork.getNodeByKey(worldKey, connKey);
                        if (next != null) {
                            visited.add(connKey);
                            queue.add(next);
                        }
                    }
                }
            }
        }
    }

    public static void setEnabled(boolean val) { enabled = val; }
}
