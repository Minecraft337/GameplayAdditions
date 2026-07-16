package com.gameplayadditions.combat.weapons.core;

public interface BaseProjectile {

    ProjectileType getType();

    void tick();

    boolean isDead();
}
