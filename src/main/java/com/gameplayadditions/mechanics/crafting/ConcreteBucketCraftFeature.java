package com.gameplayadditions.mechanics.crafting;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * ConcreteBucketCraftFeature — порт {@code ConcreteBucketCraftListener} из MC-Plugin.
 * <p>
 * Регистрирует кастомное ведро бетона (Concrete Bucket) и защищает
 * его от расплавки в других рецептах.
 */
public class ConcreteBucketCraftFeature extends AbstractFeature {

    private static final String CONCRETE_BUCKET_TAG = "IsConcreteBucket";

    public ConcreteBucketCraftFeature() {
    }

    @Override
    public String getName() {
        return "ConcreteBucketCraft";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[ConcreteBucketCraft] Feature initialized.");
    }

    /**
     * Создать предмет Concrete Bucket.
     */
    public ItemStack createConcreteBucket() {
        ItemStack stack = new ItemStack(Items.WATER_BUCKET);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("§r§7Concrete Bucket"));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(CONCRETE_BUCKET_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /**
     * Проверить, является ли предмет Concrete Bucket.
     */
    public boolean isConcreteBucket(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.WATER_BUCKET)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean(CONCRETE_BUCKET_TAG);
    }

    /**
     * Защита от расплавки Concrete Bucket в других рецептах.
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getCrafting().isEmpty()) return;

        // Проверяем, не используется ли Concrete Bucket в матрице крафта
        for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
            ItemStack slot = event.getInventory().getItem(i);
            if (isConcreteBucket(slot)) {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                    player.sendSystemMessage(
                            Component.literal("§cНельзя использовать Concrete Bucket в этом рецепте!"));
                }
                event.getCrafting().shrink(event.getCrafting().getCount());
                return;
            }
        }
    }
}
