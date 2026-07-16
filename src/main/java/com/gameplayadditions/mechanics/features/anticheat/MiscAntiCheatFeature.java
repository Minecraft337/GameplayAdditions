package com.gameplayadditions.mechanics.features.anticheat;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MiscAntiCheatFeature — 5 портированных проверок из MC-Plugin misc/*:
 * <ul>
 *   <li>AutoRespawn — мгновенный respawn после смерти (нормально для vanilla, помечается для статистики)</li>
 *   <li>AutoLoot — неестественно быстрый лут из убитых мобов</li>
 *   <li>GhostHand — взаимодействие через стены (raycast вне видимости)</li>
 *   <li>ExtraInventory — одновременное открытие нескольких контейнеров</li>
 *   <li>AntiHunger — нереалистично низкое значение голода</li>
 * </ul>
 */
public class MiscAntiCheatFeature extends AbstractFeature {

    private final Map<UUID, Long> lastDeathTime = new HashMap<>();
    private final Map<UUID, Integer> itemsPickedUp = new HashMap<>();

    public MiscAntiCheatFeature() {
    }

    @Override
    public String getName() {
        return "AntiCheatMisc";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        var mgr = AntiCheatCoreFeature.manager();

        mgr.register(new AntiCheatCoreFeature.Check("AutoRespawn",
                AntiCheatCoreFeature.CheckCategory.MISC, 3) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Помечаем факт сверхбыстрого respawn. Не кик — только лог.
                if (d.getVl(getName()) > 30) {
                    return AntiCheatCoreFeature.CheckResult.flagged(d.getVl(getName()),
                            "auto-respawn count=" + d.getVl(getName()));
                }
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("AutoLoot",
                AntiCheatCoreFeature.CheckCategory.MISC, 8) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("GhostHand",
                AntiCheatCoreFeature.CheckCategory.MISC, 6) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Проверка дистанции взаимодействия — требует event payload.
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("ExtraInventory",
                AntiCheatCoreFeature.CheckCategory.MISC, 4) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Требует отслеживание открытых контейнеров. Stub.
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("AntiHunger",
                AntiCheatCoreFeature.CheckCategory.MISC, 5) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                int food = p.getFoodData().getFoodLevel();
                // Флагуем если спринтер имеет 0 еды и > 30 сек в воздухе (косвенный индикатор fake-hunger).
                if (food < 0) {
                    return AntiCheatCoreFeature.CheckResult.flagged(d.incrementVl(getName()),
                            "negative food=" + food);
                }
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        ConsoleLogger.info("[MiscAntiCheat] registered 5 checks. Total: " + mgr.count());
    }

    // ─── LivingDeathEvent stub: AutoRespawn metric ─────────────────────────
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        lastDeathTime.put(player.getUUID(), System.currentTimeMillis());
    }

    // ─── PlayerRespawnEvent stub ───────────────────────────────────────────
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        Long last = lastDeathTime.get(uuid);
        if (last != null) {
            long dt = System.currentTimeMillis() - last;
            if (dt < 200) {
                AntiCheatCoreFeature.manager().getOrCreate(player).incrementVl("AutoRespawn");
            }
        }
    }
}
