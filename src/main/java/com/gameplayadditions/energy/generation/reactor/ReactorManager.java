package com.gameplayadditions.energy.generation.reactor;

import com.gameplayadditions.energy.transfer.cable.CableNetwork;
import com.gameplayadditions.energy.transfer.cable.CableNode;
import com.gameplayadditions.energy.transfer.cable.NodeType;
import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.MessageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.MinecraftServer;

import java.util.*;

/**
 * ReactorManager — оркестратор реактора тёмного синтеза.
 * <p>
 * Порт {@code com.mcplugin.energy.generation.reactor.ReactorManager} из MC-Plugin.
 * Управляет состоянием, симуляцией, тиками реактора.
 */
public class ReactorManager {

    // =========================
    // SINGLETON
    // =========================
    private static ReactorManager instance;
    private MinecraftServer server;

    private final ReactorDisplay display;

    public static ReactorManager getInstance() { return instance; }

    public static void init(MinecraftServer server) {
        if (instance != null) return;
        instance = new ReactorManager();
        instance.server = server;
        ReactorConfig.init();
        instance.copyConfig();
        loadAll();
        ConsoleLogger.info("[Reactor] Initialized.");
    }

    public static void shutdown() {
        if (instance != null) {
            saveAll();
            instance.setReactorLocation(null, null);
            instance = null;
        }
    }

    // =========================
    // CONFIG
    // =========================
    private ReactorConfig cfg;
    private boolean enabled;
    private int tempDecayRate, heatRate, coolRate, coreTempMax, coreTempMin, coreTempCoolMin;
    private int corePressReduceRate, caseTempHeatRate, caseTempMax, caseTempCoolRate, caseTempCoolMin;
    private int caseTempDecayRate, casePressHeatRate, casePressMax, casePressDecayRate;
    private int shIntDecayTempThreshold, shellIntDecayRate, shellIntRecoveryTempMax, shellIntRecoveryRate;
    private int caseIntDecayPressThreshold, caseIntDecayTempThreshold, caseIntDecayPressRate, caseIntDecayTempRate;
    private int caseIntRecoveryPressMax, caseIntRecoveryTempMax, caseIntRecoveryRate;
    private boolean wearEnabled;
    private int wearIntervalNormal, wearIntervalDegradation, wearChatCountdown, wearFinalMeltdownAt, wearFinalMeltdownDuration;
    private int meltdownExplosionRadius, recipeTimeMax;

    public int getRecipeTimeMax() { return recipeTimeMax; }

    private void copyConfig() {
        if (cfg == null) return;
        enabled = cfg.isEnabled();
        tempDecayRate = cfg.getTempDecayRate();
        heatRate = cfg.getHeatRate();
        coolRate = cfg.getCoolRate();
        coreTempMax = cfg.getCoreTempMax();
        coreTempMin = cfg.getCoreTempMin();
        coreTempCoolMin = cfg.getCoreTempCoolMin();
        corePressReduceRate = cfg.getCorePressReduceRate();
        caseTempHeatRate = cfg.getCaseTempHeatRate();
        caseTempMax = cfg.getCaseTempMax();
        caseTempCoolRate = cfg.getCaseTempCoolRate();
        caseTempCoolMin = cfg.getCaseTempCoolMin();
        caseTempDecayRate = cfg.getCaseTempDecayRate();
        casePressHeatRate = cfg.getCasePressHeatRate();
        casePressMax = cfg.getCasePressMax();
        casePressDecayRate = cfg.getCasePressDecayRate();
        shIntDecayTempThreshold = cfg.getShIntDecayTempThreshold();
        shellIntDecayRate = cfg.getShellIntDecayRate();
        shellIntRecoveryTempMax = cfg.getShellIntRecoveryTempMax();
        shellIntRecoveryRate = cfg.getShellIntRecoveryRate();
        caseIntDecayPressThreshold = cfg.getCaseIntDecayPressThreshold();
        caseIntDecayTempThreshold = cfg.getCaseIntDecayTempThreshold();
        caseIntDecayPressRate = cfg.getCaseIntDecayPressRate();
        caseIntDecayTempRate = cfg.getCaseIntDecayTempRate();
        caseIntRecoveryPressMax = cfg.getCaseIntRecoveryPressMax();
        caseIntRecoveryTempMax = cfg.getCaseIntRecoveryTempMax();
        caseIntRecoveryRate = cfg.getCaseIntRecoveryRate();
        wearEnabled = cfg.isWearEnabled();
        wearIntervalNormal = cfg.getWearIntervalNormal();
        wearIntervalDegradation = cfg.getWearIntervalDegradation();
        wearChatCountdown = cfg.getWearChatCountdown();
        wearFinalMeltdownAt = cfg.getWearFinalMeltdownAt();
        wearFinalMeltdownDuration = cfg.getWearFinalMeltdownDuration();
        meltdownExplosionRadius = cfg.getMeltdownExplosionRadius();
        recipeTimeMax = cfg.getRecipeTimeMax();
    }

