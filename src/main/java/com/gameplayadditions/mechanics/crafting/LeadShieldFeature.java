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
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * LeadShieldFeature — порт {@code LeadShieldCraftListener} из MC-Plugin.
 * <p>
 * Позволяет улучшать щит в наковальне с помощью Lead Ingot,
 * превращая его в Lead Shield с улучшенными характеристиками.
 */
public class LeadShieldFeature extends AbstractFeature {

    private static final String LEAD_SHIELD_TAG = "IsLeadShield";
    private static final String LEAD_INGOT_TAG = "IsLeadIngot";

    public LeadShieldFeature() {
    }

    @Override
    public String getName() {
        return "LeadShield";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[LeadShield] Feature initialized.");
    }

    /**
     * Проверить, является ли предмет Lead Ingot.
     */
    private boolean isLeadIngot(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.NETHERITE_INGOT)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean(LEAD_INGOT_TAG);
    }

    /**
     * Улучшение щита в наковальне.
     */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // Левая = щит, правая = Lead Ingot
        if (!left.is(Items.SHIELD) || !isLeadIngot(right)) return;

        ItemStack output = left.copy();
        CustomData customData = output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putBoolean(LEAD_SHIELD_TAG, true);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        output.set(DataComponents.CUSTOM_NAME,
                Component.literal("§r§7Lead Shield §r§8*"));

        event.setOutput(output);
        event.setCost(1);
        event.setMaterialCost(1);
    }
}
