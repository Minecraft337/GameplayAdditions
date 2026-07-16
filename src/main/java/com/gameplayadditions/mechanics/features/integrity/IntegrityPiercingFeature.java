package com.gameplayadditions.mechanics.features.integrity;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 🎯 Piercing Feature — обработчик зачарования PIERCING (Пробитие).
 * <p>
 * При ударе игрока оружием с зачарованием {@code minecraft:piercing}
 * устанавливает флаг {@link IntegritySystem#setPiercingActive(boolean)} = true,
 * чтобы {@link IntegritySystem#decreaseIntegrity(ItemStack, double, Player)} добавил
 * {@code +piercingExtraCost%} к трате целостности брони цели. После атаки флаг
 * сбрасывается в tick (см. {@link IntegrityCoreFeature}).
 */
public class IntegrityPiercingFeature extends AbstractFeature {

    @Override
    public String getName() {
        return "integrity_piercing";
    }

    @Override
    public void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        logInfo("setup — piercing " + (IntegrityConfig.piercingEnabled ? "ENABLED" : "DISABLED"));
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        // 🚫 LivingDamageEvent.Pre срабатывает на ОБЕИХ сторонах (клиент + сервер).
        //    Модифицировать IntegritySystem.setPiercingActive на клиенте — корень
        //    рассинхрона: это серверная сущность (предмет + состояние), живёт только
        //    на логическом сервере. Гарантируем server-only.
        if (event.getEntity().level().isClientSide()) return;

        if (!IntegrityConfig.piercingEnabled) {
            IntegritySystem.setPiercingActive(false);
            return;
        }
        // Нас интересует только удар ПО ИГРОКУ (броня)
        if (!(event.getEntity() instanceof Player victim)) {
            IntegritySystem.setPiercingActive(false);
            return;
        }
        var source = event.getSource();
        var attacker = source.getEntity();
        if (!(attacker instanceof Player attackerP)) {
            IntegritySystem.setPiercingActive(false);
            return;
        }
        ItemStack weapon = attackerP.getMainHandItem();
        if (weapon.isEmpty()) {
            IntegritySystem.setPiercingActive(false);
            return;
        }
        int piercingLevel = IntegritySystem.getPiercingLevelForItem(weapon);
        IntegritySystem.setPiercingActive(piercingLevel > 0);
    }
}
