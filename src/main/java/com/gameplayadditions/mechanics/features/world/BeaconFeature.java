package com.gameplayadditions.mechanics.features.world;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * BeaconFeature — периодическое наложение эффектов зелий на игроков, стоящих на {@link Blocks#BEACON}.
 *
 * <p>Порт {@code com.mcplugin.mechanics.features.world.BeaconManager} из MC-Plugin.
 * В Bukkit использовался {@code BukkitRunnable}; в NeoForge — {@link ServerTickEvent}.
 *
 * <p>Конфигурация (по умолчанию совпадает с Bukkit):
 * <ul>
 *   <li>{@code enabled=true} — вкл/выкл механику</li>
 *   <li>{@code glowing=true} — накладывать {@link MobEffects#GLOWING}</li>
 *   <li>{@code blindness=true} — накладывать {@link MobEffects#BLINDNESS}</li>
 *   <li>{@code regeneration_amplifier=4} — амплитуда регенерации</li>
 *   <li>{@code resistance_amplifier=4} — амплитуда сопротивления</li>
 *   <li>{@code interval_ticks=5} — период тика</li>
 * </ul>
 *
 * <p>Первый тик пропускается (initial delay=20) — имитация Bukkit-инициализации.
 */
public class BeaconFeature extends AbstractFeature {

    private int tickCounter = 0;

    // TODO(config): перенести в ConfigManager (читать из JSON мод-конфига)
    private boolean enabled = true;
    private boolean giveGlowing = true;
    private boolean giveBlindness = true;
    private int regenAmp = 4;
    private int resistAmp = 4;
    private int intervalTicks = 5;

    @Override
    public String getName() {
        return "beacon";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (!enabled) {
            return;
        }
        tickCounter++;
        // Bukkit-инициализация стартовала после 20 тиков — повторяем
        if (tickCounter < 20) {
            return;
        }
        if (tickCounter % intervalTicks != 0) {
            return;
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            // ServerTickEvent гарантированно server, но defense-in-depth на всякий случай
            if (player.level().isClientSide()) {
                continue;
            }
            BlockPos below = player.blockPosition().below();
            if (!player.level().getBlockState(below).is(Blocks.BEACON)) {
                continue;
            }
            // duration=40 тиков = 2 сек; ambient=false; visible=true
            if (giveGlowing) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, true));
            }
            if (giveBlindness) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, true));
            }
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, regenAmp, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, resistAmp, false, true));
        }
    }
}
