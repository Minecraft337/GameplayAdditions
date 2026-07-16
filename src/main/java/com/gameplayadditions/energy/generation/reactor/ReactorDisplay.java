package com.gameplayadditions.energy.generation.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.SignBlock;

/**
 * ReactorDisplay — управление визуальными эффектами, звуками и обновлением табличек реактора.
 * <p>
 * Порт {@code com.mcplugin.energy.generation.reactor.ReactorDisplay} из MC-Plugin.
 */
public class ReactorDisplay {

    private final ReactorManager reactor;

    // Smoothed display values
    private double displayCoreTemp;
    private double displayCorePress;
    private double displayCoreShInt = 100;
    private double displayCoreCaseTemp;
    private double displayCoreCasePress;
    private double displayCoreCaseInt = 100;
    private double displayRecipeTime;
    private double displayReactorWear;
    private double displayEnergyRate;

    private static final double SMOOTHING_FACTOR = 0.35;

    // Tick counters
    private int displayTick;
    private boolean prevHeating;
    private boolean prevCooling;
    private int integrityWarnTick;
    private int soundTick;

    public ReactorDisplay(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // SMOOTH DISPLAY TICK
    // =========================
    public void tickSmoothDisplay() {
        displayCoreTemp += (reactor.getCoreTemp() - displayCoreTemp) * SMOOTHING_FACTOR;
        displayCorePress += (reactor.getCorePress() - displayCorePress) * SMOOTHING_FACTOR;
        displayCoreShInt += (reactor.getCoreShInt() - displayCoreShInt) * SMOOTHING_FACTOR;
        displayCoreCaseTemp += (reactor.getCoreCaseTemp() - displayCoreCaseTemp) * SMOOTHING_FACTOR;
        displayCoreCasePress += (reactor.getCoreCasePress() - displayCoreCasePress) * SMOOTHING_FACTOR;
        displayCoreCaseInt += (reactor.getCoreCaseInt() - displayCoreCaseInt) * SMOOTHING_FACTOR;
        displayRecipeTime += (reactor.getRecipeTime() - displayRecipeTime) * SMOOTHING_FACTOR;
        displayReactorWear += (reactor.getReactorWear() - displayReactorWear) * SMOOTHING_FACTOR;

        double rawEnergyRate = reactor.getCoreTemp() > 1000 ? (double) reactor.getCoreTemp() * 0.9 * 20.0 : 0.0;
        displayEnergyRate += (rawEnergyRate - displayEnergyRate) * SMOOTHING_FACTOR;
    }

    // =========================
    // SOUND TICK
    // =========================
    public void tickSound() {
        BlockPos base = reactor.getReactorPos();
        Level level = reactor.getReactorLevel();
        if (base == null || level == null) return;

        if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) {
            level.playSound(null, base, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1.0f, 1.5f);
        }
    }

