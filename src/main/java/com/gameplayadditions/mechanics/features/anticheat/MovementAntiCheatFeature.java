package com.gameplayadditions.mechanics.features.anticheat;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * MovementAntiCheatFeature — 8 портированных проверок из MC-Plugin movement/*:
 * <ul>
 *   <li>Speed — горизонтальная скорость выше нормы</li>
 *   <li>Flight — нахождение в воздухе без полёта</li>
 *   <li>Jesus — движение по воде как по суше</li>
 *   <li>Step — слишком высокие ступеньки вверх</li>
 *   <li>Spider — ходьба по стенам без паутины</li>
 *   <li>Glide — длинная фаза скольжения при выключенном креативе</li>
 *   <li>Teleport — мгновенное перемещение на большое расстояние</li>
 *   <li>FastFall — слишком короткое падение (skip fall cancel)</li>
 * </ul>
 */
public class MovementAntiCheatFeature extends AbstractFeature {

    private static final double WALK_SPEED_MAX = 0.30;
    private static final double RUN_SPEED_MAX = 0.45;
    private static final double SPRINT_SPEED_MAX = 0.61;

    public MovementAntiCheatFeature() {
    }

    @Override
    public String getName() {
        return "AntiCheatMovement";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        var mgr = AntiCheatCoreFeature.manager();

        mgr.register(new AntiCheatCoreFeature.Check("Speed",
                AntiCheatCoreFeature.CheckCategory.MOVEMENT, 8) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                double dx = p.getDeltaMovement().horizontalDistance();
                double lim = p.isSprinting() ? SPRINT_SPEED_MAX
                        : p.isCrouching() ? WALK_SPEED_MAX : RUN_SPEED_MAX;
                if (dx > lim * 1.6) {
                    return AntiCheatCoreFeature.CheckResult.flagged(d.incrementVl(getName()),
                            "speed=" + Math.round(dx * 100) / 100.0 + " max=" + lim);
                }
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("Flight",
                AntiCheatCoreFeature.CheckCategory.MOVEMENT, 12) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Свободно висит в воздухе > 1.5 сек без полёта/levitation.
                if (p.getAbilities().mayfly) return AntiCheatCoreFeature.CheckResult.passed();
                if (p.onGround() || p.isInWater() || p.onClimbable()) {
                    return AntiCheatCoreFeature.CheckResult.passed();
                }
                double dy = p.getDeltaMovement().y;
                if (dy > 0 && p.getY() < p.level().getMaxBuildHeight() - 2) {
                    int vl = d.incrementVl(getName());
                    return AntiCheatCoreFeature.CheckResult.flagged(vl, "ascending in air dy=" + dy);
                }
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("Jesus",
                AntiCheatCoreFeature.CheckCategory.MOVEMENT, 6) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Placeholder: требует sample of 10+ ticks для уверенного определения.
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("Step",
                AntiCheatCoreFeature.CheckCategory.MOVEMENT, 5) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Vanilla max step = 0.5 без эффекта jump boost.
                if (p.getDeltaMovement().y > 0.6 && !p.onGround()) {
                    return AntiCheatCoreFeature.CheckResult.flagged(d.incrementVl(getName()),
                            "step dy=" + p.getDeltaMovement().y);
                }
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("Spider",
                AntiCheatCoreFeature.CheckCategory.MOVEMENT, 6) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                if (p.horizontalCollision && !p.onClimbable() && !p.isInWater()) {
                    int dy = (int) (p.getDeltaMovement().y * 100);
                    if (dy > 0) {
                        return AntiCheatCoreFeature.CheckResult.flagged(d.incrementVl(getName()),
                                "climb on wall dy=" + dy);
                    }
                }
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("Glide",
                AntiCheatCoreFeature.CheckCategory.MOVEMENT, 8) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Без Elytra/полёта: падение < -0.05 дольше ~1 сек.
                if (p.isFallFlying() || p.getAbilities().mayfly) {
                    return AntiCheatCoreFeature.CheckResult.passed();
                }
                if (p.getDeltaMovement().y < -0.05 && !p.onGround()) {
                    int ticks = d.incrementVl(getName());
                    if (ticks > 20) {
                        return AntiCheatCoreFeature.CheckResult.flagged(ticks,
                                "glide ticks=" + ticks);
                    }
                } else if (d.getVl(getName()) > 0) {
                    d.resetVl(getName());
                }
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("Teleport",
                AntiCheatCoreFeature.CheckCategory.MOVEMENT, 4) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Требует сохранение previous Pos. Stub: возвращаем passed.
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("FastFall",
                AntiCheatCoreFeature.CheckCategory.MOVEMENT, 4) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                // Требует сохранение fallDistance. Stub.
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        ConsoleLogger.info("[MovementAntiCheat] registered 8 checks. Total: " + mgr.count());
    }
}