    // =========================
    // STATE
    // =========================
    private BlockPos reactorPos;
    private Level reactorLevel;
    private boolean valid;
    private String reactorId;

    // Core parameters
    private int coreTemp, corePress, coreShInt = 100;
    private int coreCaseTemp, coreCasePress, coreCaseInt = 100;

    // Recipe
    private int recipeTime;
    private boolean rcDone;

    // Self-destruct
    private boolean selfDestruct;

    // Energy
    private long energyGenerated;
    private double energyRemainder;

    // Wear
    private int reactorWear;
    private int wearTickCounter;
    private boolean prevWearDegraded;
    private boolean selfDestructActive;
    private int selfDestructChatTimer;
    private boolean finalMeltdownActive;

    // Meltdown
    private boolean meltdownCountdown;
    private int meltdownTimer;

    // Previous integrity
    private int prevShInt = 100, prevCaseInt = 100;

    // Tick counters
    private int pressTick, recipeTick, intensityDownTick, intensityUpTick, soundTick, noFuelWarnTick;

    // =========================
    // CONSTRUCTOR
    // =========================
    private ReactorManager() {
        this.display = new ReactorDisplay(this);
    }

    // =========================
    // GET LOCATION
    // =========================
    public BlockPos getReactorPos() { return reactorPos; }
    public Level getReactorLevel() { return reactorLevel; }
    public boolean isValid() { return valid && reactorPos != null && reactorLevel != null; }
    public String getReactorId() { return reactorId; }

    // =========================
    // SET REACTOR LOCATION
    // =========================
    public void setReactorLocation(Level level, BlockPos pos) {
        if (level != null && pos != null) {
            this.reactorLevel = level;
            this.reactorPos = pos;
            this.valid = true;
            this.reactorId = "REACTOR-" + pos.getX() + "-" + pos.getY() + "-" + pos.getZ();
            saveToDb();
        } else {
            // Remove cable node created for energy output
            if (reactorPos != null && reactorLevel != null) {
                BlockPos coreLoc = reactorPos.below();
                if (CableNetwork.exists(reactorLevel, coreLoc)) {
                    CableNetwork.removeNode(reactorLevel, coreLoc);
                }
            }
            if (reactorId != null) {
                ReactorPersistence.deleteFromDb(reactorId);
            }
            this.reactorLevel = null;
            this.reactorPos = null;
            this.valid = false;
            this.reactorId = null;
            resetAll();
        }
    }

    // =========================
    // VALIDATE STRUCTURE
    // =========================
    public void validateStructure() {
        if (reactorPos == null || reactorLevel == null) return;
        boolean wasValid = valid;
        valid = ReactorStructure.isValid(reactorPos, reactorLevel, false);
        if (!valid && wasValid) {
            setReactorLocation(null, null);
        }
    }

