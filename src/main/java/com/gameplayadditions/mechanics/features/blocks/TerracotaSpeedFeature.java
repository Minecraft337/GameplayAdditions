package com.gameplayadditions.mechanics.features.blocks;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * TerracotaSpeedFeature — Speed V на игроке, стоящем на MAGENTA_GLAZED_TERRACOTTA.
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.blocks.TerracotaSpeedManager} (40 строк).
 * Оригинал использовал {@code BukkitRunnable} каждые 5 тиков — здесь заменено
 * на {@link PlayerTickEvent.Pre}. Эффект продлевается каждый тик, пока игрок стоит
 * на блоке (минимальный gap по сравнению с BukkitRunnable интервалом).
 * <p>
 * Поведение идентично оригиналу: bypass creative/spectator, refresh эффекта
 * ужесточает (без gap-ов), но зато гарантирует непрерывный Speed V.
 */
public class TerracotaSpeedFeature extends AbstractFeature {

    public static final boolean ENABLED = true;
    /** Длительность эффекта в тиках. Совпадает с MC-Plugin'ом (40 = 2 сек). */
    public static final int DURATION_TICKS = 40;
    /** Уровень эффекта: amplifier=4 → Speed V (как в MC-Plugin speedAmp=4). */
    public static final int AMPLIFIER = 4;

    @Override
    public String getName() {
        return "terracota_speed";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        logInfo("subscribed to PlayerTickEvent.Pre.");
        super.onServerStart(event);
    }

    /**
     * Каждый тик проверяем блок ПОД ногами игрока. Если это MAGENTA_GLAZED_TERRACOTTA —
     * накладываем/обновляем Speed V. {@code player.addEffect} автоматически заменяет
     * предыдущий инстанс эффекта с большей длительностью (если есть).
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!ENABLED) return;
        if (event.getEntity().level().isClientSide()) return;

        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) return;

        BlockPos below = player.blockPosition().below();
        if (!player.level().getBlockState(below).is(Blocks.MAGENTA_GLAZED_TERRACOTTA)) return;

        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                DURATION_TICKS,
                AMPLIFIER,
                false,  // ambient
                true,   // visible particles
                true    // show icon
        ));
    }
}
