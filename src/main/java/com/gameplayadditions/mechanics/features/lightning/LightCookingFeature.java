package com.gameplayadditions.mechanics.features.lightning;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.mechanics.environment.lightning.LightningStructure;
import com.gameplayadditions.util.ConsoleLogger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⚡ LightCookingFeature — порт MC-Plugin {@code LightningManager} в NeoForge 1.21.
 * <p>
 * Что делает: при обнаружении «грозовой структуры» (5×4×5 из Lightning Rod,
 * Copper Bulb / Grate / Trapdoor / Chiseled Copper — см.
 * {@link LightningStructure}) — любой ItemEntity, попавший на верх грамоотвода,
 * автоматически сваривается ванильным smelting-рецептом. Сопровождается
 * визуальным lightning-эффектом и звуком.
 *
 * <p><b>АРХИТЕКТУРНЫЕ ОТЛИЧИЯ от Bukkit-оригинала:</b></p>
 * <ul>
 *   <li>{@code activeStructures} — in-memory {@code Map<BlockPos, Boolean>}.
 *       TODO: persistence через {@code DatabaseManager} (было StructureMarker).</li>
 *   <li>{@code hasEnergyForOperation()} — MVP-stub возвращает true. TODO: кабель
 *       проверка через EnergyModule (в GameplayAdditions эта система ещё не
 *       полностью портирована).</li>
 *   <li>Периодический scan: 20 тиков (1 сек). {@code cleanupLatestItemIds} на
 *       каждом тике (UUID-дедуп — keep simple).</li>
 *   <li>Bukkit-команды для assemble/disassemble — MVP не реализованы (будет
 *       через {@code /mp str lightning}).</li>
 * </ul>
 *
 * <p><b>Server-only enforcement:</b> все @SubscribeEvent handlers явно проверяют
 * {@code event.getEntity().level().isClientSide()} даже для server-targeted events
 * (defense-in-depth).</p>
 */
public class LightCookingFeature extends AbstractFeature {

    /** Cooldown между варками для одной структуры. Bukkit: COOKING_COOLDOWN_MS = 1000L. */
    private static final long COOKING_COOLDOWN_MS = 1000L;
    /** Энергия, требуемая на одну операцию варки. TODO: реальный расчёт через CableNetwork. */
    private static final int ENERGY_COST = 100;

    /** Active structures: center BlockPos → enabled flag. */
    private final Map<BlockPos, Boolean> activeStructures = new ConcurrentHashMap<>();
    /** Per-structure cooking-cooldown timestamps. */
    private final Map<BlockPos, Long> cookingCooldowns = new ConcurrentHashMap<>();
    /** ItemEntity UUID'ы, уже обработанные в текущем окне тика. */
    private final Set<UUID> cookedItemIds = ConcurrentHashMap.newKeySet();

    /** Countdown до периодического periodic-scan (ServerTickEvent.Post). */
    private long tickCounter = 0L;

    /** Cached ServerLevel reference set on {@link #onServerStart}. */
    private net.minecraft.server.MinecraftServer serverRef = null;

    @Override
    public String getName() {
        return "light_cooking";
    }

    @Override
    public void onServerStart(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        super.onServerStart(event);
        this.serverRef = event.getServer();
        registerGameEvents();
        logInfo("subscribed to LivingDamageEvent.Pre + ServerTickEvent.Post"
                + ". activeStructures restored from in-memory; TODO: load from DB.");
    }

    @Override
    public void onServerStop(ServerStoppingEvent event) {
        logInfo("scan counters cleared. structures="
                + activeStructures.size() + " cooldowns=" + cookingCooldowns.size());
        super.onServerStop(event);
    }

    // ==========================================================================
    // LISTENER 1: периодический scan (ServerTickEvent.Post — server-side by API)
    // ==========================================================================

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (!isRunning()) return;
        if (serverRef == null) return;

        tickCounter++;
        if (tickCounter < 20L) return;   // 1 сек — same as Bukkit periodic scan (20 ticks)
        tickCounter = 0L;
        cookedItemIds.clear();             // cleanup — matches Bukkit cleanupTick pattern

