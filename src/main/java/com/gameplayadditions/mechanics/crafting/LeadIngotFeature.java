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
 * LeadIngotFeature — порт {@code LeadIngotCraftListener} из MC-Plugin.
 * <p>
 * Создаёт кастомный Lead Ingot (переименованный Netherite Ingot) и защищает
 * его от расплавки/использования в других рецептах.
 */
public class LeadIngotFeature extends AbstractFeature {

    private static final String LEAD_INGOT_TAG = "IsLeadIngot";

    public LeadIngotFeature() {
    }

    @Override
    public String getName() {
        return "LeadIngot";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[LeadIngot] Feature initialized.");
    }

    /**
     * Создать предмет Lead Ingot.
     */
    public ItemStack createLeadIngotStack() {
        ItemStack stack = new ItemStack(Items.NETHERITE_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§r§7Lead Ingot §r§8*"));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(LEAD_INGOT_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /**
     * Проверить, является ли предмет Lead Ingot.
     */
    public boolean isLeadIngot(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.NETHERITE_INGOT)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean(LEAD_INGOT_TAG);
    }

    /**
     * Защита от расплавки — при крафте проверяем, не используется ли Lead Ingot
     * в других рецептах.
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (event.getCrafting().isEmpty()) return;

        // Проверяем, не является ли результат Lead Ingot (защита от расплавки)
        if (isLeadIngot(event.getCrafting())) return;

        // Проверяем матрицу крафта на наличие Lead Ingot
        for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
            ItemStack slot = event.getInventory().getItem(i);
            if (isLeadIngot(slot)) {
                // Lead Ingot используется в чужом рецепте — отменяем
                player.sendSystemMessage(Component.literal("§cНельзя использовать Lead Ingot в этом рецепте!"));
                event.getCrafting().shrink(event.getCrafting().getCount());
                return;
            }
        }
    }
}
