package com.gameplayadditions.module;

import com.gameplayadditions.command.*;
import com.gameplayadditions.listener.*;
import com.gameplayadditions.punish.PunishJoinListener;
import com.gameplayadditions.util.ConsoleLogger;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * CoreModule — базовый модуль для команд /ga и системных слушателей.
 * <p>
 * Регистрирует все субкоманды и слушает {@link RegisterCommandsEvent}
 * для добавления /ga в Brigadier диспетчер.
 * <p>
 * Essential модуль: без команд мод бесполезен.
 */
public class CoreModule extends PluginModule {

    private boolean commandsRegistered = false;

    public CoreModule() {
        super("Core", true);
    }

    @Override
    protected void onInit() {
        var registry = SubCommandRegistry.getInstance();

        // ═══════════════════════════════════════════
        // РЕГИСТРАЦИЯ СЛУШАТЕЛЕЙ (listeners)
        // ═══════════════════════════════════════════

        // Система энергосети (блоки)
        NeoForge.EVENT_BUS.register(new BlockBreakListener());
        NeoForge.EVENT_BUS.register(new BlockPlaceListener());

        // Защита сервера
        NeoForge.EVENT_BUS.register(new PluginHideListener());
        NeoForge.EVENT_BUS.register(new PowerInterceptListener());
        NeoForge.EVENT_BUS.register(new VoidProtectionListener());
        NeoForge.EVENT_BUS.register(new ServerBrandListener());
        NeoForge.EVENT_BUS.register(new ShulkerBulletListener());

        // Чат
        NeoForge.EVENT_BUS.register(new ChatFilterManager());

        // MOTD
        NeoForge.EVENT_BUS.register(new MOTDListener());

        // Мультиметр (осмотр энергосети)
        NeoForge.EVENT_BUS.register(new MultimeterListener());

        // Наказания (баны/муты при входе)
        NeoForge.EVENT_BUS.register(new PunishJoinListener());

        // ═══════════════════════════════════════════
        // РЕГИСТРАЦИЯ СУБКОМАНД
        // ═══════════════════════════════════════════

        // Помощь
        registry.register(new HelpCommand());

        // Player features
        registry.register(new HomeCommand());
        registry.register(new NotesCommand());

        // Server management
        registry.register(new PowerCommand());
        registry.register(new MaintenanceCommand());
        registry.register(new BroadcastCommand());
        registry.register(new HealFeedCommand());

        // Admin tools
        registry.register(new VanishCommand());
        registry.register(new ChgDimSubcommand());

        // Economy
        registry.register(new EconomyCommand());

        // Whitelist / Blacklist
        registry.register(new WhitelistCommand());
        registry.register(new BlacklistCommand());
        registry.register(new OpWhitelistCommand());

        // Punishment
        registry.register(new PunishCommand());

        // Reports
        registry.register(new ReportCommand());
        registry.register(new ReportsCommand());

        ConsoleLogger.info("[Core] Registered " + registry.getCommandNames().size() + " subcommands.");

        // ── Слушаем RegisterCommandsEvent ──
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        commandsRegistered = true;
    }

    @Override
    protected void onDisable() {
        if (commandsRegistered) {
            NeoForge.EVENT_BUS.unregister(this);
            SubCommandRegistry.reset();
            commandsRegistered = false;
            ConsoleLogger.info("[Core] Commands unregistered.");
        }
    }

    /**
     * RegisterCommandsEvent — регистрация /ga в Brigadier.
     */
    private void onRegisterCommands(RegisterCommandsEvent event) {
        GaCommand.register(event.getDispatcher());
        ConsoleLogger.info("[Core] /ga command registered in Brigadier.");
    }
}
