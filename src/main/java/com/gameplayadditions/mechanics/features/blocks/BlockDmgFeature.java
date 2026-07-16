package com.gameplayadditions.mechanics.features.blocks;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Port of {@code com.mcplugin.mechanics.features.blocks.BlockDmgManager}.
 *
 * <p>Applies tick damage to a player when they stand on a {@code POINTED_DRIPSTONE}
 * block or on an {@code END_ROD} whose facing is {@code UP}. Players in creative
 * or spectator mode are skipped, and only players whose feet are flush with the
 * block (Y-offset &le; 0.1) take damage — matches the MC-Plugin
 * &quot;standing on block&quot; check.</p>
 *
 * <p>Damage amounts are not user-tunable yet; defaults mirror the MC-Plugin values
 * (1 HP per standing tick). Future change: source these from a config file
 * (deferred — see {@code MagnetConfig} for the planned pattern).</p>
 */
public class BlockDmgFeature extends AbstractFeature {

    /** Default damage applied when the player stands on a pointed dripstone. */
    private static final float DRIPSTONE_DMG = 1.0F;

    /** Default damage applied when the player stands on an upward-facing end rod. */
    private static final float END_ROD_DMG = 1.0F;

    /** Max Y distance above the block at which the player still counts as "standing on" it. */
    private static final double STANDING_Y_TOLERANCE = 0.1D;

    @Override
    public String getName() {
        return "block_dmg";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        // Subscribe this instance to NeoForge.EVENT_BUS (parent's helper, idempotent).
        registerGameEvents();
        super.onServerStart(event);
    }

    /**
     * Fires at the start of every player tick — drop-in for MC-Plugin's
     * "every N ticks" BukkitRunnable scheduler.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        // 🚫 PlayerTickEvent.Pre срабатывает на ОБЕИХ сторонах (клиент + сервер).
        //    player.hurt() — серверный damage-applier; вызов с клиента даёт ghost-damage
        //    или лаг. Гарантируем server-only.
        if (player.level().isClientSide()) return;

        // Skip creative / spectator — same filter as MC-Plugin.
        if (player.isCreative() || player.isSpectator()) return;
        // BUG-FIX (item 2026): invalid check. Иначе dripstone/end-rod ломает фреймы
        // неуязвимости (Creative += effect resistance и пр.).
        if (player.isInvulnerable()) return;

        BlockPos feetPos = player.blockPosition();
        BlockState feet = player.level().getBlockState(feetPos);

        // Y offset check: only counts as "standing on" when feet are flush
        // with the block surface (player Y minus block Y <= tolerance).
        double yOffset = player.getY() - feetPos.getY();
        if (yOffset > STANDING_Y_TOLERANCE) return;

        // Pointed dripstone at the feet — always hurts, regardless of orientation.
        if (feet.is(Blocks.POINTED_DRIPSTONE)) {
            player.hurt(player.level().damageSources().generic(), DRIPSTONE_DMG);
            return;
        }

        // End rod — only hurts when its FACING is UP (the rod points upward,
        // i.e. the player is standing on its tip).
        if (feet.is(Blocks.END_ROD)
                && feet.getValue(EndRodBlock.FACING) == Direction.UP) {
            player.hurt(player.level().damageSources().generic(), END_ROD_DMG);
        }
    }
}
