package com.gameplayadditions.mechanics.features.world;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * DeathBellFeature — при взаимодействии с блоком {@link Blocks#BELL} бьёт молнией в источник удара.
 *
 * <p>Порт {@code com.mcplugin.mechanics.features.world.DeathBellManager} из MC-Plugin.
 *
 * <p>В Bukkit использовался {@code BellRingEvent} — у NeoForge нет прямого аналога,
 * поэтому мы перехватываем {@link PlayerInteractEvent.RightClickBlock} на блоке колокола.
 * Это покрывает ~90% случаев (игрок кликает по колоколу). Полное покрытие (включая
 * удары снарядами) потребовало бы Mixin.
 *
 * <p>Приоритет цели для молнии:
 * <ol>
 *   <li>Игрок (если ударил по колоколу)</li>
 *   <li>Блок колокола (fallback — точка удара)</li>
 * </ol>
 *
 * <p>Конфигурация (дефолты совпадают с Bukkit):
 * <ul>
 *   <li>{@code features.deathbell.enabled=true}</li>
 *   <li>{@code features.deathbell.lightning=true} — спавнить молнию</li>
 * </ul>
 */
public class DeathBellFeature extends AbstractFeature {

    // TODO(config): перенести в ConfigManager
    private boolean enabled = true;
    private boolean lightning = true;

    @Override
    public String getName() {
        return "deathbell";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!enabled || !lightning) {
            return;
        }
        // PlayerInteractEvent уже серверный, но explicit guard — defense-in-depth
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL)) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();
        Player player = event.getEntity();
        // Приоритет: позиция игрока → позиция колокола (fallback)
        BlockPos target = player != null ? player.blockPosition() : event.getPos();
        strikeLightning(level, target);
    }

    private void strikeLightning(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        // Vanilla LightningBolt construction (EntityType, Level) — канонический 1.21.x подход
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        // Центрируем по X/Z; Y оставляем как у блока (визуально бьёт сверху)
        bolt.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(bolt);
    }
}
