package com.gameplayadditions.mechanics.features.movement;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 🧊 BlockFrictionFeature — кастомное трение для блоков.
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.movement.BlockFrictionListener}
 * (Bukkit, ~169 строк). Позволяет переопределить трение определённых блоков и
 * получить либо тормоз (<1.0, напр. Soul Sand 0.4), либо ускорение (>1.0, экспоненциальный
 * буст на льду). Hard cap 50 m/s — защита от экс-эксплойтов.
 * <p>
 * <b>АРХИТЕКТУРНЫЕ ОТЛИЧИЯ от Bukkit:</b><br>
 * Bukkit {@code PlayerMoveEvent} — pre-move event с per-tick delta. В NeoForge 1.21
 * нет прямого аналога без Mixin. Принятая стратегия: подписка на
 * {@link PlayerTickEvent.Post}, проверка ground-state + horizontal-speed threshold,
 * применение friction к {@code player.getDeltaMovement()}. Это РАБОТАЕТ
 * с one-tick delay vs Bukkit (acceptable).
 * <p>
 * <b>ГАРАНТИЯ server-only:</b> все @SubscribeEvent handlers явно guard'нуты
 * {@code event.getEntity().level().isClientSide()}. {@code hurtMarked = true}
 * гарантирует отправку velocity-packet клиенту.
 */
public class BlockFrictionFeature extends AbstractFeature {

    /** {@code Block -> customFriction}. custom / 0.6 = multiplier для velocity. */
    private final Map<Block, Double> frictionMap = new HashMap<>();

    /** Стандартное трение vanilla MC для большинства блоков. */
    private static final double DEFAULT_FRICTION = 0.6;
    /** Хард-кап anti-exploit: max horizontal speed. */
    private static final double MAX_SPEED = 50.0;
    /** Square-distance threshold — fast-path выход для стоячего игрока. */
    private static final double MIN_HORIZONTAL_SPEED_SQR = 0.000001D;

    @Override
    public String getName() {
        return "block_friction";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        // MAP с дефолтами, имитирующими загрузку из MC-Plugin config:
        //   block_friction.<block-id>: <friction_value>
        //
        // В релизе TODO: load from GameplayAdditions/ConfigManager через YAML/TOML —
        // или чтение из ForgeConfigSpec.Builder.

        Map<String, Double> configStrings = Map.ofEntries(
                Map.entry("minecraft:soul_sand", 0.4),
                Map.entry("minecraft:soul_soil", 0.4),
                Map.entry("minecraft:slime_block", 0.8),
                Map.entry("minecraft:ice", 0.98),
                Map.entry("minecraft:packed_ice", 0.98),
                Map.entry("minecraft:blue_ice", 0.989),
                // Кастомные блоки из мода для скоростных-ледяных дорог
                Map.entry("minecraft:magenta_glazed_terracotta", 1.1)
        );

        frictionMap.clear();
        int loaded = 0;
        for (Map.Entry<String, Double> entry : configStrings.entrySet()) {
            ResourceLocation loc = ResourceLocation.tryParse(entry.getKey());
            if (loc == null) continue;
            Block block = BuiltInRegistries.BLOCK.getOptional(loc).orElse(null);
            if (block == null) {
                logWarn("Unknown block in friction config: " + entry.getKey());
                continue;
            }
            frictionMap.put(block, entry.getValue());
            loaded++;
        }
        logInfo("setup complete. Loaded " + loaded + " block friction values (out of "
                + configStrings.size() + " configured).");
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        logInfo("subscribed to PlayerTickEvent.Post (server-only by API).");
        super.onServerStart(event);
    }

    /**
     * Применяем кастомное трение. Вызывается на серверном post-tick для каждого игрока.
     */
    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // 🚫 Server-only guard (defense-in-depth)
        if (player.level().isClientSide()) return;
        // Fast-path: empty map или не на земле
        if (frictionMap.isEmpty() || !player.onGround()) return;

        Vec3 vel = player.getDeltaMovement();
        // Fast-path: нет горизонтального движения
        if (vel.horizontalDistanceSqr() < MIN_HORIZONTAL_SPEED_SQR) return;

        // Получаем блок под ногами (snow/half-blocks корректно работают в 1.21)
        BlockPos onPos = player.getOnPos();
        Block block = player.level().getBlockState(onPos).getBlock();
        Double customFriction = frictionMap.get(block);
        if (customFriction == null) return;  // vanilla default — нет модификации

        // BUG-FIX (review 2026): защита от stuck / NaN / Infinity.
        //    customFriction <= 0 → multiplier <= 0 → vel = 0 → игрок не может
        //    стартовать движение даже при попытке прыжка. Это жёсткий DOS-chaining.
        //    Double.isFinite() защищает от NaN/Infinity propagation в vel.
        if (customFriction <= 0.0 || !Double.isFinite(customFriction)) return;

        // multiplier: friction > 1.0 → буст, friction < 1.0 → тормоз
        double multiplier = customFriction / DEFAULT_FRICTION;
        double newX = vel.x * multiplier;
        double newZ = vel.z * multiplier;

        // hard-cap anti-exploit с защитой от деления на ноль
        double speed = Math.sqrt(newX * newX + newZ * newZ);
        if (speed > MAX_SPEED) {
            double scale = MAX_SPEED / Math.max(speed, 0.0001D);
            newX *= scale;
            newZ *= scale;
        }

        // Защита от NaN/Infinity (defence-in-depth: вдруг кастомный friction имеет мусор)
        if (Double.isNaN(newX) || Double.isInfinite(newX)) newX = 0.0;
        if (Double.isNaN(newZ) || Double.isInfinite(newZ)) newZ = 0.0;

        // Применяем новую скорость
        player.setDeltaMovement(newX, vel.y, newZ);

        // ⚠ Критично для MC 1.21:
        //   В 1.21 отправить SetEntityMotionPacket клиенту можно через hurtMarked = true.
        //   Без этого флага ванильный клиент игнорирует server-side установку velocity
        //   и визуально остаётся на старой скорости. Игрок будет двигаться по логике,
        //   но видеть себя — нет.
        player.hurtMarked = true;
    }
}
