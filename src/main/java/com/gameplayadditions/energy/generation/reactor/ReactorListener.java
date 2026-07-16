package com.gameplayadditions.energy.generation.reactor;

import com.gameplayadditions.util.LocationUtil;
import com.gameplayadditions.util.MessageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * ReactorListener — NeoForge события для реактора.
 * <p>
 * Порт {@code com.mcplugin.energy.generation.reactor.ReactorListener} из MC-Plugin.
 * Слушает размещение/разрушение блоков структуры реактора и взаимодействие с рамками/табличками.
 */
public class ReactorListener {

    private static final BlockState[] KEY_BLOCKS = {
        Blocks.WAXED_COPPER_BULB.defaultBlockState(),
        Blocks.DIAMOND_BLOCK.defaultBlockState(),
        Blocks.GOLD_BLOCK.defaultBlockState(),
        Blocks.OAK_WALL_SIGN.defaultBlockState(),
        Blocks.DARK_OAK_WALL_SIGN.defaultBlockState(),
        Blocks.BIRCH_WALL_SIGN.defaultBlockState(),
        Blocks.SPRUCE_WALL_SIGN.defaultBlockState(),
        Blocks.JUNGLE_WALL_SIGN.defaultBlockState(),
        Blocks.ACACIA_WALL_SIGN.defaultBlockState(),
        Blocks.CHERRY_WALL_SIGN.defaultBlockState(),
        Blocks.MANGROVE_WALL_SIGN.defaultBlockState(),
        Blocks.CRIMSON_WALL_SIGN.defaultBlockState(),
        Blocks.WARPED_WALL_SIGN.defaultBlockState()
    };

    // =========================
    // BLOCK BREAK — проверка блоков реактора
    // =========================
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent e) {
        Level level = (Level) e.getLevel();
        BlockPos pos = e.getPos();
        BlockState state = e.getState();

        if (!isReactorBlock(state)) return;

        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null) return;

        BlockPos reactorLoc = reactor.getReactorPos();
        if (reactorLoc == null) return;

        if (!isWithinStructure(reactorLoc, pos)) return;

        reactor.setReactorLocation(null, null);
        if (e.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(MessageUtil.legacy(
                    "§c❕ Реактор разрушен и деактивирован!"));
        }
    }

    // =========================
    // BLOCK PLACE — ревалидация реактора
    // =========================
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        Level level = (Level) e.getLevel();
        BlockPos pos = e.getPos();
        BlockState state = e.getState();

        if (!isReactorBlock(state)) return;

        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null) return;

        if (reactor.getReactorPos() != null) {
            reactor.validateStructure();
        }
    }

    // =========================
    // HELPERS
    // =========================
    private boolean isReactorBlock(BlockState state) {
        for (BlockState key : KEY_BLOCKS) {
            if (state.is(key.getBlock())) return true;
        }
        return false;
    }

    private boolean isWithinStructure(BlockPos reactorPos, BlockPos checkPos) {
        int dx = Math.abs(reactorPos.getX() - checkPos.getX());
        int dy = Math.abs(reactorPos.getY() - checkPos.getY());
        int dz = Math.abs(reactorPos.getZ() - checkPos.getZ());
        return dx <= 3 && dy <= 5 && dz <= 3;
    }
}
