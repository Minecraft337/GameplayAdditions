package com.gameplayadditions.mechanics.features.world;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.List;

/**
 * EntityLocatorFeature — порт {@code EntityLocatorManager} + {@code EntityLocatorCraftListener} из MC-Plugin.
 * <p>
 * Периодически сканирует ближайших существ рядом с игроком, держащим локатор,
 * и отправляет сообщение о расстоянии до ближайшей сущности.
 */
public class EntityLocatorFeature extends AbstractFeature {

    private static final String LOCATOR_TAG = "IsEntityLocator";

    private int scanRadius = 12;
    private int intervalTicks = 20;
    private int tickCounter = 0;

    public EntityLocatorFeature() {
    }

    @Override
    public String getName() {
        return "EntityLocator";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[EntityLocator] Feature initialized. scanRadius=" + scanRadius + ", interval=" + intervalTicks + "t");
    }

    /**
     * Создать предмет-локатор сущностей.
     */
    public ItemStack createLocatorItem() {
        ItemStack stack = new ItemStack(Items.RECOVERY_COMPASS);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("§r§dЛокатор сущностей §r§7*"));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(LOCATOR_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /**
     * Проверить, является ли предмет локатором.
     */
    private boolean isLocatorItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.RECOVERY_COMPASS)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean(LOCATOR_TAG);
    }

    /**
     * Найти ближайшую сущность (не игрок, не предмет) в радиусе.
     */
    private Entity findNearestEntity(ServerPlayer player, ServerLevel level, int radius) {
        BlockPos playerPos = player.blockPosition();
        AABB searchBox = (new AABB(playerPos)).inflate(radius);

        List<Entity> entities = level.getEntities(player, searchBox, entity ->
                !(entity instanceof Player) && !(entity instanceof ItemEntity) && entity.isAlive()
        );

        return entities.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }

    /**
     * Периодическое сканирование.
     */
    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < intervalTicks) return;
        tickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.level().isClientSide()) continue;

            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();

            if (!isLocatorItem(mainHand) && !isLocatorItem(offHand)) continue;

            if (!(player.level() instanceof ServerLevel level)) continue;

            Entity nearest = findNearestEntity(player, level, scanRadius);
            sendProximityMessage(player, nearest, scanRadius);
        }
    }

    /**
     * Отправить сообщение о близости сущности.
     */
    private void sendProximityMessage(ServerPlayer player, Entity entity, int radius) {
        if (entity == null) {
            player.sendSystemMessage(Component.literal("§7[Локатор] §fСущности не обнаружены в радиусе §e" + radius + " §fблоков."));
            return;
        }

        double distance = entity.distanceTo(player);
        String distanceCategory;

        if (distance <= radius * 0.25) {
            distanceCategory = "§cОчень близко";
        } else if (distance <= radius * 0.5) {
            distanceCategory = "§eБлизко";
        } else if (distance <= radius * 0.75) {
            distanceCategory = "§aНа среднем расстоянии";
        } else {
            distanceCategory = "§7Далеко";
        }

        player.sendSystemMessage(Component.literal(
                "§7[Локатор] §fОбнаружена сущность: §e" + entity.getDisplayName().getString() +
                        " §f| " + distanceCategory + " §f(" + String.format("%.1f", distance) + " м)"
        ));
    }
}
