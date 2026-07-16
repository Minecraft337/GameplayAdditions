package com.gameplayadditions.combat.weapons.core;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

/**
 * WeaponResolver — определяет тип оружия из ItemStack.
 * Портирован из MC-Plugin для NeoForge.
 * Использует DataComponents вместо PDC.
 */
public class WeaponResolver {

    public static final String PLASMA_TAG = "isPlasma";
    public static final String SHOCKER_TAG = "isShocker";

    public static ProjectileType resolve(ItemStack item) {
        if (item == null || item.isEmpty()) return null;

        var data = item.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;

        if (data.contains(PLASMA_TAG)) {
            return ProjectileType.PLASMA;
        }
        if (data.contains(SHOCKER_TAG)) {
            return ProjectileType.SHOCKER;
        }

        return null;
    }

    public static boolean isPlasma(ItemStack item) {
        return resolve(item) == ProjectileType.PLASMA;
    }

    public static boolean isShocker(ItemStack item) {
        return resolve(item) == ProjectileType.SHOCKER;
    }
}
