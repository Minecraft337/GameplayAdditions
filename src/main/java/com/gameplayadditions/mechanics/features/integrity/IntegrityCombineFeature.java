package com.gameplayadditions.mechanics.features.integrity;

import com.gameplayadditions.core.AbstractFeature;

/**
 * 🛡 Integrity Combine Feature — заглушка для будущего расширения.
 * <p>
 * MVP реализует только базовую систему целостности (CoreFeature) и PIERCING flag.
 * Не реализовано (TODO для следующего пакета):
 * <ul>
 *   <li>Mending XP listener — в NeoForge 21.1.143 нет стабильного inner-класса
 *       {@code PlayerXpEvent.XpChange}/{@code PickupXp}, проверенного на этой версии.
 *       Безопасный путь — polling {@code player.experienceProgress}/{@code totalExperience}
 *       в {@link IntegrityCoreFeature} каждый тик и сравнение с прошлым значением.</li>
 *   <li>{@code PrepareAnvilEvent} honor — в NeoForge нет прямого хука на обновление
 *       результата наковальни. Требуется polling {@code player.containerMenu} каждые N
 *       тиков или пакетная прослойка.</li>
 *   <li>{@code PrepareItemCraftEvent} / Grindstone аналогично.</li>
 * </ul>
 */
public class IntegrityCombineFeature extends AbstractFeature {

    @Override
    public String getName() {
        return "integrity_combine";
    }

    @Override
    public void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        logInfo("setup — mendingXp/anvil/grindstone/craft: TODO (см. javadoc)");
    }
}
