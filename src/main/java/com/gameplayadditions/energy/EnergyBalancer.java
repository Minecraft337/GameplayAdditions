package com.gameplayadditions.energy;

import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.LocationUtil;

import java.util.*;

/**
 * EnergyBalancer — балансировка энергии между батареями в сети.
 * <p>
 * Порт {@code com.mcplugin.energy.EnergyBalancerTask} из MC-Plugin.
 * Вызывается из NeoForge tick listener, а не BukkitRunnable.
 */
public class EnergyBalancer {

    private static int maxTransfer = 25;
    private static boolean logEnabled = false;

    private EnergyBalancer() {}

    public static void configure(int maxTransferAmount, boolean log) {
        maxTransfer = maxTransferAmount;
        logEnabled = log;
    }

    /**
     * Выполняет один цикл балансировки.
     * Должен вызываться раз в ~20 тиков (1 секунда) или реже.
     */
    public static void tick() {
        // Собираем BATTERY ноды
        List<CableNode> batteries = new ArrayList<>();
        CableNetwork.forEachNode(node -> {
            if (node != null && node.getType() == NodeType.BATTERY) {
                batteries.add(node);
            }
        });

        if (batteries.isEmpty()) return;

        Set<Long> visitedKeys = new HashSet<>();

        for (CableNode start : batteries) {
            if (start == null || start.getType() != NodeType.BATTERY) continue;

            // Skip already visited batteries (connected to same network)
            if (visitedKeys.contains(start.getKey())) continue;

            // BFS to find all batteries in this network
            List<CableNode> networkBatteries = new ArrayList<>();
            Queue<CableNode> queue = new LinkedList<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                CableNode node = queue.poll();
                if (node == null) continue;
                if (!visitedKeys.add(node.getKey())) continue;

                if (node.getType() == NodeType.BATTERY) {
                    networkBatteries.add(node);
                }

                for (long connKey : node.getConnectionKeys()) {
                    if (visitedKeys.contains(connKey)) continue;
                    CableNode next = CableNetwork.getNodeByKey(
                            LocationUtil.worldKey(node.getLevel()), connKey);
                    if (next != null) queue.add(next);
                }
            }

            if (networkBatteries.size() <= 1) continue;

            // Calculate average
            int totalEnergy = 0;
            for (CableNode b : networkBatteries) totalEnergy += b.getEnergy();
            int average = totalEnergy / networkBatteries.size();

            // Collect excess
            int collected = 0;
            List<CableNode> rich = new ArrayList<>();
            List<CableNode> poor = new ArrayList<>();
            for (CableNode b : networkBatteries) {
                int current = b.getEnergy();
                if (current > average) {
                    int remove = Math.min(current - average, maxTransfer);
                    if (remove > 0) {
                        b.removeEnergy(remove);
                        collected += remove;
                        rich.add(b);
                        CableNetwork.markFlowing(LocationUtil.worldKey(b.getLevel()), b.getKey());
                    }
                } else if (current < average) {
                    poor.add(b);
                }
            }

            // Distribute to poor
            for (CableNode b : poor) {
                if (collected <= 0) break;
                int deficit = average - b.getEnergy();
                if (deficit <= 0) continue;
                int add = Math.min(Math.min(deficit, maxTransfer), collected);
                if (add > 0) {
                    b.addEnergy(add);
                    collected -= add;
                    CableNetwork.markFlowing(LocationUtil.worldKey(b.getLevel()), b.getKey());
                }
            }

            // Return undistributed energy back to rich batteries (proportionally)
            if (collected > 0 && !rich.isEmpty()) {
                int totalRichEnergy = 0;
                for (CableNode r : rich) totalRichEnergy += r.getEnergy();
                int remainingToReturn = collected;
                for (int i = 0; i < rich.size() && remainingToReturn > 0; i++) {
                    CableNode r = rich.get(i);
                    int takenBack;
                    if (i == rich.size() - 1) {
                        takenBack = remainingToReturn;
                    } else {
                        double share = totalRichEnergy > 0 ? (double) r.getEnergy() / totalRichEnergy : 1.0 / rich.size();
                        takenBack = Math.min(remainingToReturn, Math.max(1, (int) (collected * share)));
                        if (takenBack > remainingToReturn) takenBack = remainingToReturn;
                    }
                    r.addEnergy(takenBack);
                    remainingToReturn -= takenBack;
                }
                int returned = collected - remainingToReturn;
                if (logEnabled && returned > 0) {
                    ConsoleLogger.info("[Balancer] Returned " + returned + " undistributed energy to rich batteries");
                }
            }
        }
    }
}
