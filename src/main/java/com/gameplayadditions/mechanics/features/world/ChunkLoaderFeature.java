package com.gameplayadditions.mechanics.features.world;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * ChunkLoaderFeature — порт {@code ChunkLoaderItemListener} из MC-Plugin.
 * <p>
 * Позволяет игрокам размещать специальные изумрудные блоки как загрузчики чанков.
 * Загруженный чанк остаётся активным, пока блок не будет сломан.
 */
public class ChunkLoaderFeature extends AbstractFeature {

    private static final ResourceLocation CHUNK_LOADER_KEY = ResourceLocation.parse("gameplayadditions:is_chunk_loader");
    private final Set<BlockPos> activeLoaders = new HashSet<>();

    public ChunkLoaderFeature() {
        super();
    }

    @Override
    public String getName() {
        return "ChunkLoader";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[ChunkLoader] Feature initialized.");
    }

    /**
     * Проверить, является ли предмет загрузчиком чанков.
     */
    private boolean isChunkLoaderItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.EMERALD_BLOCK)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean("IsChunkLoader");
    }

    /**
     * Создать предмет-загрузчик чанков.
     */
    public ItemStack createChunkLoaderItem() {
        ItemStack stack = new ItemStack(Items.EMERALD_BLOCK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§r§6Загрузчик чанков"));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("IsChunkLoader", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /**
     * Обработка установки загрузчика чанков.
     */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (!isChunkLoaderItem(player.getMainHandItem()) &&
                !isChunkLoaderItem(player.getOffhandItem())) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK)) return;

        if (activeLoaders.contains(pos)) return;

        activeLoaders.add(pos);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        ConsoleLogger.debug("[ChunkLoader] Activated at " + pos.toShortString());
    }

    /**
     * Обработка разрушения загрузчика чанков.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getPlayer() == null) return;

        BlockPos pos = event.getPos();
        if (!activeLoaders.remove(pos)) return;

        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);

        // Выпадение предмета
        event.getPlayer().spawnAtLocation(createChunkLoaderItem(), 0.5F);

        ConsoleLogger.debug("[ChunkLoader] Deactivated at " + pos.toShortString());
    }

    /**
     * Получить все активные загрузчики чанков (read-only).
     */
    public Set<BlockPos> getActiveLoaders() {
        return Set.copyOf(activeLoaders);
    }
}
