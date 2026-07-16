package com.gameplayadditions.energy.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Materials — константы блоков для энергосистемы.
 * <p>
 * Порт {@code com.mcplugin.util.Materials} из MC-Plugin.
 * Использует прямые ссылки на Blocks вместо Registry для NeoForge.
 */
public final class Materials {

    private Materials() {}

    // =========================
    // ⚡ ENERGY NETWORK
    // =========================
    /** Straight cable block. */
    public static final Block WAXED_LIGHTNING_ROD = Blocks.LIGHTNING_ROD;
    /** Corner / junction cable block. */
    public static final Block WAXED_CHISELED_COPPER = Blocks.WAXED_CHISELED_COPPER;
    /** Battery multiblock block. */
    public static final Block WAXED_COPPER_GRATE = Blocks.WAXED_COPPER_GRATE;

    // =========================
    // ⚙ GENERATOR / FURNACE
    // =========================
    /** Generator and Electric Furnace block. */
    public static final Block BLAST_FURNACE = Blocks.BLAST_FURNACE;
    /** Reactor structure block. */
    public static final Block WAXED_COPPER_BULB = Blocks.WAXED_COPPER_BULB;

    // =========================
    // 🧱 COPPER VARIANTS
    // =========================
    public static final Block WAXED_COPPER_BLOCK = Blocks.WAXED_COPPER_BLOCK;
    public static final Block WAXED_CUT_COPPER = Blocks.WAXED_CUT_COPPER;
    public static final Block WAXED_CUT_COPPER_STAIRS = Blocks.WAXED_CUT_COPPER_STAIRS;
    public static final Block WAXED_COPPER_TRAPDOOR = Blocks.WAXED_COPPER_TRAPDOOR;
}
