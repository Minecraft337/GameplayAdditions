package com.gameplayadditions;

import com.gameplayadditions.config.ConfigManager;
import com.gameplayadditions.config.MessagesManager;
import com.gameplayadditions.core.FeatureRegistry;
import com.gameplayadditions.database.DatabaseManager;
import com.gameplayadditions.energy.generation.reactor.ReactorManager;
import com.gameplayadditions.mechanics.environment.magnet.MagnetFeature;
import com.gameplayadditions.mechanics.features.blocks.BlockDmgFeature;
import com.gameplayadditions.mechanics.features.blocks.BoostedCobwebFeature;
import com.gameplayadditions.mechanics.features.blocks.ContainerTriggerFeature;
import com.gameplayadditions.mechanics.features.integrity.IntegrityCoreFeature;
import com.gameplayadditions.mechanics.features.integrity.IntegrityPiercingFeature;
import com.gameplayadditions.mechanics.features.integrity.IntegrityCombineFeature;
import com.gameplayadditions.mechanics.features.creativeitem.CreativeItemValidatorFeature;
import com.gameplayadditions.mechanics.features.lightning.LightCookingFeature;
import com.gameplayadditions.mechanics.features.items.ItemKillFeature;
import com.gameplayadditions.mechanics.features.items.NotesManagerFeature;
import com.gameplayadditions.mechanics.features.movement.BlockFrictionFeature;
import com.gameplayadditions.mechanics.features.world.BeaconFeature;
import com.gameplayadditions.mechanics.features.world.DeathBellFeature;
import com.gameplayadditions.mechanics.features.world.DragonEggFeature;
import com.gameplayadditions.mechanics.features.player.ShieldSlownessFeature;
import com.gameplayadditions.mechanics.features.player.WaypointFeature;
import com.gameplayadditions.mechanics.features.player.AttributesFeature;
import com.gameplayadditions.mechanics.features.player.ModeProtectFeature;
import com.gameplayadditions.mechanics.features.security.CodePanelCleanupFeature;
import com.gameplayadditions.mechanics.features.world.ChunkLoaderFeature;
import com.gameplayadditions.mechanics.features.world.EntityLocatorFeature;
import com.gameplayadditions.mechanics.crafting.RecipeRegistryFeature;
import com.gameplayadditions.mechanics.features.items.ChestplateFlightFeature;
import com.gameplayadditions.mechanics.features.items.NetheriteUpgradeFeature;
import com.gameplayadditions.mechanics.features.world.ConcreteBucketFeature;
import com.gameplayadditions.mechanics.features.anticheat.AntiCheatFeature;
import com.gameplayadditions.mechanics.features.auth.AuthFeature;
import com.gameplayadditions.mechanics.crafting.LeadIngotFeature;
import com.gameplayadditions.mechanics.crafting.LeadShieldFeature;
import com.gameplayadditions.mechanics.crafting.ConcreteBucketCraftFeature;
import com.gameplayadditions.mechanics.environment.magnet.MagnetStructure;
import com.gameplayadditions.mechanics.radiation.RadiationTaskFeature;
import com.gameplayadditions.mechanics.features.blocks.EnderChestFeature;
import com.gameplayadditions.mechanics.features.blocks.GlassBreakFeature;
import com.gameplayadditions.mechanics.features.blocks.TerracotaSpeedFeature;
import com.gameplayadditions.mechanics.vanish.VanishManager;
import com.gameplayadditions.module.CoreModule;
import com.gameplayadditions.module.DatabaseModule;
import com.gameplayadditions.module.EnergyModule;
import com.gameplayadditions.module.ModuleManager;
import com.gameplayadditions.server.PowerManager;
import com.gameplayadditions.util.ConsoleLogger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod(GameplayAdditionsMod.MOD_ID)
public class GameplayAdditionsMod {

    public static final String MOD_ID = "gameplayadditions";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static GameplayAdditionsMod instance;
    private Path modConfigDir;