    // =========================
    // PERSISTENCE
    // =========================
    public static void saveAll() {
        ReactorManager r = instance;
        if (r == null) return;
        ReactorState state = buildState(r);
        ReactorPersistence.saveToDb(state);
    }

    public void saveToDb() {
        ReactorState state = buildState(this);
        ReactorPersistence.saveToDb(state);
    }

    private static ReactorState buildState(ReactorManager r) {
        ReactorState s = new ReactorState();
        s.setReactorLocation(r.reactorLevel, r.reactorPos);
        s.setCoreTemp(r.coreTemp);
        s.setCorePress(r.corePress);
        s.setCoreShInt(r.coreShInt);
        s.setCoreCaseTemp(r.coreCaseTemp);
        s.setCoreCasePress(r.coreCasePress);
        s.setCoreCaseInt(r.coreCaseInt);
        s.setRecipeTime(r.recipeTime);
        s.setSelfDestruct(r.selfDestruct);
        s.setReactorWear(r.reactorWear);
        s.setEnergyGenerated(r.energyGenerated);
        return s;
    }

    public static void loadAll() {
        if (instance == null) return;
        ReactorState state = new ReactorState();
        if (ReactorPersistence.loadFromDb(state)) {
            instance.reactorPos = state.getReactorPos();
            instance.valid = state.isValid();
            instance.reactorId = state.getReactorId();
            instance.coreTemp = state.getCoreTemp();
            instance.corePress = state.getCorePress();
            instance.coreShInt = state.getCoreShInt();
            instance.coreCaseTemp = state.getCoreCaseTemp();
            instance.coreCasePress = state.getCoreCasePress();
            instance.coreCaseInt = state.getCoreCaseInt();
            instance.recipeTime = state.getRecipeTime();
            instance.selfDestruct = state.isSelfDestruct();
            instance.reactorWear = state.getReactorWear();
            instance.energyGenerated = state.getEnergyGenerated();
        }
    }

    // =========================
    // MAIN TICK
    // =========================
    public void tick() {
        if (!enabled || !valid || reactorPos == null || reactorLevel == null) return;

        BlockPos base = reactorPos;

        boolean heating = display.isBulbPowered(reactorLevel, base, -1, 0, -2);
        boolean cooling = display.isBulbPowered(reactorLevel, base, 1, 0, -2);

        // Broadcast state changes
        if (heating != display.wasHeating()) {
            broadcast("§6🔥 §eНагрев включён");
            display.setHeating(heating);
        }
        if (cooling != display.wasCooling()) {
            broadcast("§b❄ §3Охлаждение включено");
            display.setCooling(cooling);
        }

        // Temperature control
        if (heating && coreTemp < coreTempMax) {
            if (hasBarrelFuel()) {
                coreTemp += heatRate;
                noFuelWarnTick = 0;
            } else if (noFuelWarnTick == 0) {
                broadcast("§e⚠ §7Нет топлива! В левую бочку поместите алмазные блоки, в правую — золотые блоки.");
                noFuelWarnTick++;
            } else {
                noFuelWarnTick++;
            }
        }
        if (cooling && coreTemp > coreTempCoolMin) {
            coreTemp -= coolRate;
        }

        // Case reaction
        if (heating && hasBarrelFuel()) {
            coreCaseTemp = Math.min(coreCaseTemp + caseTempHeatRate, caseTempMax);
            coreCasePress = Math.min(coreCasePress + casePressHeatRate, casePressMax);
        }
        if (cooling) {
            coreCaseTemp = Math.max(coreCaseTemp - caseTempCoolRate, caseTempCoolMin);
        }

        // Integrity indicator bulbs
        display.updateIntegrityBulbs(base);

        // Integrity warning every 10s
        int warnTick = display.getIntegrityWarnTick() + 1;
        display.setIntegrityWarnTick(warnTick);
        if (warnTick >= 200) {
            display.setIntegrityWarnTick(0);
            if (coreShInt < 100) broadcast("§4⚠ §cЦелостность оболочки ядра нарушена!");
            if (coreCaseInt < 100) broadcast("§4⚠ §cЦелостность корпуса реактора нарушена!");
        }

        // Pressure
        corePress += coreTemp;
        if (corePress < 0) corePress = 0;
        if (coreTemp >= 1000 && coreTemp <= 5000) {
            corePress = Math.max(0, corePress - corePressReduceRate);
        }

        // Natural temp decay
        if (coreTemp > coreTempMin) {
            coreTemp = Math.max(coreTempMin, coreTemp - tempDecayRate);
        }

        // Case pressure decay
        if (coreCasePress > 0) {
            coreCasePress -= casePressDecayRate;
            if (coreCasePress < 0) coreCasePress = 0;
        }

        // Case temp decay
        if (coreCaseTemp > caseTempCoolMin) {
            coreCaseTemp -= caseTempDecayRate;
        }

        // Recipe completion
        if (rcDone) {
            completeRecipe();
        }

        // Integrity threshold warnings
        checkIntegrityThreshold(prevShInt, coreShInt, "оболочки ядра");
        checkIntegrityThreshold(prevCaseInt, coreCaseInt, "корпуса");
        prevShInt = coreShInt;
        prevCaseInt = coreCaseInt;

        // Energy generation
        if (coreTemp > 1000) {
            generateEnergy(base);
        }

        // Meltdown countdown start
        if ((coreShInt <= 0 || coreCaseInt <= 0) && !meltdownCountdown && !selfDestructActive) {
            meltdownCountdown = true;
            meltdownTimer = 200;
            selfDestruct = true;
            broadcast("§4☠ §cЦелостность разрушена! §f10§c секунд до детонации...");
        }
    }

