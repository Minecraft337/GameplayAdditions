package com.gameplayadditions.mechanics.features.blocks;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * GlassBreakFeature — non-empty-hand glass breaking hurts the player.
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.blocks.GlassBreakManager} (78 строк).
 * Логика:
 * <ol>
 *   <li>Игрок ломает стекло (любое: vanilla или stained, full block или pane);</li>
 *   <li>Главная рука ПУСТАЯ (без предмета);</li>
 *   <li>Игрок не в Creative;</li>
 *   <li>Наносим урон {@link #DAMAGE} + предупреждение в action bar.</li>
 * </ol>
 * <p>
 * Vanilla tags {@code minecraft:glass} (включает все stained варианты) и
 * {@code minecraft:glass_panes} (тоже все stained) покрывают СПИСОК из Bukkit —
 * не нужно перечислять 32 материала вручную.
 */
public class GlassBreakFeature extends AbstractFeature {

    public static final boolean ENABLED = true;
    public static final float DAMAGE = 5.0F;

    private static final TagKey<Block> GLASS_TAG =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("minecraft", "glass"));
    private static final TagKey<Block> PANES_TAG =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("minecraft", "glass_panes"));

    @Override
    public String getName() {
        return "glass_break";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        logInfo("subscribed to BlockEvent.BreakEvent.");
        super.onServerStart(event);
    }

    /**
     * Подписка через {@link #registerGameEvents()}. Срабатывает ДО фактического
     * разрушения блока; мы НЕ отменяем событие (стекло всё равно сломается по плану),
     * только наказываем за «голые руки».
     */
    @SubscribeEvent
    public void onGlassBreak(BlockEvent.BreakEvent event) {
        if (!ENABLED) return;

        // NeoForge 21.1.x: BlockEvent.getLevel() возвращает {@link LevelAccessor}
        // (интерфейс, реализованный и клиентским, и серверным Level'ом).
        // Проверяем client-side через интерфейс, затем безопасно кастуем в Level
        // — после isClientSide()==гарантировано серверная ветка.
        LevelAccessor accessor = event.getLevel();
        if (accessor.isClientSide()) return;
        Level level = (Level) accessor;

        BlockState state = event.getState();
        if (!state.is(GLASS_TAG) && !state.is(PANES_TAG)) return;

        Player player = event.getPlayer();
        if (player.isCreative() || player.isSpectator()) return;
        if (!player.getMainHandItem().isEmpty()) return;   // bare hands only

        player.hurt(level.damageSources().generic(), DAMAGE);

        player.displayClientMessage(
                Component.literal("Don't break glass with bare hands!")
                        .withStyle(ChatFormatting.RED),
                true  // action bar
        );
    }
}
