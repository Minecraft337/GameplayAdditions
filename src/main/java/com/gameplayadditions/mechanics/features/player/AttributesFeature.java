package com.gameplayadditions.mechanics.features.player;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * AttributesFeature — периодическое принудительное задание атрибутов игрокам.
 *
 * <p>Порт {@code com.mcplugin.mechanics.features.player.AttributesManager} из MC-Plugin.
 * Bukkit: {@code BukkitRunnable} каждые 200 тиков (10 сек) задаёт {@code ATTACK_DAMAGE=0.1},
 * {@code SNEAKING_SPEED=1.0}, {@code ATTACK_SPEED=3.5}.
 * NeoForge: {@link ServerTickEvent.Post} + {@link net.minecraft.world.entity.ai.attributes.Attribute}.
 *
 * <p>Конфигурация:
 * <ul>
 *   <li>{@code enabled=true} — вкл/выкл</li>
 *   <li>{@code attackDamage=0.1} — базовая атака</li>
 *   <li>{@code sneakSpeed=1.0} — скорость крадучись</li>
 *   <li>{@code attackSpeed=3.5} — скорость атаки</li>
 *   <li>{@code intervalTicks=200} — период тика</li>
 * </ul>
 */
public class AttributesFeature extends AbstractFeature {

    private int tickCounter = 0;

    // TODO(config): перенести в ConfigManager
    private boolean enabled = true;
    private double attackDamage = 0.1;
    private double sneakSpeed = 1.0;
    private double attackSpeed = 3.5;
    private int intervalTicks = 200;

    @Override
    public String getName() {
        return "attributes";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (!enabled) return;
        tickCounter++;
        if (tickCounter % intervalTicks != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.level().isClientSide()) continue;

            var attackDmgAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDmgAttr != null) {
                attackDmgAttr.setBaseValue(attackDamage);
            }

            var sneakSpeedAttr = player.getAttribute(Attributes.SNEAKING_SPEED);
            if (sneakSpeedAttr != null) {
                sneakSpeedAttr.setBaseValue(sneakSpeed);
            }

            var attackSpeedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeedAttr != null) {
                attackSpeedAttr.setBaseValue(attackSpeed);
            }
        }
    }
}