    // =========================
    // ENERGY GENERATION
    // =========================
    private void generateEnergy(BlockPos base) {
        double energyPerTick = (double) coreTemp * 0.9;
        energyRemainder += energyPerTick;
        int toGenerate = (int) energyRemainder;
        if (toGenerate <= 0) return;
        energyRemainder -= toGenerate;
        energyGenerated += toGenerate;

        String worldKey = reactorLevel.dimension().location().toString();
        Collection<CableNode> worldNodes = CableNetwork.getWorldNodes(worldKey);
        List<CableNode> nearbyCables = new ArrayList<>();

        for (CableNode node : worldNodes) {
            int dx = Math.abs(node.getBlockX() - base.getX());
            int dy = Math.abs(node.getBlockY() - base.getY());
            int dz = Math.abs(node.getBlockZ() - base.getZ());
            if (dx <= 3 && dy <= 5 && dz <= 3) {
                nearbyCables.add(node);
            }
        }

        if (!nearbyCables.isEmpty()) {
            int remaining = toGenerate;
            int perNode = toGenerate / nearbyCables.size();
            for (CableNode node : nearbyCables) {
                int space = node.getMaxEnergy() - node.getEnergy();
                int give = Math.min(perNode, space);
                if (give <= 0) continue;
                node.addEnergy(give);
                remaining -= give;
            }
            if (remaining > 0) {
                BlockPos coreLoc = base.below();
                CableNode genNode = CableNetwork.getNode(reactorLevel, coreLoc);
                if (genNode == null) {
                    CableNetwork.addNode(reactorLevel, coreLoc);
                    genNode = CableNetwork.getNode(reactorLevel, coreLoc);
                }
                if (genNode != null) {
                    genNode.setType(NodeType.GENERATOR);
                    genNode.setMaxEnergy(coreTempMax * 10);
                    genNode.addEnergy(remaining);
                }
            }
        } else {
            BlockPos coreLoc = base.below();
            CableNode genNode = CableNetwork.getNode(reactorLevel, coreLoc);
            if (genNode == null) {
                CableNetwork.addNode(reactorLevel, coreLoc);
                genNode = CableNetwork.getNode(reactorLevel, coreLoc);
            }
            if (genNode != null) {
                genNode.setType(NodeType.GENERATOR);
                genNode.setMaxEnergy(coreTempMax * 10);
                genNode.addEnergy(toGenerate);
            }
        }
    }

