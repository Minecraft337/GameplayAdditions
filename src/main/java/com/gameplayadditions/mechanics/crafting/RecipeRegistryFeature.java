package com.gameplayadditions.mechanics.crafting;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecipeRegistryFeature — порт {@code RecipeRegistry} из MC-Plugin.
 * <p>
 * Регистрирует ResourceLocation для кастомных рецептов и
 * автоматически разблокирует их для игроков при входе на сервер.
 */
public class RecipeRegistryFeature extends AbstractFeature {

    private static RecipeRegistryFeature instance;
    private final Set<ResourceLocation> customRecipes = new HashSet<>();

    public RecipeRegistryFeature() {
        instance = this;
    }

    @Override
    public String getName() {
        return "RecipeRegistry";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[RecipeRegistry] Initialized with " + customRecipes.size() + " custom recipes registered.");
    }

    public static RecipeRegistryFeature getInstance() {
        return instance;
    }

    /**
     * Зарегистрировать кастомный рецепт.
     */
    public void registerRecipe(ResourceLocation key) {
        customRecipes.add(key);
    }

    /**
     * Получить все зарегистрированные кастомные рецепты.
     */
    public Set<ResourceLocation> getCustomRecipes() {
        return Set.copyOf(customRecipes);
    }

    /**
     * Авторазблокировка всех кастомных рецептов для игрока при входе.
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (customRecipes.isEmpty()) return;

        var recipeManager = player.server.getRecipeManager();
        List<RecipeHolder<?>> recipes = recipeManager.getRecipes().stream()
                .filter(holder -> customRecipes.contains(holder.id()))
                .collect(java.util.stream.Collectors.toList());

        player.awardRecipes(recipes);

        ConsoleLogger.debug("[RecipeRegistry] Unlocked " + customRecipes.size() + " custom recipes for " + player.getScoreboardName());
    }
}
