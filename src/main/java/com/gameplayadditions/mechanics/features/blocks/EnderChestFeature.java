package com.gameplayadditions.mechanics.features.blocks;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of {@code com.mcplugin.mechanics.features.blocks.EnderChestManager}.
 *
 * <p>Adds a small open/close cost and a tiny randomised explosion risk to
 * vanilla ender chests. Skips creative/spectator. Defaults mirror the
 * MC-Plugin values (1 HP per open/close, 0.1 % explosion chance @ power 10).</p>
 *
 * <p>TODO: integrate {@code StructureIntegrityManager.onEnderChestInteract(...)}
 * — that manager is part of the still-to-port {@code features/structure/}
 * subsystem; for now we log a no-op marker and move on. The public API
 * {@link #addEnderseeViewer(UUID)} is kept so future port can wire it back in.</p>
 */
public class EnderChestFeature extends AbstractFeature {

    // ─── Config (hard-coded; will move to YAML once config layer is unified) ─
    private static boolean enabled = true;
    private static double explosionChance = 0.001D;   // 0.1 %
    private static double explosionPower = 10.0D;
    private static double damage = 1.0D;

    private static final Set<UUID> enderseeViewers = ConcurrentHashMap.newKeySet();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, BlockPos> lastOpenedChest = new ConcurrentHashMap<>();

    /** Public hook used by the future {@code Endersee} port to mark a viewer — bypasses damage. */
    public static void addEnderseeViewer(UUID uuid) {
        enderseeViewers.add(uuid);
    }

    @Override
    public String getName() {
        return "ender_chest";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        logInfo("subscribed to PlayerInteractEvent.RightClickBlock + PlayerContainerEvent.Close.");
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!enabled) return;
        if (event.getLevel().isClientSide()) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.ENDER_CHEST)) return;

        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) return;

        // Damage per open.
        if (damage > 0) {
            float before = player.getHealth();
            player.hurt(event.getLevel().damageSources().generic(), (float) damage);
            float after = player.getHealth();
            ConsoleLogger.info("[EnderChest] " + player.getName().getString()
                    + " health: " + String.format("%.1f", before)
                    + " → " + String.format("%.1f", after)
                    + " (damage=" + damage + ")");
        }

        // Stub for StructureIntegrityManager — TODO: wire when that lands.
        // StructureIntegrityManager.getInstance().onEnderChestInteract(event.getPos());

        lastOpenedChest.put(player.getUUID(), event.getPos());

        // Random explosion check (default 0.1 %).
        double roll = RANDOM.nextDouble();
        if (roll >= explosionChance) return;

        explodeChest(player, event.getLevel(), event.getPos(), roll);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!enabled) return;
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        // We track ender-chest opens via BlockPos; verify the underlying block
        // is still an ender chest before applying close damage. The original
        // MC-Plugin relied on `InventoryType`. In NeoForge we don't have a
        // 1:1 InventoryType mapping; the BlockPos check is equivalent and
        // safely skips ALL non-ender-chest closes.
        BlockPos chestPos = lastOpenedChest.get(player.getUUID());
        if (chestPos == null) return;
        if (!player.level().getBlockState(chestPos).is(Blocks.ENDER_CHEST)) return;

        if (enderseeViewers.remove(player.getUUID())) {
            lastOpenedChest.remove(player.getUUID());
            return;
        }

        // Damage per close (same magnitude as open — matches MC-Plugin).
        if (damage > 0) {
            player.hurt(player.level().damageSources().generic(), (float) damage);
        }

        // Stub: StructureIntegrityManager.onEnderChestInteract(chestPos);
        lastOpenedChest.remove(player.getUUID());
    }

    /**
     * Mirror of MC-Plugin's {@code explodeChest(...)}: destroy the chest block,
     * create an explosion with block damage, award the datapack advancement.
     */
    private void explodeChest(Player player, Level level, BlockPos pos, double roll) {
        // Destroy the chest block.
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

        // Create explosion (MC 1.21 signature; block-damage enabled).
        if (explosionPower > 0) {
            level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    (float) explosionPower, false, Level.ExplosionInteraction.BLOCK);
        }

        // Award the "blowed_by_echest" advancement (deferred — the datapack that
        // registers it isn't shipped with the mod, and the NeoForge 1.21 API for
        // PlayerAdvancements#award(...) has shifted around across point releases.
        // TODO: re-wire during the IntegrityManager port once we pin down the
        // exact PlayerAdvancements signature on NeoForge 21.1.143. Skipping
        // here is safe — the explosion + damage still fire; only the achievement
        // banner is missed.
        ConsoleLogger.debug("[EnderChest] explosion fired (advancement award deferred)");

        ConsoleLogger.warn("[EnderChest] " + player.getName().getString()
                + " opened an ender chest at "
                + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " and it EXPLODED! (roll=" + String.format("%.6f", roll)
                + " < chance=" + explosionChance + ")");
    }
}