    public GameplayAdditionsMod(IEventBus modEventBus) {
        instance = this;

        // ─── Logging ────────────────────────────────────────────────────────
        ConsoleLogger.init();
        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  Gameplay Additions — Initializing...");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        // ─── Lifecycle events ──────────────────────────────────────────────
        modEventBus.addListener(FMLCommonSetupEvent.class, this::onCommonSetup);

        // ─── Server events ─────────────────────────────────────────────────
        var serverBus = NeoForge.EVENT_BUS;
        serverBus.addListener(ServerStartingEvent.class, this::onServerStarting);
        serverBus.addListener(ServerStoppingEvent.class, this::onServerStopping);

        // ─── FeatureRegistry: новый слой фич поверх существующих модулей ──
        // Регистрация ДО init() — иначе фичи не подцепятся.
        // Существующие Manager'ы (Radiation, Reactor, Cable, Vanish, etc.)
        // по-прежнему живут через ModuleManager — здесь только портированные
        // фичи единого паттерна IFeature.
        FeatureRegistry.register(new MagnetFeature());
        FeatureRegistry.register(new GlassBreakFeature());
        FeatureRegistry.register(new BoostedCobwebFeature());
        FeatureRegistry.register(new TerracotaSpeedFeature());
        FeatureRegistry.register(new BlockDmgFeature());
        FeatureRegistry.register(new EnderChestFeature());
        FeatureRegistry.register(new ContainerTriggerFeature());
        FeatureRegistry.register(new IntegrityCoreFeature());
        FeatureRegistry.register(new IntegrityPiercingFeature());
        FeatureRegistry.register(new IntegrityCombineFeature());
        FeatureRegistry.register(new CreativeItemValidatorFeature());
        FeatureRegistry.register(new LightCookingFeature());
        FeatureRegistry.register(new NotesManagerFeature());
        FeatureRegistry.register(new ItemKillFeature());
        FeatureRegistry.register(new BlockFrictionFeature());

        // World features (периодические тики + bell interaction)
        FeatureRegistry.register(new BeaconFeature());
        FeatureRegistry.register(new DeathBellFeature());
        FeatureRegistry.register(new DragonEggFeature());

        // Radiation periodic tick (использует существующий RadiationManager)
        FeatureRegistry.register(new RadiationTaskFeature());

        // MagnetStructure — статическая утилита, регистрации не требует.

        // Player features (shield slowness, waypoint, attributes, mode protect)
        FeatureRegistry.register(new ShieldSlownessFeature());
        FeatureRegistry.register(new WaypointFeature());
        FeatureRegistry.register(new AttributesFeature());
        FeatureRegistry.register(new ModeProtectFeature());

        // Security features (codepanel cleanup)
        FeatureRegistry.register(new CodePanelCleanupFeature());

        // Crafting features (recipe registry)
        FeatureRegistry.register(new RecipeRegistryFeature());

        // World features (chunk loader, entity locator)
        FeatureRegistry.register(new ChunkLoaderFeature());
        FeatureRegistry.register(new EntityLocatorFeature());

        // Item features (chestplate flight, netherite upgrade)
        FeatureRegistry.register(new ChestplateFlightFeature());
        FeatureRegistry.register(new NetheriteUpgradeFeature());

        // Crafting features (lead ingot, lead shield, concrete bucket)
        FeatureRegistry.register(new LeadIngotFeature());
        FeatureRegistry.register(new LeadShieldFeature());
        FeatureRegistry.register(new ConcreteBucketCraftFeature());

        // World features (concrete bucket conversion)
        FeatureRegistry.register(new ConcreteBucketFeature());

        // AntiCheat (5 checks: AutoClicker, FastBow, PortalInventory, FoodSprint, NoFall)
        FeatureRegistry.register(new AntiCheatFeature());

        // AntiCheat extensions: core infra + Combat/Movement/World/Misc checks (28 checks)
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.anticheat.AntiCheatCoreFeature());
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.anticheat.CombatAntiCheatFeature());
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.anticheat.MovementAntiCheatFeature());
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.anticheat.WorldAntiCheatFeature());
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.anticheat.MiscAntiCheatFeature());

        // Auth (player authentication, rate limiting, timeout)
        FeatureRegistry.register(new AuthFeature());

        // Auth extensions (DB, 2FA, command orchestrator)
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.auth.AuthDatabaseFeature());
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.auth.Auth2FAFeature());
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.auth.AuthAuthenticatorFeature());

        // KeyAuth — альтернативная авторизация через локальный .key файл на клиенте.
        FeatureRegistry.register(new com.gameplayadditions.mechanics.features.keyauth.KeyAuthFeature());

        FeatureRegistry.init(modEventBus);
    }

    /**
     * FMLCommonSetup — инициализация инфраструктуры (ранняя фаза).
     * Выполняется в enqueueWork() для потокобезопасности (SQLite, файловые операции).
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ConsoleLogger.info("[Lifecycle] Common setup starting...");

            // Config directory — NeoForge's default config path
            modConfigDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
            modConfigDir.toFile().mkdirs();

            // Database folder
            DatabaseManager.setDataFolder(modConfigDir.toFile());

            // Config
            ConfigManager.init(modConfigDir.toFile());
            MessagesManager.init(modConfigDir.toFile());

            // Module system
            ModuleManager.init();
            registerModules();
            ModuleManager.getInstance().initAll();

            ConsoleLogger.success("[Lifecycle] Common setup complete.");
        });
    }

    /**
     * ServerStartingEvent — финальная инициализация после старта сервера.
     */
    private void onServerStarting(ServerStartingEvent event) {
        ConsoleLogger.info("[Lifecycle] Server starting...");

        // Power management
        PowerManager.init(event.getServer());

        // Vanish
        VanishManager.init();

        // Reactor — нужно для broadcast (ссылка на server)
        ReactorManager.init(event.getServer());

        // Banner
        printBanner();

        // Mark startup complete
        ConsoleLogger.info("");
        ConsoleLogger.success("[MOD] Gameplay Additions v" + BuildInfo.VERSION + " enabled!");
        ConsoleLogger.info("");
    }

    /**
     * ServerStoppingEvent — сохранение и остановка.
     */
    private void onServerStopping(ServerStoppingEvent event) {
        ConsoleLogger.info("[Lifecycle] Server stopping, shutting down modules...");
        ModuleManager.getInstance().shutdownAll();
        ConsoleLogger.info("[MOD] Disabled.");
    }

    // ==========================================================================
    // MODULE REGISTRATION
    // ==========================================================================

    private void registerModules() {
        var mm = ModuleManager.getInstance();

        // System modules
        mm.register(new DatabaseModule());

        // Core module (commands)
        mm.register(new CoreModule());

        // Energy module (cables + reactor)
        mm.register(new EnergyModule());

        ConsoleLogger.info("[Init] Registered " + mm.getModules().size() + " modules.");
    }

    // ==========================================================================
    // BANNER
    // ==========================================================================

    private void printBanner() {
        ConsoleLogger.raw(" __  __  _____      _____  _     _    _  _____ _____ _   _ ");
        ConsoleLogger.raw("|  \\/  |/ ____|    |  __ \\| |   | |  | |/ ____|_   _| \\ | |");
        ConsoleLogger.raw("| \\  / | |   ______| |__) | |   | |  | | |  __  | | |  \\| |");
        ConsoleLogger.raw("| |\\/| | |  |______|  ___/| |   | |  | | | |_ | | | | . ` |");
        ConsoleLogger.raw("| |  | | |____     | |    | |___| |__| | |__| |_| |_| |\\  |");
        ConsoleLogger.raw("|_|  |_|\\_____|    |_|    |______\\____/ \\_____|_____|_| \\_|");
        ConsoleLogger.raw("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        ConsoleLogger.raw("  Version: " + BuildInfo.VERSION + "  |  NeoForge");
        ConsoleLogger.raw("  Authors: rizer001");
        ConsoleLogger.raw("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ==========================================================================
    // INSTANCE
    // ==========================================================================

    public static GameplayAdditionsMod getInstance() {
        return instance;
    }

    public Path getModConfigDir() {
        return modConfigDir;
    }
}
