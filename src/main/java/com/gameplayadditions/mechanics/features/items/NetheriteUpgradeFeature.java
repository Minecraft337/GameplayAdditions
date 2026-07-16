package com.gameplayadditions.mechanics.features.items;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * NetheriteUpgradeFeature — порт {@code NetheriteUpgradeListener} из MC-Plugin.
 * <p>
 * Позволяет улучшать нетеритовые предметы в наковальне с помощью
 * Netherite Scrap, добавляя атрибуты и прочность.
 */
public class NetheriteUpgradeFeature extends AbstractFeature {

    private static final String UPGRADE_TAG = "NetheriteUpgrade";

    public NetheriteUpgradeFeature() {
    }

    @Override
    public String getName() {
        return "NetheriteUpgrade";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[NetheriteUpgrade] Feature initialized.");
    }

    /**
     * Получить количество апгрейдов на предмете.
     */
    private int getUpgradeCount(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getInt(UPGRADE_TAG);
    }

    /**
     * Установить количество апгрейдов.
     */
    private void setUpgradeCount(ItemStack stack, int count) {
        CustomData current = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = current.copyTag();
        tag.putInt(UPGRADE_TAG, count);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Проверить, является ли предмет нетеритовым.
     */
    private boolean isNetheriteItem(ItemStack stack) {
        return stack.is(Items.NETHERITE_SWORD) ||
                stack.is(Items.NETHERITE_PICKAXE) ||
                stack.is(Items.NETHERITE_AXE) ||
                stack.is(Items.NETHERITE_SHOVEL) ||
                stack.is(Items.NETHERITE_HOE) ||
                stack.is(Items.NETHERITE_HELMET) ||
                stack.is(Items.NETHERITE_CHESTPLATE) ||
                stack.is(Items.NETHERITE_LEGGINGS) ||
                stack.is(Items.NETHERITE_BOOTS);
    }

    /**
     * Создать модификаторы атрибутов для апгрейда.
     */
    private ItemAttributeModifiers buildUpgradeModifiers(int totalUpgrades, ItemStack itemStack) {
        var builder = ItemAttributeModifiers.builder();

        double attackBonus = totalUpgrades * 0.1;
        double armorBonus = totalUpgrades * 0.1;
        double toughnessBonus = totalUpgrades * 0.05;
        double knockbackBonus = totalUpgrades * 0.1;

        // Бонус к атаке (для оружия и инструментов)
        if (attackBonus > 0) {
            builder.add(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                            ResourceLocation.parse("gameplayadditions:netherite_upgrade_attack"),
                            attackBonus,
                            AttributeModifier.Operation.ADD_VALUE
                    ),
                    EquipmentSlotGroup.MAINHAND
            );
        }

        // Бонус к броне — определяется типом предмета
        if (armorBonus > 0) {
            var item = itemStack.getItem();
            EquipmentSlotGroup slotGroup = (item instanceof ArmorItem armorItem)
                    ? EquipmentSlotGroup.bySlot(armorItem.getEquipmentSlot())
                    : EquipmentSlotGroup.CHEST;

            builder.add(
                    Attributes.ARMOR,
                    new AttributeModifier(
                            ResourceLocation.parse("gameplayadditions:netherite_upgrade_armor"),
                            armorBonus,
                            AttributeModifier.Operation.ADD_VALUE
                    ),
                    slotGroup
            );
            builder.add(
                    Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(
                            ResourceLocation.parse("gameplayadditions:netherite_upgrade_toughness"),
                            toughnessBonus,
                            AttributeModifier.Operation.ADD_VALUE
                    ),
                    slotGroup
            );
            builder.add(
                    Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(
                            ResourceLocation.parse("gameplayadditions:netherite_upgrade_knockback"),
                            knockbackBonus,
                            AttributeModifier.Operation.ADD_VALUE
                    ),
                    slotGroup
            );
        }

        return builder.build();
    }

    /**
     * Улучшение нетеритового предмета в наковальне.
     */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // Левая = нетеритовый предмет, правая = Netherite Scrap
        if (!isNetheriteItem(left) || !right.is(Items.NETHERITE_SCRAP)) return;

        ItemStack output = left.copy();
        int currentUpgrades = getUpgradeCount(output);
        int scrapCount = right.getCount();

        // Ставим лимит в 100 апгрейдов
        int newUpgrades = Math.min(100, currentUpgrades + scrapCount);
        int actualScrapUsed = newUpgrades - currentUpgrades;
        if (actualScrapUsed <= 0) return;

        setUpgradeCount(output, newUpgrades);

        // Применяем модификаторы атрибутов (перезаписываем полностью)
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, buildUpgradeModifiers(newUpgrades, output));

        // Увеличиваем прочность
        int maxDamage = output.getMaxDamage();
        if (maxDamage > 0) {
            output.set(DataComponents.MAX_DAMAGE, maxDamage + actualScrapUsed);
        }

        // Обновляем имя — добавляем суффикс апгрейда
        output.set(DataComponents.CUSTOM_NAME,
                Component.literal("§r§5§l+" + newUpgrades + " ")
                        .append(left.getHoverName().copy()));

        event.setOutput(output);
        event.setCost(0); // Бесплатно по опыту
        event.setMaterialCost(actualScrapUsed);

        ConsoleLogger.debug("[NetheriteUpgrade] Upgraded " + output.getHoverName().getString() + " to +" + newUpgrades);
    }
}
