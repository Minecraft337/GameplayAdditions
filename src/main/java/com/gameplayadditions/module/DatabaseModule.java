package com.gameplayadditions.module;

import com.gameplayadditions.database.DBBootstrap;

/**
 * DatabaseModule — модуль инициализации SQLite.
 * <p>
 * Essential модуль: без БД мод не может работать.
 */
public class DatabaseModule extends PluginModule {

    public DatabaseModule() {
        super("Database", true);
    }

    @Override
    protected void onInit() {
        DBBootstrap.init();
    }

    @Override
    protected void onDisable() {
        DBBootstrap.shutdown();
    }
}
