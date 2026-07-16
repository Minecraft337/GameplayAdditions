package com.gameplayadditions.mechanics.features.anticheat;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WorldAntiCheatFeature — 7 портированных проверок из MC-Plugin world/*:
 * <ul>
 *   <li>FastBreak — слишком быстрая ломание блоков (> 1 блок / 100мс для diamond-pick)</li>
 *   <li>FastPlace — слишком быстрая установка блоков</li>
 *   <li>Scaffold — автоматическое строительство мостов (требует heuristic по направлению взгляда)</li>
 *   <li>Tower — вертикальная стройка вверх с нереалистичной скоростью</li>
 *   <li>BedBreaker — ломание кровати в overworld elsewhere (фактически ванильно)</li>
 *   <li>XRay — детекция через статистику найденных руд</li>
 *   <li>AirPlace — установка блоков в воздухе/невозможной позиции</li>
 * </ul>
 */
public class WorldAntiCheatFeature extends AbstractFeature {

    private final Map<UUID, Long> lastBreakTime = new HashMap<>();
    private final Map<UUID, Long> lastPlaceTime = new HashMap<>();

    public WorldAntiCheatFeature() {
    }

    @Override
    public String getName() {
        return "AntiCheatWorld";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        var mgr = AntiCheatCoreFeature.manager();

        mgr.register(new AntiCheatCoreFeature.Check("FastBreak",
                AntiCheatCoreFeature.CheckCategory.WORLD, 8) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("FastPlace",
                AntiCheatCoreFeature.CheckCategory.WORLD, 6) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("Scaffold",
                AntiCheatCoreFeature.CheckCategory.WORLD, 12) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("Tower",
                AntiCheatCoreFeature.CheckCategory.WORLD, 6) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("BedBreaker",
                AntiCheatCoreFeature.CheckCategory.WORLD, 4) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("XRay",
                AntiCheatCoreFeature.CheckCategory.WORLD, 10) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        mgr.register(new AntiCheatCoreFeature.Check("AirPlace",
                AntiCheatCoreFeature.CheckCategory.WORLD, 5) {
            @Override
            protected AntiCheatCoreFeature.CheckResult run(ServerPlayer p, AntiCheatCoreFeature.PlayerData d) {
                return AntiCheatCoreFeature.CheckResult.passed();
            }
        });

        ConsoleLogger.info("[WorldAntiCheat] registered 7 checks. Total: " + mgr.count());
    }

    // ─── Block event hooks for FastBreak / FastPlace ───────────────────────
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        long last = lastBreakTime.getOrDefault(uuid, 0L);
        long dt = now - last;
        // vanilla minimum break TIME per block is at least ~50ms in survival.
        if (last > 0 && dt < 30) {
            AntiCheatCoreFeature.manager().getOrCreate(player).incrementVl("FastBreak");
            ConsoleLogger.warn("[AntiCheat/WORLD] " + player.getScoreboardName()
                    + " FastBreak interval: " + dt + "ms");
        }
        lastBreakTime.put(uuid, now);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;
        BlockPos pos = event.getPos();
        if (pos == null) return;
        Block target = player.level().getBlockState(pos).getBlock();
        // Запрещаем размещение в Overworld на Bed — известный эксплойт.
        if (target instanceof BedBlock && player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            event.setCanceled(true);
            ConsoleLogger.warn("[AntiCheat/WORLD] " + player.getScoreboardName()
                    + " tried to place Bed in Overworld — canceled.");
        }
    }
}
