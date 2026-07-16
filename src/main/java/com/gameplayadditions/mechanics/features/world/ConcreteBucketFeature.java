package com.gameplayadditions.mechanics.features.world;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * ConcreteBucketFeature — порт {@code ConcreteBucketManager} из MC-Plugin.
 * <p>
 * При использовании Concrete Bucket вода превращается в серый бетон через 60 секунд.
 */
public class ConcreteBucketFeature extends AbstractFeature {

    private static final String CONCRETE_BUCKET_TAG = "IsConcreteBucket";
    private static final int CONCRETE_DELAY_TICKS = 1200; // 60 секунд (20 tick/sec)

    // Храним уровень вместе с позицией
    private final Map<BlockPos, ConcreteWater> trackedWater = new HashMap<>();

    private record ConcreteWater(ServerLevel level, long placedTick, UUID groupId) {}

    public ConcreteBucketFeature() {
    }

    @Override
    public String getName() {
        return "ConcreteBucket";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
        ConsoleLogger.info("[ConcreteBucket] Feature initialized. Delay: 60s");
    }

    private boolean isConcreteBucket(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.WATER_BUCKET)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean(CONCRETE_BUCKET_TAG);
    }

    /**
     * Использование Concrete Bucket — размещение воды.
     * Используем RightClickBlock с проверкой, что игрок целится в блок,
     * на который можно поставить воду.
     */
    @SubscribeEvent
    public void onBucketUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ItemStack stack = event.getItemStack();
        if (!isConcreteBucket(stack)) return;

        // Проверяем, что игрок не шифтует (иначе открывается сундук и т.д.)
        // и что кликнутый блок можно заменить водой
        BlockPos clickedPos = event.getPos();
        BlockPos placePos = clickedPos.relative(event.getHitVec().getDirection());

        if (!level.getBlockState(placePos).canBeReplaced()) return;

        UUID groupId = UUID.randomUUID();
        long currentTick = level.getGameTime();

        // Размещаем воду
        level.setBlockAndUpdate(placePos, Blocks.WATER.defaultBlockState());

        // Отмечаем для отслеживания
        trackedWater.put(placePos, new ConcreteWater(level, currentTick, groupId));

        // Заменяем предмет в руке на пустое ведро
        if (event.getEntity() instanceof Player player) {
            ItemStack handStack = player.getItemInHand(event.getHand());
            if (handStack.getCount() > 1) {
                handStack.shrink(1);
                player.addItem(new ItemStack(Items.BUCKET));
            } else {
                player.setItemInHand(event.getHand(), new ItemStack(Items.BUCKET));
            }
        }

        ConsoleLogger.debug("[ConcreteBucket] Water placed at " + placePos.toShortString());
    }

    /**
     * Периодическая проверка — превращение воды в бетон.
     */
    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (trackedWater.isEmpty()) return;

        Iterator<Map.Entry<BlockPos, ConcreteWater>> iterator = trackedWater.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ConcreteWater> entry = iterator.next();
            BlockPos pos = entry.getKey();
            ConcreteWater data = entry.getValue();

            long elapsed = data.level().getGameTime() - data.placedTick();
            if (elapsed >= CONCRETE_DELAY_TICKS) {
                // Превращаем в бетон, если вода ещё на месте
                if (data.level().isLoaded(pos) &&
                        data.level().getBlockState(pos).getFluidState().is(Fluids.WATER)) {
                    data.level().setBlockAndUpdate(pos, Blocks.GRAY_CONCRETE.defaultBlockState());
                    ConsoleLogger.debug("[ConcreteBucket] Converted to concrete at " + pos.toShortString());
                }
                iterator.remove();
            }
        }
    }

    /**
     * Очистка при разрушении/замене блока.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        trackedWater.remove(pos);
    }

    /**
     * Восстановление при изменении блока (вода ушла).
     */
    @SubscribeEvent
    public void onBlockNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        ConcreteWater data = trackedWater.get(pos);
        if (data == null) return;

        // Если вода пропала до таймера — убираем из отслеживания
        BlockState state = event.getLevel().getBlockState(pos);
        if (!state.getFluidState().is(Fluids.WATER)) {
            trackedWater.remove(pos);
        }
    }
}
