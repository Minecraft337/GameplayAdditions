package com.gameplayadditions.mechanics.environment.magnet;

import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MagnetManager — регистр центров магнитов и поток pull-силы на ItemEntity.
 * <p>
 * Порт логики из MC-Plugin (976 строк) урезан до MVP:
 * <ul>
 *   <li>FLOOD-FILL по LODESTONE в MC-Plugin не реализован — координаты центров
 *       хранятся вручную (через будущую server-команду {@code /mp magnet add}).</li>
 *   <li>AABB-притяжение {@code ItemEntity} — каждый {@link com.gameplayadditions.mechanics.environment.magnet.MagnetFeature#onServerTick}</li>
 * </ul>
 * <p>
 * Структура данных потокобезопасна: {@link ConcurrentHashMap} + immutable
 * snapshot при чтении.
 */
public class MagnetManager {

    private final Map<String, List<BlockPos>> centersByWorld = new ConcurrentHashMap<>();

    private static MagnetManager INSTANCE;

    public static MagnetManager get() {
        if (INSTANCE == null) INSTANCE = new MagnetManager();
        return INSTANCE;
    }

    /** Загрузить центры из БД (вызывается на setup фичи). */
    public void loadFromDatabase() {
        MagnetDatabase.loadAll(centersByWorld);
    }

    /** Сохранить текущие центры в БД (вызывается на остановке сервера). */
    public void persistToDatabase() {
        MagnetDatabase.persistAll(centersByWorld);
    }

    /**
     * Зарегистрировать центр магнита в мире.
     */
    public synchronized void addCenter(String worldId, BlockPos pos) {
        centersByWorld.computeIfAbsent(worldId, k -> new ArrayList<>()).add(pos.immutable());
    }

    /**
     * Удалить центр магнита в мире (по координатам).
     */
    public synchronized boolean removeCenter(String worldId, BlockPos pos) {
        List<BlockPos> list = centersByWorld.get(worldId);
        if (list == null) return false;
        boolean removed = list.removeIf(p -> p.equals(pos));
        if (list.isEmpty()) centersByWorld.remove(worldId);
        return removed;
    }

    /** Является ли данная позиция активным центром (точное совпадение). */
    public synchronized boolean isActiveAt(String worldId, BlockPos pos) {
        List<BlockPos> list = centersByWorld.get(worldId);
        if (list == null) return false;
        for (BlockPos p : list) {
            if (p.equals(pos)) return true;
        }
        return false;
    }

    /** Immutable snapshot центров для текущего уровня. */
    public List<BlockPos> getCenters(String worldId) {
        List<BlockPos> list = centersByWorld.get(worldId);
        if (list == null) return Collections.emptyList();
        return Collections.unmodifiableList(list);
    }

    /** Полный snapshot всех центров (defensive copy). */
    public Map<String, List<BlockPos>> snapshotAll() {
        return MagnetDatabase.snapshot(centersByWorld);
    }

    /**
     * Тик притяжения: для каждого центра этого уровня — собрать ItemEntity
     * в радиусе и приложить силу по направлению к центру.
     * <p>
     * Идемпотентен: вызывается на каждом {@code ServerTickEvent.Pre}, поэтому
     * один тик — одна итерация.
     * <p>
     * Rate-limit на тик: {@link #MAX_ITEMS_PER_TICK} — защита от давления
     * на main thread при большом числе ловушек и предметов.
     */
    public synchronized void tickPull(ServerLevel level) {
        if (!MagnetConfig.isEnabled()) return;
        String worldId = level.dimension().location().toString();
        List<BlockPos> list = centersByWorld.get(worldId);
        if (list == null || list.isEmpty()) return;

        final int radius = MagnetConfig.MAX_RADIUS;
        final int cap = MAX_ITEMS_PER_TICK;
        int touched = 0;
        // Итерируем до cap per tick — остальные подождут следующего тика.
        outer:
        for (BlockPos center : list) {
            AABB box = new AABB(center).inflate(radius);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
            for (ItemEntity item : items) {
                if (touched >= cap) break outer;
                if (!item.isAlive()) continue;
                double dx = center.getX() + 0.5 - item.getX();
                double dy = center.getY() + 0.5 - item.getY();
                double dz = center.getZ() + 0.5 - item.getZ();
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < 0.5) continue;  // уже почти в центре
                double d = Math.sqrt(d2);
                double falloff = 1.0 / Math.max(1.0, d2 / 16.0);
                double force = Math.min(MagnetConfig.FORCE_MAX,
                        MagnetConfig.FORCE_BASE + falloff * MagnetConfig.FORCE_DIST_MULT);
                item.setDeltaMovement(item.getDeltaMovement().add(
                        dx / d * force,
                        dy / d * force + MagnetConfig.ITEM_Y_BOOST,
                        dz / d * force));
                item.hasImpulse = true;
                touched++;
            }
        }
    }

    /**
     * Потолок обработанных ItemEntity за один тик. 5 центров × 500+
     * предметов в радиусе = 2500 вызовов — на 20 TPS риск микрофриза.
     * 200 за тик — практичный дефолт; конфиг будет позже.
     */
    public static final int MAX_ITEMS_PER_TICK = 200;
}
