package com.gameplayadditions.mechanics.radiation;

import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.MessageUtil;
import com.gameplayadditions.util.PlayerResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RadiationManager — управление радиацией игроков.
 * <p>
 * Порт {@code com.mcplugin.mechanics.environment.radiation.RadiationManager} из MC-Plugin.
 * Упрощён: без кастомных PDC ключей и кастомных предметов.
 */
public class RadiationManager {

    private static RadiationManager instance;

    private final Map<UUID, Integer> radiationMap = new ConcurrentHashMap<>();
    private final Set<UUID> radViewEnabled = new HashSet<>();

    // =========================
    // CONFIG (hardcoded for now)
    // =========================
    private boolean enabled = true;
    private int naturalDecay = 1;
    private boolean effectsEnabled = true;
    private int ancientDebrisRad = 2;
    private int basaltDeltasRad = 2;
    private int endRad = 2;
    private int killReduction = 100;
    private boolean deathReset = true;

    // =========================
    // SINGLETON
    // =========================
    public static RadiationManager getInstance() { return instance; }

    public static void init() {
        instance = new RadiationManager();
        ConsoleLogger.info("[Radiation] Manager initialized.");
    }

    // =========================
    // PUBLIC API
    // =========================
    public static void addRadiation(ServerPlayer player, int amount) {
        if (instance == null || !instance.enabled || player == null) return;
        int current = instance.radiationMap.getOrDefault(player.getUUID(), 0);
        instance.radiationMap.put(player.getUUID(), Math.max(0, current + amount));
    }

