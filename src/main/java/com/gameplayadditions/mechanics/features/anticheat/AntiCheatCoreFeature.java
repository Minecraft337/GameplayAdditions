package com.gameplayadditions.mechanics.features.anticheat;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AntiCheatCoreFeature — инфраструктурная база всех античит-проверок.
 * <p>
 * Содержит облегчённые порты из MC-Plugin:
 * <ul>
 *   <li>{@link CheckResult} — результат одной проверки (passed / flagged).</li>
 *   <li>{@link CheckCategory} — категория (COMBAT, MOVEMENT, WORLD, MISC).</li>
 *   <li>{@link AbstractCheck}-подобный inner-класс {@link Check} с maxVl + decay.</li>
 *   <li>{@link PlayerData} — кеш данных игрока (VL, timestamps).</li>
 *   <li>{@link CheckManager} — реестр зарегистрированных проверок.</li>
 * </ul>
 * <p>
 * Остальные 4 фичи (CombatAnitCheat, MovementAntiCheat, WorldAntiCheat, MiscAntiCheat)
 * используют {@link #manager()} для регистрации своих проверок.
 */
public class AntiCheatCoreFeature extends AbstractFeature {

    public enum CheckCategory {
        COMBAT, MOVEMENT, WORLD, MISC
    }

    /**
     * Результат проверки. Иммутабельный record.
     */
    public record CheckResult(boolean flagged, int vl, String message) {
        public static CheckResult passed() {
            return new CheckResult(false, 0, "");
        }
        public static CheckResult flagged(int vl, String message) {
            return new CheckResult(true, vl, message);
        }
    }

    // ─── Cached per-player VL data ─────────────────────────────────────────
    /**
     * Per-player storage. Хранит cumulative violation level по имени проверки.
     * <p>
     * Полный port PlayerData из MC-Plugin слишком тяжёл для этапа "compile-clean ref".
     * Здесь — минимальный набор для flag-then-warn.
     */
    public static final class PlayerData {
        final UUID uuid;
        final Map<String, AtomicInteger> violations = new ConcurrentHashMap<>();
        long lastViolationTime = 0L;

        public PlayerData(UUID uuid) {
            this.uuid = uuid;
        }

        public int getVl(String checkName) {
            AtomicInteger ai = violations.get(checkName);
            return ai == null ? 0 : ai.get();
        }

        public int incrementVl(String checkName) {
            AtomicInteger ai = violations.computeIfAbsent(checkName, k -> new AtomicInteger(0));
            int v = ai.incrementAndGet();
            lastViolationTime = System.currentTimeMillis();
            return v;
        }

        public void resetVl(String checkName) {
            violations.remove(checkName);
        }
    }

    // ─── Check parent ───────────────────────────────────────────────────────
    /**
     * Базовый класс для одной проверки. Порт AbstractCheck из MC-Plugin — урезанный.
     * <p>
     * Не требует Bukkit Listener; использует @SubscribeEvent в конкретных фичах.
     */
    public abstract static class Check {
        private final String name;
        private final CheckCategory category;
        private final int maxVl;
        private final long vlDecayMs;
        private boolean enabled = true;

        protected Check(String name, CheckCategory category, int maxVl) {
            this(name, category, maxVl, 30_000L);
        }

        protected Check(String name, CheckCategory category, int maxVl, long vlDecayMs) {
            this.name = name;
            this.category = category;
            this.maxVl = maxVl;
            this.vlDecayMs = vlDecayMs;
        }

        public String getName() { return name; }
        public CheckCategory getCategory() { return category; }
        public int getMaxVl() { return maxVl; }

        /** Логика проверки. Возвращает CheckResult. */
        protected abstract CheckResult run(ServerPlayer player, PlayerData data);

        /** Хелпер для вызова из листенера: проверяет enabled, запускает run, инкрементит VL. */
        public final void tick(ServerPlayer player) {
            if (!enabled) return;
            PlayerData data = AntiCheatCoreFeature.manager().getOrCreate(player);
            long now = System.currentTimeMillis();
            // Decay old VL
            if (data.lastViolationTime > 0 && (now - data.lastViolationTime) > vlDecayMs) {
                data.resetVl(name);
            }
            CheckResult result;
            try {
                result = run(player, data);
            } catch (Exception e) {
                ConsoleLogger.warn("[AntiCheat] " + name + " threw: " + e.getMessage());
                return;
            }
            if (result.flagged()) {
                int vl = data.incrementVl(name);
                if (vl >= maxVl / 2 && vl % 5 == 0) {
                    ConsoleLogger.warn("[AntiCheat/" + category + "] "
                            + player.getScoreboardName() + " flagged: "
                            + name + " (VL=" + vl + "/" + maxVl + ") — " + result.message());
                }
            }
        }
    }

    // ─── CheckManager: реестр проверок ──────────────────────────────────────
    /**
     * CheckManager — хранит все зарегистрированные проверки и PlayerData.
     */
    public static final class CheckManager {
        private final Map<String, Check> checks = new ConcurrentHashMap<>();
        private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

        public void register(Check check) {
            checks.put(check.getName(), check);
        }

        public PlayerData getOrCreate(ServerPlayer player) {
            UUID uuid = player.getUUID();
            return playerData.computeIfAbsent(uuid, k -> new PlayerData(uuid));
        }

        public void forget(UUID uuid) {
            playerData.remove(uuid);
        }

        public int count() { return checks.size(); }

        public void tick(ServerPlayer player) {
            for (Check c : checks.values()) {
                try {
                    c.tick(player);
                } catch (Exception e) {
                    ConsoleLogger.warn("[AntiCheat] tick " + c.getName() + " threw: " + e.getMessage());
                }
            }
        }
    }

    private static final CheckManager MANAGER = new CheckManager();

    public static CheckManager manager() { return MANAGER; }

    public AntiCheatCoreFeature() {
    }

    @Override
    public String getName() {
        return "AntiCheatCore";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        ConsoleLogger.info("[AntiCheatCore] infrastructure ready. Registered checks so far: "
                + MANAGER.count() + ".");
    }
}