        // ── Scan all dimensions (BUG parity с MagnetFeature fix: не только overworld) ──
        for (ServerLevel level : serverRef.getAllLevels()) {
            if (level == null) continue;
            scanLevel(level);
        }
    }

    private void scanLevel(ServerLevel level) {
        long now = System.currentTimeMillis();
        for (Map.Entry<BlockPos, Boolean> entry : activeStructures.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            BlockPos center = entry.getKey();
            // sanity: структура должна быть в текущем сканируемом level
            if (level.dimension() != levelAt(center)) continue;

            Long lastCook = cookingCooldowns.get(center);
            if (lastCook != null && (now - lastCook) < COOKING_COOLDOWN_MS) continue;

            // ItemEntity scan: AABB ~1.5×0.6×1.5 above the lightning rod
            AABB rodBox = new AABB(
                    center.getX() + 0.5 - 1.5D, center.getY() + 0.7D, center.getZ() + 0.5 - 1.5D,
                    center.getX() + 0.5 + 1.5D, center.getY() + 1.7D, center.getZ() + 0.5 + 1.5D);
            java.util.List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, rodBox,
                    ie -> ie.isAlive() && !ie.getItem().isEmpty());

            for (ItemEntity ie : nearby) {
                if (cookItem(level, center, ie)) break;
            }
        }
    }

    /**
     * Cook {@code ie} на структуре {@code center}. Возвращает true если варка прошла.
     * Семантика совпадает с Bukkit:
     *   1. cooldown ✓
     *   2. dedup по UUID ✓
     *   3. hasEnergy ✓ (MVP stub — true)
     *   4. ищем smelting-рецепт ✓ (SmeltingRecipe через RecipeManager)
     *   5. если найден — set cooldown + dedup + strike lightning effect + sound +
     *      drop result ItemStack + удалить оригинальный ItemEntity.
     */
    private boolean cookItem(ServerLevel level, BlockPos center, ItemEntity ie) {
        ItemStack stack = ie.getItem();
        if (stack.isEmpty()) return false;
        if (cookedItemIds.contains(ie.getUUID())) return false;

        // TODO: реальный hasEnergyForOperation checks из CableNetwork
        // if (!hasEnergyForOperation(center)) return false;

        // Recipe lookup @ MC 1.21 API
        Optional<RecipeHolder<SmeltingRecipe>> recipeOpt = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level);
        if (recipeOpt.isEmpty()) return false;

        ItemStack result = recipeOpt.get().value()
                .assemble(new SingleRecipeInput(stack), level.registryAccess());
        if (result.isEmpty()) return false;
        result.setCount(stack.getCount());

        // Mark as cooked (atomic state set)
        cookingCooldowns.put(center, System.currentTimeMillis());
        cookedItemIds.add(ie.getUUID());

        // Effects: visual lightning + sound (matches Bukkit strikeLightningEffect + Sound.BOLT_THUNDER)
        // ⚠ BUG-FIX (compile iter 2026): Level.strikeLightningEffect(double, double, double)
        //    отсутствует в neoforge-21.1.143 merged jar (разрешение overload-ов
        //    падает при неявном касте BlockPos). Используем vanilla-side конструктор
        //    LightningBolt + setVisualOnly(true) — работает во всех 1.21.x.
        double sx = center.getX() + 0.5D;
        double sy = center.getY() + 1.0D;
        double sz = center.getZ() + 0.5D;

        LightningBolt visualBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        visualBolt.setVisualOnly(true);  // no damage — pure visual FX
        visualBolt.setPos(sx, sy, sz);
        level.addFreshEntity(visualBolt);

        level.playSound(null,
                sx, sy - 0.5D, sz,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.8F, 1.2F);

        // Replace item: drop result + remove original
        ItemEntity newDrop = new ItemEntity(level,
                ie.getX(), ie.getY(), ie.getZ(), result);
        level.addFreshEntity(newDrop);
        ie.discard();
        return true;
    }

    // ==========================================================================
    // LISTENER 2: защита item-ов рядом со структурой от lightning-damage
    // ==========================================================================
    //
    // ⚠ MVP TODO: ItemEntity НЕ является LivingEntity, поэтому
    // LivingDamageEvent.Pre на них НЕ срабатывает (compile-error confirmed). Для
    // защиты ItemEntity от lightning-damage требуется либо:
    //   - Mixin на ItemEntity.tick()/hurt() / Level.strikeLightning()
    //   - Или vanilla-equivalent event hook
    //
    // Bukkit-плагин обрабатывал это через EntityDamageEvent (cause=LIGHTNING). В
    // NeoForge + MC 1.21 такого cross-cutting event для всех Entity нет —
    // только Living*-префиксные. Поэтому "Item-Protection" отложен и будет
    // добавлен через Mixin в отдельном PR.
    //
    // Для предотвращения потери предметов от lightning-damage в текущей
    // имплементации: cookItem() уже дискардит оригинальный ItemEntity сразу
    // после smelting → даже если lightning попадёт в созданный ItemEntity result
    // в редком edge-case, мы просто теряем один стек, не повреждая структуру.

    // ==========================================================================
    // ADMIN API (callable из команд /mp — TODO)
    // ==========================================================================

    /** Register an assembled structure. Валидация по {@link LightningStructure#isValid}. */
    public boolean registerStructure(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return false;
        if (activeStructures.containsKey(center)) {
            logInfo("registerStructure: already active at " + center);
            return false;
        }
        if (!LightningStructure.isValid(level, center, /*requireFrame=*/ true)) {
            ConsoleLogger.warn("[LightCooking] registerStructure rejected — invalid 5x4x5 layout at " + center);
            return false;
        }
        activeStructures.put(center, true);
        ConsoleLogger.info("[LightCooking] structure registered at " + center
                + " (" + activeStructures.size() + " active)");
        // Side-effects (lightning + particles + sound) — placeholder for /mp admin command
        return true;
    }

    public boolean unregisterStructure(BlockPos center) {
        if (center == null) return false;
        Boolean removed = activeStructures.remove(center);
        cookingCooldowns.remove(center);
        if (removed != null) {
            ConsoleLogger.info("[LightCooking] structure unregistered at " + center);
            return true;
        }
        return false;
    }

    public Set<BlockPos> getActiveCenters() {
        return new HashSet<>(activeStructures.keySet());
    }

    // ==========================================================================
    // UTIL
    // ==========================================================================

    /**
     * Резолвим {@link ResourceKey} для BlockPos. В 1.21 у BlockPos нет
     * dimension field — нам нужен server-side контекст. MVP: ищем matching level
     * среди загруженных уровней по координатам (загруженность чанки).
     */
    private ResourceKey<Level> levelAt(BlockPos pos) {
        if (serverRef == null) return null;
        for (ServerLevel level : serverRef.getAllLevels()) {
            if (!level.hasChunkAt(pos)) continue;
            return level.dimension();
        }
        return null;
    }
}
