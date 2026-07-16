package com.gameplayadditions.mechanics.features.items;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * ChestplateFlightFeature — порт {@code ChestplateFlightListener} из MC-Plugin.
 * <p>
 * Позволяет улучшать нагрудники с помощью фантомных мембран в наковальне,
 * добавляя возможность полёта (до 100%).
 */
public class ChestplateFlightFeature extends AbstractFeature {

    private static final String FLIGHT_TAG = "ChestplateFlight";

    public ChestplateFlightFeature() {
    }

    @Override
    public String getName() {
        return "ChestplateFlight";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[ChestplateFlight] Feature initialized.");
    }

    /**
     * Получить текущий процент полёта на нагруднике.
     */
    private int getFlightPercent(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getInt(FLIGHT_TAG);
    }

    /**
     * Установить процент полёта на нагруднике.
     */
    private void setFlightPercent(ItemStack stack, int percent) {
        CustomData current = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        var tag = current.copyTag();
        tag.putInt(FLIGHT_TAG, Math.min(100, Math.max(0, percent)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Обновить имя и описание предмета при достижении 100%.
     */
    private void updateFlightLore(ItemStack stack, int percent) {
        if (percent < 100) return;
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("§r§6§lНагрудник полёта"));
    }

    /**
     * Цветовой градиент для процента полёта.
     */
    private ChatFormatting flightGradientColor(int percent) {
        if (percent >= 85) return ChatFormatting.DARK_RED;
        if (percent >= 65) return ChatFormatting.RED;
        if (percent >= 45) return ChatFormatting.GOLD;
        if (percent >= 25) return ChatFormatting.YELLOW;
        if (percent >= 10) return ChatFormatting.GREEN;
        return ChatFormatting.DARK_GREEN;
    }

    /**
     * Обработка события наковальни — улучшение нагрудника мембранами.
     */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // Левая = нагрудник, правая = мембрана
        if (!isValidChestplate(left) || !right.is(Items.PHANTOM_MEMBRANE)) return;

        ItemStack output = left.copy();
        int currentFlight = getFlightPercent(output);
        if (currentFlight >= 100) return;

        // Каждая мембрана даёт +1%
        int membranes = right.getCount();
        int newFlight = Math.min(100, currentFlight + membranes);

        setFlightPercent(output, newFlight);
        updateFlightLore(output, newFlight);

        // Стоимость
        event.setOutput(output);
        event.setCost(membranes);
        event.setMaterialCost(membranes);

        ConsoleLogger.debug("[ChestplateFlight] Upgraded to " + newFlight + "%");
    }

    /**
     * Проверить, является ли предмет валидным нагрудником для улучшения.
     */
    private boolean isValidChestplate(ItemStack stack) {
        return stack.is(Items.LEATHER_CHESTPLATE) ||
                stack.is(Items.CHAINMAIL_CHESTPLATE) ||
                stack.is(Items.IRON_CHESTPLATE) ||
                stack.is(Items.GOLDEN_CHESTPLATE) ||
                stack.is(Items.DIAMOND_CHESTPLATE) ||
                stack.is(Items.NETHERITE_CHESTPLATE);
    }
}
