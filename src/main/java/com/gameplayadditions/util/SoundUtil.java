package com.gameplayadditions.util;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * SoundUtil — утилита для поиска звуков по имени.
 * Портирован из MC-Plugin для NeoForge.
 */
public final class SoundUtil {

    private static final Map<String, SoundEvent> SOUND_CACHE = new HashMap<>();

    static {
        // Register common sounds
        register("block.note_block.pling", SoundEvents.NOTE_BLOCK_PLING.value());
        register("entity.lightning_bolt.thunder", SoundEvents.LIGHTNING_BOLT_THUNDER);
        register("entity.experience_orb.pickup", SoundEvents.EXPERIENCE_ORB_PICKUP);
        register("entity.player.levelup", SoundEvents.PLAYER_LEVELUP);
        register("block.beacon.activate", SoundEvents.BEACON_ACTIVATE);
        register("block.beacon.deactivate", SoundEvents.BEACON_DEACTIVATE);
        register("entity.generic.explode", SoundEvents.GENERIC_EXPLODE.value());
        register("block.fire.extinguish", SoundEvents.FIRE_EXTINGUISH);
    }

    private SoundUtil() {}

    private static void register(String name, SoundEvent sound) {
        SOUND_CACHE.put(name, sound);
        SOUND_CACHE.put(name.replace('.', '_').toUpperCase(), sound);
    }

    public static SoundEvent getSound(String name) {
        if (name == null || name.isEmpty()) return null;
        // Try direct lookup first
        SoundEvent cached = SOUND_CACHE.get(name);
        if (cached != null) return cached;
        // Try converting underscores to dots (old Bukkit format)
        String registryKey = name.toLowerCase().replace('_', '.');
        cached = SOUND_CACHE.get(registryKey);
        if (cached != null) return cached;
        // Try the uppercase version
        cached = SOUND_CACHE.get(name.toUpperCase());
        return cached;
    }

    public static SoundEvent getSound(String name, SoundEvent fallback) {
        SoundEvent sound = getSound(name);
        return sound != null ? sound : fallback;
    }
}