    // =========================
    // VISUAL TICK (particles)
    // =========================
    public void tickVisual() {
        BlockPos base = reactor.getReactorPos();
        ServerLevel level = reactor.getReactorLevel() instanceof ServerLevel sl ? sl : null;
        if (base == null || level == null) return;

        BlockPos coreCenter = new BlockPos(base.getX(), base.getY() - 2, base.getZ());

        int coreTemp = reactor.getCoreTemp();
        boolean meltdown = reactor.isMeltdownCountdown();
        int meltdownTimer = reactor.getMeltdownTimer();

        // Core temperature particles
        if (coreTemp <= 999) {
            level.sendParticles(ParticleTypes.WHITE_ASH, coreCenter.getX() + 0.5, coreCenter.getY() - 0.5,
                    coreCenter.getZ() + 0.5, 8, 0, 0, 0, 0);
        } else if (coreTemp <= 1999) {
            level.sendParticles(ParticleTypes.FLAME, coreCenter.getX() + 0.5, coreCenter.getY() - 0.5,
                    coreCenter.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
        } else if (coreTemp <= 3999) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, coreCenter.getX() + 0.5, coreCenter.getY() - 0.5,
                    coreCenter.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        } else {
            level.sendParticles(ParticleTypes.LAVA, coreCenter.getX() + 0.5, coreCenter.getY() - 0.5,
                    coreCenter.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
        }

        // High temp effects
        if (coreTemp >= 1000) {
            level.playSound(null, coreCenter, SoundEvents.BEACON_POWER_SELECT, SoundSource.MASTER, 0.5f, 1.0f);
        }

        // Meltdown effects
        if (meltdown) {
            float progress = 1.0f - (meltdownTimer / 200.0f);
            if (progress < 0) progress = 0;
            if (progress > 1) progress = 1;

            int smokeCount = 8 + (int)(progress * 56);
            int fireCount = 2 + (int)(progress * 14);

            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    coreCenter.getX() + 0.5, coreCenter.getY() + 0.5, coreCenter.getZ() + 0.5,
                    smokeCount, 0.5, 0.5, 0.5, 0.15);
            level.sendParticles(ParticleTypes.LAVA,
                    coreCenter.getX() + 0.5, coreCenter.getY() - 0.5, coreCenter.getZ() + 0.5,
                    fireCount, 0.3, 0.3, 0.3, 0);
            level.sendParticles(ParticleTypes.FLAME,
                    coreCenter.getX() + 0.5, coreCenter.getY() - 0.5, coreCenter.getZ() + 0.5,
                    fireCount, 0.3, 0.3, 0.3, 0.05);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    coreCenter.getX() + 0.5, coreCenter.getY() - 0.5, coreCenter.getZ() + 0.5,
                    (int)(2 + progress * 8), 1.0, 1.0, 1.0, 0);

            float volume = 0.5f + progress * 4.0f;
            float pitch = 0.5f + progress * 0.8f;
            level.playSound(null, coreCenter, SoundEvents.BEACON_AMBIENT, SoundSource.MASTER, volume, pitch);
        }
    }

    // =========================
    // UPDATE SIGNS
    // =========================
    public void updateDisplays() {
        BlockPos base = reactor.getReactorPos();
        Level level = reactor.getReactorLevel();
        if (base == null || level == null) return;

        displayTick++;

        boolean selfDestruct = reactor.isSelfDestructActive() || reactor.isMeltdownCountdown();
        boolean meltdownCdown = reactor.isMeltdownCountdown();

        if (selfDestruct) {
            String blank = " ";
            String noSignal = meltdownCdown ? "Взрыв неизбежен!" : "НЕТ СИГНАЛА";

            setSignText(level, base.offset(-1, -4, -3), 0, blank);
            setSignText(level, base.offset(-1, -4, -3), 1, noSignal);
            setSignText(level, base.offset(-1, -4, -3), 2, blank);
            setSignText(level, base.offset(-1, -4, -3), 3, blank);

            setSignText(level, base.offset(0, -4, -3), 0, blank);
            setSignText(level, base.offset(0, -4, -3), 1, noSignal);
            setSignText(level, base.offset(0, -4, -3), 2, blank);
            setSignText(level, base.offset(0, -4, -3), 3, blank);

            setSignText(level, base.offset(1, -4, -3), 0, blank);
            setSignText(level, base.offset(1, -4, -3), 1, noSignal);
            setSignText(level, base.offset(1, -4, -3), 2, blank);
            setSignText(level, base.offset(1, -4, -3), 3, blank);
            return;
        }

        int dCoreTemp = (int) Math.round(displayCoreTemp);
        int dCorePress = (int) Math.round(displayCorePress);
        int dCoreShInt = (int) Math.round(displayCoreShInt);
        int dCaseTemp = (int) Math.round(displayCoreCaseTemp);
        int dCasePress = (int) Math.round(displayCoreCasePress);
        int dCaseInt = (int) Math.round(displayCoreCaseInt);
        int dRecipe = (int) Math.round(displayRecipeTime);
        int dWear = (int) Math.round(displayReactorWear);

        boolean flashing = dCoreShInt < 100 || dCaseInt < 100;
        String color = (flashing && (displayTick % 10 < 5)) ? "§c" : "§f";

        // Center sign — core data
        setSignText(level, base.offset(0, -4, -3), 0, color + "Данные ядра");
        setSignText(level, base.offset(0, -4, -3), 1, color + "T: " + dCoreTemp + " C*");
        setSignText(level, base.offset(0, -4, -3), 2, color + "P: " + dCorePress + " kPa");
        setSignText(level, base.offset(0, -4, -3), 3, color + "I: " + dCoreShInt + " %");

        // Left sign — case data
        setSignText(level, base.offset(-1, -4, -3), 0, color + "Данные корпуса");
        setSignText(level, base.offset(-1, -4, -3), 1, color + "T: " + dCaseTemp + " C*");
        setSignText(level, base.offset(-1, -4, -3), 2, color + "P: " + dCasePress + " kPa");
        setSignText(level, base.offset(-1, -4, -3), 3, color + "I: " + dCaseInt + " %");

        // Right sign — recipe data
        setSignText(level, base.offset(1, -4, -3), 0, color + "Данные рецепта");
        setSignText(level, base.offset(1, -4, -3), 1, color + "P: " + dRecipe + " %");
        String status = dRecipe <= 0 ? "Бездействует" : (dRecipe < reactor.getRecipeTimeMax() ? "Готовится" : "Завершён");
        setSignText(level, base.offset(1, -4, -3), 2, color + "S: " + status);
        setSignText(level, base.offset(1, -4, -3), 3, color + "W: " + dWear + " %");
    }

    // =========================
    // HELPERS
    // =========================
    public boolean isBulbPowered(Level level, BlockPos center, int dx, int dy, int dz) {
        BlockPos pos = center.offset(dx, dy, dz);
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WAXED_COPPER_BULB)) return false;
        return state.getValue(CopperBulbBlock.POWERED);
    }

    public void setBulbLit(Level level, BlockPos center, int dx, int dy, int dz, boolean lit) {
        BlockPos pos = center.offset(dx, dy, dz);
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WAXED_COPPER_BULB)) return;
        if (state.getValue(CopperBulbBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(CopperBulbBlock.LIT, lit), 3);
        }
    }

    private void setSignText(Level level, BlockPos pos, int line, String text) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SignBlockEntity sign) {
            SignText oldText = sign.getFrontText();
            SignText newText = oldText.setMessage(line, Component.literal(text));
            sign.setText(newText, true);
            sign.setChanged();
            if (level instanceof ServerLevel sl) {
                sl.getChunkSource().blockChanged(pos);
            }
        }
    }

    public void updateIntegrityBulbs(BlockPos base) {
        Level level = reactor.getReactorLevel();
        if (level == null) return;
        setBulbLit(level, base, -1, 0, 2, reactor.getCoreShInt() < 100);
        setBulbLit(level, base, 1, 0, 2, reactor.getCoreCaseInt() < 100);
    }

    // =========================
    // RESET
    // =========================
    public void resetDisplay() {
        displayCoreTemp = 0;
        displayCorePress = 0;
        displayCoreShInt = 100;
        displayCoreCaseTemp = 0;
        displayCoreCasePress = 0;
        displayCoreCaseInt = 100;
        displayRecipeTime = 0;
        displayReactorWear = 0;
        displayEnergyRate = 0;
        displayTick = 0;
        prevHeating = false;
        prevCooling = false;
        integrityWarnTick = 0;
        soundTick = 0;
    }

    // =========================
    // GETTERS
    // =========================
    public int getDisplayTick() { return displayTick; }

    public int getDisplayCoreTemp() { return (int) Math.round(displayCoreTemp); }
    public int getDisplayCorePress() { return (int) Math.round(displayCorePress); }
    public int getDisplayCoreShInt() { return (int) Math.round(displayCoreShInt); }
    public int getDisplayCoreCaseTemp() { return (int) Math.round(displayCoreCaseTemp); }
    public int getDisplayCoreCasePress() { return (int) Math.round(displayCoreCasePress); }
    public int getDisplayCoreCaseInt() { return (int) Math.round(displayCoreCaseInt); }
    public int getDisplayRecipeTime() { return (int) Math.round(displayRecipeTime); }
    public int getDisplayReactorWear() { return (int) Math.round(displayReactorWear); }
    public int getDisplayEnergyRate() { return (int) Math.round(displayEnergyRate); }

    public boolean wasHeating() { return prevHeating; }
    public boolean wasCooling() { return prevCooling; }
    public void setHeating(boolean val) { prevHeating = val; }
    public void setCooling(boolean val) { prevCooling = val; }

    public int getIntegrityWarnTick() { return integrityWarnTick; }
    public void setIntegrityWarnTick(int val) { integrityWarnTick = val; }
}
