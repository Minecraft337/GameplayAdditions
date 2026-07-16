package com.gameplayadditions.mechanics.features.items;

import com.gameplayadditions.core.AbstractFeature;

/**
 * 📝 NotesManagerFeature — stub координатор для подсистемы «записки игроков».
 * <p>
 * Порт {@code com.mcplugin.mechanics.features.items.NotesManager} (Bukkit, 733 B).
 * Bukkit-оригинал — singleton manager, который при {@code init()} инстанцирует
 * и регистрирует {@code NotesGUIListener} + {@code NotesDatabase}, плюс
 * {@code getInstance()} API.
 * <p>
 * MVP решений (после review 2026):
 * <ul>
 *   <li>Удалены {@code getInstance()} static (всегда null — мёртвый API).</li>
 *   <li>Удалён {@code noopSubscribe()} пустой {@code @SubscribeEvent}.</li>
 *   <li>Удалён {@code registerGameEvents()} call — нет @SubscribeEvent методов.</li>
 *   <li>NotesGUIListener (~9.6 KB) — Bukkit InventoryGUI; для полного порта на 1.21
 *       потребовалась бы реимплементация через {@code AbstractContainerMenu} +
 *       NeoForge {@code MenuType}. <b>Отложено</b> — TODO отдельный PR.</li>
 *   <li>NotesDatabase (~3.9 KB) — обёртка над {@code DatabaseManager}; уже частично
 *       портирована в {@code DatabaseModule}. <b>Отложено</b>.</li>
 * </ul>
 *
 * <p>Этот класс — minimal lifecycle stub, чтобы имя 'notes_manager' существовало в
 * {@code FeatureRegistry}, и чтобы позже NotesGUIListener/NotesDatabase могли быть
 * портированы и ссылаться на этот Feature как на parent.</p>
 */
public class NotesManagerFeature extends AbstractFeature {

    @Override
    public String getName() {
        return "notes_manager";
    }

    @Override
    public void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        logInfo("setup complete. Stub — full GUI/DB port pending.");
    }

    @Override
    public void onServerStart(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        super.onServerStart(event);
        logInfo("NotesManager installed. NotesGUIListener + NotesDatabase — TODO (NeoForge MenuType port).");
    }
}
