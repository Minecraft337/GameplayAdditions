package com.gameplayadditions.mechanics.features.blocks;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * BoostedCobwebFeature — игрок в cobweb полностью останавливается по горизонтали
 * и вверх, но МОЖЕТ падать вниз (гравитация сквозь паутину).
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.blocks.BoostedCobwebManager}
 * (76 строк). Замена {@code PlayerMoveEvent} (Paper) на {@link PlayerTickEvent.Pre}
 * (NeoForge) — нет прямого аналога move-cancel event, поэтому проверяем позицию
 * и движение каждый тик.
 * <p>
 * Эффекты Cobweb от ванили всё ещё действуют (замедление через атрибуты),
 * мы дополнительно зануляем горизонтальное и восходящее движение.
 */
public class BoostedCobwebFeature extends AbstractFeature {

    public static final boolean ENABLED = true;

    @Override
    public String getName() {
        return "boosted_cobweb";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        logInfo("subscribed to PlayerTickEvent.Pre.");
        super.onServerStart(event);
    }

    /**
     * На каждом тике проверяем, стоит ли игрок на cobweb. Если да, и он пытается
     * двигаться (X|Z ≠ 0 или Y > 0) — обнуляем горизонтальное и восходящее,
     * сохраняя Y для падения.
     * <p>
     * Pre-event срабатывает до применения motion; наш setDeltaMovement заменит
     * предстоящий шаг физики.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!ENABLED) return;
        if (event.getEntity().level().isClientSide()) return;

        Player player = event.getEntity();
        BlockPos feetPos = player.blockPosition();
        if (!player.level().getBlockState(feetPos).is(Blocks.COBWEB)) return;

        Vec3 motion = player.getDeltaMovement();
        boolean wantsHorizontalOrUp = motion.x != 0.0 || motion.z != 0.0 || motion.y > 0.0;
        if (!wantsHorizontalOrUp) return;

        // Разрешаем только падение: новый Y = min(0, current Y).
        double newY = Math.min(0.0, motion.y);
        player.setDeltaMovement(0.0, newY, 0.0);
        player.hasImpulse = true;

        player.displayClientMessage(
                Component.literal("You cannot move in a cobweb!")
                        .withStyle(ChatFormatting.RED),
                true  // action bar
        );
    }
}
