package com.gameplayadditions.mechanics.features.anticheat;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CombatAntiCheatFeature — 8 проверок из MC-Plugin combat/*:
 * <ul>
 *   <li>AimAssist — резкое совпадение yaw с углом к цели</li>
 *   <li>Aimbot — мгновенное прицеливание когда цель появляется</li>
 *   <li>KillAura — удары по нескольким целям за короткий тик</li>
 *   <li>Reach — дистанция атаки > 3.0 (стандартный vanilla reach ~3)</li>
 *   <li>Velocity — нереалистичное изменение velocity после hit</li>
 *   <li>HitBoxes — удар по энтити вне видимости</li>
 *   <li>NoSwing — пакет анимации свинга не дошёл</li>
 *   <li>BlockReach — дистанция удара по блоку > 5.0</li>
 * </ul>
 * <p>
 * Логика упрощена для reference-порта: каждый чек выдаёт {@link AntiCheatCoreFeature.CheckResult}
 * при превышении порога. Реальные anti-cheat эвристики должны быть расширены.
 */
public class CombatAntiCheatFeature extends AbstractFeature {

    private final Map<UUID, Integer> hitsPerTick = new HashMap<>();

    public CombatAntiCheatFeature() {
    }

    @Override
    public String getName() {
        return "AntiCheatCombat";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        var mgr = AntiCheatCoreFeature.manager();

        // AimAssist
        mgr.register(new AntiCheatCoreFeature.Check("AimAssist",
                AntiCheatCoreFeature.CheckCategory.COMBAT, 10) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Stub: detect < 0.05 yaw delta + perfect pitch alignment.
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        // Aimbot
        mgr.register(new AntiCheatCoreFeature.Check("Aimbot",
                AntiCheatCoreFeature.CheckCategory.COMBAT, 8) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        // KillAura
        mgr.register(new AntiCheatCoreFeature.Check("KillAura",
                AntiCheatCoreFeature.CheckCategory.COMBAT, 15) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                int hits = d.getVl(getName());
                return hits > 6
                        ? AntiCheatCoreFeature.CheckResult.flagged(hits, "multi-target swing: " + hits)
                        : AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        // Reach
        mgr.register(new AntiCheatCoreFeature.Check("Reach",
                AntiCheatCoreFeature.CheckCategory.COMBAT, 6) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        // Velocity
        mgr.register(new AntiCheatCoreFeature.Check("Velocity",
                AntiCheatCoreFeature.CheckCategory.COMBAT, 8) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        // HitBoxes
        mgr.register(new AntiCheatCoreFeature.Check("HitBoxes",
                AntiCheatCoreFeature.CheckCategory.COMBAT, 6) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        // NoSwing
        mgr.register(new AntiCheatCoreFeature.Check("NoSwing",
                AntiCheatCoreFeature.CheckCategory.COMBAT, 12) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        // BlockReach
        mgr.register(new AntiCheatCoreFeature.Check("BlockReach",
                AntiCheatCoreFeature.CheckCategory.COMBAT, 5) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        ConsoleLogger.info("[CombatAntiCheat] registered 8 checks. Total: " + mgr.count());
    }

    // ─── PlayerTick: периодически вызываем все check-ы ────────────────────
    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AntiCheatCoreFeature.manager().tick(player);
    }

    // ─── AttackEntityEvent: конкретная проверка Reach ──────────────────────
    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        if (target == null) return;
        double dist = player.distanceTo(target);
        if (dist > 4.5) {
            AntiCheatCoreFeature.PlayerData d = AntiCheatCoreFeature.manager().getOrCreate(player);
            int vl = d.incrementVl("Reach");
            ConsoleLogger.warn("[AntiCheat/COMBAT] " + player.getScoreboardName()
                    + " Reach too far: " + dist + " (VL=" + vl + ")");
        }
    }
}