    // =========================
    // PRESSURE TICK (every 5s)
    // =========================
    public void tickPressure() {
        if (!enabled || !valid || reactorPos == null || reactorLevel == null) return;

        BlockPos base = reactorPos;
        BlockPos coreCenter = new BlockPos(base.getX(), base.getY() - 2, base.getZ());

        int particleCount, radAmount;

        if (corePress >= 500000)      { particleCount = 512; radAmount = 600; }
        else if (corePress >= 400000) { particleCount = 256; radAmount = 500; }
        else if (corePress >= 300000) { particleCount = 128; radAmount = 400; }
        else if (corePress >= 200000) { particleCount = 64;  radAmount = 300; }
        else if (corePress >= 100000) { particleCount = 32;  radAmount = 200; }
        else                          { particleCount = 0;   radAmount = 0;   }

        if (particleCount > 0 && reactorLevel instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    coreCenter.getX() + 0.5, coreCenter.getY() + 2.0, coreCenter.getZ() + 0.5,
                    Math.min(particleCount, 100), 0, 0, 0, 0.1);
        }

        // Pressure division
        if (coreTemp != 0) {
            corePress = corePress / coreTemp;
        } else {
            corePress = 0;
        }
    }

    // =========================
    // INTENSITY DECAY TICK (every 1s)
    // =========================
    public void tickIntensityDown() {
        if (!enabled || !valid) return;

        if (coreTemp >= shIntDecayTempThreshold && coreShInt > 0) {
            coreShInt = Math.max(0, coreShInt - shellIntDecayRate);
        }
        if (coreCasePress >= caseIntDecayPressThreshold && coreCaseInt > 0) {
            coreCaseInt = Math.max(0, coreCaseInt - caseIntDecayPressRate);
        }
        if (coreCaseTemp >= caseIntDecayTempThreshold && coreCaseInt > 0) {
            coreCaseInt = Math.max(0, coreCaseInt - caseIntDecayTempRate);
        }
    }

    // =========================
    // INTENSITY RECOVERY TICK (every 3s)
    // =========================
    public void tickIntensityUp() {
        if (!enabled || !valid) return;

        if (coreTemp <= shellIntRecoveryTempMax && coreShInt < 100) {
            coreShInt = Math.min(100, coreShInt + shellIntRecoveryRate);
        }
        if (coreCasePress <= caseIntRecoveryPressMax && coreCaseTemp <= caseIntRecoveryTempMax && coreCaseInt < 100) {
            coreCaseInt = Math.min(100, coreCaseInt + caseIntRecoveryRate);
        }
    }

    // =========================
    // RECIPE TICK (every 5s)
    // =========================
    public void tickRecipe() {
        if (!enabled || !valid) return;

        if (coreTemp < 1000 && recipeTime > 0) {
            recipeTime--;
        }
        if (coreTemp >= 1000 && coreTemp <= 5000 && recipeTime < recipeTimeMax) {
            recipeTime++;
        }
        if (recipeTime >= recipeTimeMax && hasBarrelFuel()) {
            rcDone = true;
        }
    }

    // =========================
    // WEAR TICK (every 1s)
    // =========================
    public void tickWear() {
        if (!enabled || !valid || reactorPos == null || reactorLevel == null) return;

        if (wearEnabled && !selfDestructActive) {
            boolean isDegraded = coreShInt < 100 || coreCaseInt < 100;
            if (isDegraded != prevWearDegraded) {
                wearTickCounter = 0;
                prevWearDegraded = isDegraded;
            }
            wearTickCounter++;

            if (isDegraded) {
                if (wearTickCounter >= wearIntervalDegradation) {
                    wearTickCounter = 0;
                    if (reactorWear > 0) reactorWear--;
                }
            } else {
                if (wearTickCounter >= wearIntervalNormal) {
                    wearTickCounter = 0;
                    if (reactorWear < 100) {
                        reactorWear++;
                        if (reactorWear >= 100 && !selfDestructActive) {
                            startSelfDestruct();
                        }
                    }
                }
            }
        }

        if (selfDestructActive && !meltdownCountdown) {
            selfDestructChatTimer--;
            if (selfDestructChatTimer <= wearFinalMeltdownAt) {
                finalMeltdownActive = true;
                meltdownCountdown = true;
                meltdownTimer = wearFinalMeltdownDuration * 20;
                broadcast("§4☠ §cВзрыв неизбежен! §f" + wearFinalMeltdownDuration + "§c сек до детонации...");
            } else if (selfDestructChatTimer > 0) {
                broadcast("§4☠ §cДетонация через §f" + selfDestructChatTimer + "§c сек...");
            }
        }
    }

    // =========================
    // MELTDOWN COUNTDOWN TICK
    // =========================
    public void tickMeltdownCountdown() {
        if (!meltdownCountdown || !enabled || !valid || reactorPos == null || reactorLevel == null) return;

        meltdownTimer--;
        if (meltdownTimer > 0 && meltdownTimer % 20 == 0) {
            broadcast("§4☠ §cВзрыв неизбежен! §f" + (meltdownTimer / 20) + "§c сек...");
        }
        if (meltdownTimer <= 0) {
            meltdownCountdown = false;
            finalMeltdownActive = false;
            selfDestructActive = false;
            meltdown();
        }
    }

    // =========================
    // SOUND TICK
    // =========================
    public void tickSound() {
        display.tickSound();
    }

    // =========================
    // SMOOTH DISPLAY TICK
    // =========================
    public void tickSmoothDisplay() {
        display.tickSmoothDisplay();
    }

    // =========================
    // VISUAL TICK
    // =========================
    public void tickVisual() {
        display.tickVisual();
    }

    // =========================
    // UPDATE DISPLAYS (signs)
    // =========================
    public void updateDisplays() {
        display.updateDisplays();
    }

    // =========================
    // START SELF-DESTRUCT
    // =========================
    private void startSelfDestruct() {
        selfDestruct = true;
        selfDestructActive = true;
        selfDestructChatTimer = wearChatCountdown;
        finalMeltdownActive = false;
        broadcast("§4☠ §cКритический износ реактора! §f" + wearChatCountdown + "§c сек до детонации...");
        broadcast("§4☠ §cПротокол самоуничтожения инициирован.");
    }

    // =========================
    // INTEGRITY THRESHOLD CHECK
    // =========================
    private void checkIntegrityThreshold(int prevVal, int currVal, String name) {
        if (currVal < prevVal) {
            if (currVal == 75 || currVal == 50 || currVal == 25) {
                broadcast("§4⚠ §cЦелостность " + name + ": §f" + currVal + "%");
            }
        }
    }

    // =========================
    // RECIPE COMPLETION
    // =========================
    private void completeRecipe() {
        if (reactorPos == null || reactorLevel == null) return;
        BlockPos base = reactorPos;

        consumeBarrelFuel(base, 0, -3, -2, Blocks.DIAMOND_BLOCK);
        consumeBarrelFuel(base, 0, -3, 2, Blocks.GOLD_BLOCK);

        BlockPos dropLoc = new BlockPos(base.getX(), base.getY() - 2, base.getZ());

        // Spawn ancient debris
        ItemEntity debris = new ItemEntity(reactorLevel,
                dropLoc.getX() + 0.5, dropLoc.getY() + 0.5, dropLoc.getZ() + 0.5,
                new ItemStack(Items.ANCIENT_DEBRIS));
        reactorLevel.addFreshEntity(debris);

        if (reactorLevel instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FLAME, dropLoc.getX() + 0.5, dropLoc.getY() + 0.5, dropLoc.getZ() + 0.5,
                    60, 2.5, 2.5, 2.5, 0.15);
            sl.sendParticles(ParticleTypes.LAVA, dropLoc.getX() + 0.5, dropLoc.getY() + 0.5, dropLoc.getZ() + 0.5,
                    20, 1.5, 1.5, 1.5, 0);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, dropLoc.getX() + 0.5, dropLoc.getY() + 0.5, dropLoc.getZ() + 0.5,
                    30, 2.0, 2.0, 2.0, 0.1);
            sl.playSound(null, dropLoc, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.MASTER, 2.0f, 0.5f);
            sl.playSound(null, dropLoc, SoundEvents.FIRE_EXTINGUISH, SoundSource.MASTER, 1.5f, 0.8f);
        }

        // Reset state
        recipeTime = 0;
        rcDone = false;
        coreShInt = 100;
        coreTemp = 0;
        coreCaseInt = 100;
        coreCaseTemp = 0;
        coreCasePress = 0;
        corePress = 0;
        selfDestruct = false;
        meltdownCountdown = false;
        meltdownTimer = 0;
        prevShInt = 100;
        prevCaseInt = 100;
        reactorWear = 0;
        wearTickCounter = 0;
        prevWearDegraded = false;
        selfDestructActive = false;
        selfDestructChatTimer = 0;
        finalMeltdownActive = false;
        energyGenerated = 0;
        energyRemainder = 0;
        noFuelWarnTick = 0;

        display.resetDisplay();
        saveToDb();
        broadcast("§4☢ §cРецепт слияния готов! Древний обломок выброшен в центре реактора.");
    }

    // =========================
    // MELTDOWN
    // =========================
    private void meltdown() {
        energyRemainder = 0;
        energyGenerated = 0;

        if (reactorPos == null || reactorLevel == null) return;

        BlockPos base = reactorPos;
        BlockPos coreCenter = new BlockPos(base.getX(), base.getY() - 2, base.getZ());

        // Destroy key blocks
        reactorLevel.setBlock(base.below(), Blocks.AIR.defaultBlockState(), 3);
        reactorLevel.setBlock(new BlockPos(base.getX(), base.getY() - 5, base.getZ()), Blocks.AIR.defaultBlockState(), 3);

        // Create explosion
        if (reactorLevel instanceof ServerLevel sl) {
            sl.explode(null, coreCenter.getX() + 0.5, coreCenter.getY() + 0.5, coreCenter.getZ() + 0.5,
                    meltdownExplosionRadius, Level.ExplosionInteraction.TNT);

            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    coreCenter.getX() + 0.5, coreCenter.getY() + 0.5, coreCenter.getZ() + 0.5,
                    100, 3.0, 3.0, 3.0, 0.5);
        }

        // Lightning strike
        if (reactorLevel instanceof ServerLevel sl) {
            net.minecraft.world.entity.LightningBolt lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(sl);
            if (lightning != null) {
                lightning.setPos(coreCenter.getX() + 0.5, coreCenter.getY() + 0.5, coreCenter.getZ() + 0.5);
                sl.addFreshEntity(lightning);
            }
        }

        broadcast("§4☠ §cРасплавление! Ядро реактора разрушено!");

        setReactorLocation(null, null);
    }

    // =========================
    // RESET ALL
    // =========================
    private void resetAll() {
        coreTemp = 0;
        corePress = 0;
        coreShInt = 100;
        coreCaseTemp = 0;
        coreCasePress = 0;
        coreCaseInt = 100;
        recipeTime = 0;
        rcDone = false;
        selfDestruct = false;
        reactorWear = 0;
        wearTickCounter = 0;
        prevWearDegraded = false;
        selfDestructActive = false;
        selfDestructChatTimer = 0;
        finalMeltdownActive = false;
        energyGenerated = 0;
        energyRemainder = 0;
        pressTick = 0;
        recipeTick = 0;
        intensityDownTick = 0;
        intensityUpTick = 0;
        soundTick = 0;
        noFuelWarnTick = 0;
        meltdownCountdown = false;
        meltdownTimer = 0;
        prevShInt = 100;
        prevCaseInt = 100;
        display.resetDisplay();
    }

    // =========================
    // BARREL FUEL HELPERS
    // =========================
    private boolean hasBarrelFuel() {
        if (reactorPos == null || reactorLevel == null) return false;
        BlockPos base = reactorPos;
        return checkBarrelForFuel(base.offset(0, -3, -2), Blocks.DIAMOND_BLOCK, 1)
            && checkBarrelForFuel(base.offset(0, -3, 2), Blocks.GOLD_BLOCK, 1);
    }

    private boolean checkBarrelForFuel(BlockPos barrelPos, net.minecraft.world.level.block.Block fuelType, int minCount) {
        if (reactorLevel == null) return false;
        BlockState state = reactorLevel.getBlockState(barrelPos);
        if (!state.is(Blocks.BARREL)) return false;
        if (reactorLevel.getBlockEntity(barrelPos) instanceof BarrelBlockEntity barrel) {
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                ItemStack item = barrel.getItem(i);
                if (item.is(fuelType.asItem()) && item.getCount() >= minCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean consumeBarrelFuel(BlockPos base, int dx, int dy, int dz, net.minecraft.world.level.block.Block fuelType) {
        if (reactorLevel == null) return false;
        BlockPos barrelPos = base.offset(dx, dy, dz);
        BlockState state = reactorLevel.getBlockState(barrelPos);
        if (!state.is(Blocks.BARREL)) return false;
        if (reactorLevel.getBlockEntity(barrelPos) instanceof BarrelBlockEntity barrel) {
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                ItemStack item = barrel.getItem(i);
                if (item.is(fuelType.asItem())) {
                    item.shrink(1);
                    barrel.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    // =========================
    // GETTERS
    // =========================
    public int getCoreTemp() { return coreTemp; }
    public int getCorePress() { return corePress; }
    public int getCoreShInt() { return coreShInt; }
    public int getCoreCaseTemp() { return coreCaseTemp; }
    public int getCoreCasePress() { return coreCasePress; }
    public int getCoreCaseInt() { return coreCaseInt; }
    public int getRecipeTime() { return recipeTime; }
    public boolean isSelfDestruct() { return selfDestruct; }
    public boolean isMeltdownCountdown() { return meltdownCountdown; }
    public int getMeltdownTimer() { return meltdownTimer; }
    public boolean isSelfDestructActive() { return selfDestructActive; }
    public boolean isFinalMeltdownActive() { return finalMeltdownActive; }
    public int getReactorWear() { return reactorWear; }
    public long getEnergyGenerated() { return energyGenerated; }

    // Display getters (delegated)
    public int getDisplayCoreTemp() { return display.getDisplayCoreTemp(); }
    public int getDisplayCorePress() { return display.getDisplayCorePress(); }
    public int getDisplayCoreShInt() { return display.getDisplayCoreShInt(); }
    public int getDisplayCoreCaseTemp() { return display.getDisplayCoreCaseTemp(); }
    public int getDisplayCoreCasePress() { return display.getDisplayCoreCasePress(); }
    public int getDisplayCoreCaseInt() { return display.getDisplayCoreCaseInt(); }
    public int getDisplayRecipeTime() { return display.getDisplayRecipeTime(); }
    public int getDisplayReactorWear() { return display.getDisplayReactorWear(); }
    public int getDisplayEnergyRate() { return display.getDisplayEnergyRate(); }

    // =========================
    // BROADCAST
    // =========================
    private void broadcast(String message) {
        String prefix = "§4Р.Т.С §8» §f";
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (reactorPos != null && reactorLevel != null
                    && player.level().dimension().equals(reactorLevel.dimension())
                    && player.distanceToSqr(reactorPos.getX(), reactorPos.getY(), reactorPos.getZ()) <= 225) {
                player.sendSystemMessage(MessageUtil.legacy(prefix + message));
            }
        }
    }
}