    public static void addRadiationNear(Level level, BlockPos pos, double radius, int amount) {
        if (instance == null || !instance.enabled || level == null || pos == null) return;
        double radiusSq = radius * radius;
        AABB box = new AABB(pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
                            pos.getX() + radius, pos.getY() + radius, pos.getZ() + radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= radiusSq) {
                addRadiation(player, (int)(amount));
            }
        }
    }

    public static int getRadiation(ServerPlayer player) {
        if (instance == null || player == null) return 0;
        return instance.radiationMap.getOrDefault(player.getUUID(), 0);
    }

    public static void setRadiation(ServerPlayer player, int amount) {
        if (instance == null || player == null) return;
        instance.radiationMap.put(player.getUUID(), Math.max(0, amount));
    }

    public static void resetRadiation(ServerPlayer player) {
        setRadiation(player, 0);
    }

    public static boolean isRadViewEnabled(ServerPlayer player) {
        if (instance == null || player == null) return false;
        return instance.radViewEnabled.contains(player.getUUID());
    }

    public static void toggleRadView(ServerPlayer player) {
        if (instance == null || player == null) return;
        UUID uuid = player.getUUID();
        if (!instance.radViewEnabled.remove(uuid)) {
            instance.radViewEnabled.add(uuid);
        }
    }

    // =========================
    // PERSISTENCE
    // =========================
    public static void savePlayer(ServerPlayer player) {
        if (instance == null || player == null) return;
        int rad = instance.radiationMap.getOrDefault(player.getUUID(), 0);
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO player_radiation (uuid, radiation) VALUES (?, ?)")) {
            ps.setString(1, player.getUUID().toString());
            ps.setInt(2, rad);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Radiation] Save error: " + e.getMessage());
        }
    }

    public static void loadPlayer(ServerPlayer player) {
        if (instance == null || player == null) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT radiation FROM player_radiation WHERE uuid = ?")) {
            ps.setString(1, player.getUUID().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                instance.radiationMap.put(player.getUUID(), rs.getInt("radiation"));
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Radiation] Load error: " + e.getMessage());
        }
    }

    public static void removePlayer(ServerPlayer player) {
        if (instance == null || player == null) return;
        instance.radiationMap.remove(player.getUUID());
        instance.radViewEnabled.remove(player.getUUID());
    }

    // =========================
    // MAIN TICK (every 20 ticks = 1 second)
    // =========================
    public void tick() {
        if (!enabled) return;

        for (Map.Entry<UUID, Integer> entry : new HashMap<>(radiationMap).entrySet()) {
            ServerPlayer player = findPlayer(entry.getKey());
            if (player == null) continue;
            if (player.isCreative() || player.isSpectator()) continue;
            if (!player.isAlive()) continue;

            int rad = entry.getValue();

            // Natural decay
            if (rad > 0) {
                rad = Math.max(0, rad - naturalDecay);
            }

            // Ancient debris in inventory
            int debrisCount = countInInventory(player, Items.ANCIENT_DEBRIS);
            if (debrisCount > 0) {
                rad += ancientDebrisRad * debrisCount;
            }

            // Basalt deltas biome
            if (player.level().getBiome(player.blockPosition()).is(Biomes.BASALT_DELTAS)) {
                rad += basaltDeltasRad;
            }

            // The End - radiation under open sky
            if (player.level().dimension() == Level.END
                    && player.level().canSeeSky(player.blockPosition())) {
                rad += endRad;
            }

            // Radiation view toggle
            if (radViewEnabled.contains(player.getUUID())) {
                double roentgen = rad / 100.0;
                player.sendSystemMessage(
                        MessageUtil.legacy("§7Radiation: §f" + String.format("%.1f", roentgen) + " §7R/h"));
            }

            radiationMap.put(player.getUUID(), Math.max(0, rad));
        }
    }

    // =========================
    // EFFECTS TICK (every 10 ticks)
    // =========================
    public void tickEffects() {
        if (!enabled || !effectsEnabled) return;

        for (Map.Entry<UUID, Integer> entry : new HashMap<>(radiationMap).entrySet()) {
            ServerPlayer player = findPlayer(entry.getKey());
            if (player == null) continue;
            if (player.isCreative() || player.isSpectator()) continue;
            if (!player.isAlive()) continue;

            int rad = entry.getValue();
            if (rad < 200) continue;

            int duration = 40; // 2 seconds
            int amplifier;

            if (rad < 400) amplifier = 0;
            else if (rad < 800) amplifier = 1;
            else if (rad < 1600) amplifier = 2;
            else if (rad < 3200) amplifier = 3;
            else if (rad < 6400) amplifier = 4;
            else amplifier = 5;

            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, Math.min(amplifier, 2)));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, Math.min(amplifier, 2)));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, Math.min(amplifier, 1)));
            if (amplifier >= 2) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0));
            }
            if (amplifier >= 3) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0));
            }
        }
    }

    // =========================
    // EVENT HANDLERS (call from outside)
    // =========================
    public static void onPlayerDeath(ServerPlayer player) {
        if (instance == null || !instance.deathReset) return;
        resetRadiation(player);
    }

    public static void onPlayerKill(ServerPlayer killer) {
        if (instance == null || !instance.enabled) return;
        int rad = instance.radiationMap.getOrDefault(killer.getUUID(), 0);
        if (rad >= 200) {
            addRadiation(killer, -instance.killReduction);
        }
    }

    // =========================
    // HELPERS
    // =========================
    /**
     * Поиск игрока по UUID. Раньше возвращал {@code null} — тик-эффекты
     * радиации (mob effects) фактически не работали. Теперь делегирует в
     * {@link PlayerResolver}, который опирается на внутренний O(1) Map
     * списка игроков {@code MinecraftServer}. Возвращает {@code null}, если
     * сервер ещё не запущен либо игрок оффлайн.
     */
    private ServerPlayer findPlayer(UUID uuid) {
        return PlayerResolver.getPlayer(uuid);
    }

    private int countInInventory(ServerPlayer player, net.minecraft.world.item.Item itemType) {
        int count = 0;
        for (ItemStack item : player.getInventory().items) {
            if (item.is(itemType)) count += item.getCount();
        }
        for (ItemStack item : player.getInventory().armor) {
            if (item.is(itemType)) count += item.getCount();
        }
        for (ItemStack item : player.getInventory().offhand) {
            if (item.is(itemType)) count += item.getCount();
        }
        return count;
    }
}
