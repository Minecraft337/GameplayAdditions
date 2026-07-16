package com.gameplayadditions.mechanics.features.player;

import com.gameplayadditions.core.AbstractFeature;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * ModeProtectFeature — защита от смены игрового режима в определённых мирах.
 *
 * <p>Порт {@code com.mcplugin.mechanics.features.player.ModeProtectManager} из MC-Plugin.
 * Bukkit: {@code PlayerGameModeChangeEvent}.
 * NeoForge: {@link PlayerEvent.PlayerChangeGameModeEvent} (implements ICancellableEvent).
 *
 * <p>Конфигурация:
 * <ul>
 *   <li>{@code enabled=true} — вкл/выкл</li>
 *   <li>{@code worlds=[]} — список защищённых миров (пусто = все миры)</li>
 *   <li>{@code bypassPermission="gameplayadditions.bypass.modeprotect"} — пермишен для обхода</li>
 *   <li>{@code message="&cYou are not allowed to change your game mode in this world!"} — сообщение</li>
 * </ul>
 */
public class ModeProtectFeature extends AbstractFeature {

    // TODO(config): перенести в ConfigManager
    private boolean enabled = true;
    private List<String> protectedWorlds = new ArrayList<>(); // пусто = все миры
    private String bypassPermission = "gameplayadditions.bypass.modeprotect";
    private String warningMessage = "\u00A7cYou are not allowed to change your game mode in this world!";

    @Override
    public String getName() {
        return "mode_protect";
    }

    @Override
    public void onServerStart(ServerStartingEvent event) {
        registerGameEvents();
        super.onServerStart(event);
    }

    @SubscribeEvent
    public void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        // bypass permission: OP level 2+
        if (player.hasPermissions(2)) return;

        // world check
        String worldId = player.level().dimension().location().toString();
        if (!protectedWorlds.isEmpty() && !protectedWorlds.contains(worldId)) {
            return; // world не в списке защищённых
        }

        // Отменяем смену режима
        event.setCanceled(true);

        // Примечание: принудительный перевод в SURVIVAL требует вызова
        // player.setGameMode() после завершения текущего event (чтобы избежать
        // рекурсии). TODO: реализовать через server task на след. тик.
        // Bukkit-оригинал вызывал player.setGameMode(SURVIVAL) после cancel.

        // Отправляем предупреждение
        player.sendSystemMessage(Component.literal(warningMessage));
    }
}
