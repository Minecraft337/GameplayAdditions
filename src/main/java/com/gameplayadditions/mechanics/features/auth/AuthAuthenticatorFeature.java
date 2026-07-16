package com.gameplayadditions.mechanics.features.auth;

import com.gameplayadditions.core.AbstractFeature;
import com.gameplayadditions.core.FeatureRegistry;
import com.gameplayadditions.util.ConsoleLogger;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthAuthenticatorFeature — порт {@code com.mcplugin.mechanics.security.auth.AuthAuthenticator} из MC-Plugin.
 * <p>
 * Оркестратор аутентификации: команды /login, /register, /changepassword, /logout,
 * freeze/unfreeze игроков, 2FA вызов, обработка неудачных попыток.
 * <p>
 * UI был Anvil в Bukkit — портирован как чат-интерфейс (TODO: кастомный {@code ContainerAnvil}).
 */
public class AuthAuthenticatorFeature extends AbstractFeature {

    // ─── Ссылки на другие Auth фичи (резолвятся в onServerStart) ─────────
    private AuthDatabaseFeature database;
    private Auth2FAFeature twoFA;
    private AuthFeature authState;

    // ─── Freeze state: оригинальный GameMode/abilities каждого игрока ─────
    private final Map<UUID, SavedPlayerState> savedStates = new ConcurrentHashMap<>();

    public AuthAuthenticatorFeature() {
    }

    @Override
    public String getName() {
        return "AuthAuthenticator";
    }

    @Override
    public void setup(FMLCommonSetupEvent event) {
        registerGameEvents();
    }

    @Override
    public void onServerStart(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        super.onServerStart(event);
        this.database = FeatureRegistry.get(AuthDatabaseFeature.class);
        this.twoFA = FeatureRegistry.get(Auth2FAFeature.class);
        this.authState = FeatureRegistry.get(AuthFeature.class);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  JOIN — freeze + open auth prompt
    // ══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (authState == null || !authState.isEnabled()) return;
        if (database == null) return;

        UUID uuid = player.getUUID();
        boolean registered = database.isRegistered(uuid);

        // IP auto-session check
        String ip = playerIp(player);
        if (registered && authState.isIpCheckEnabled()) {
            int sameIpCount = database.countAccountsByIp(ip);
            if (sameIpCount > authState.getMaxAccountsPerIp()) {
                player.connection.disconnect(
                        Component.literal("§cС вашего IP уже зарегистрировано максимум аккаунтов!"));
                return;
            }
            if (database.hasValidSession(uuid, ip,
                    authState.getSessionDurationMinutes(), true)) {
                authState.setAuthenticated(player);
                player.sendSystemMessage(Component.literal(
                        ChatFormatting.GREEN + "Добро пожаловать! Сессия активирована автоматически (по IP)."));
                return;
            }
        }

        // Freeze + prompt
        freezePlayer(player);
        String prompt = registered
                ? "§aЗарегистрированы. §eВведите §f/login <пароль>§e."
                : "§aНе зарегистрированы. §eВведите §f/register <пароль> <пароль>§e или просто §f/register <пароль>§e.";
        player.sendSystemMessage(Component.literal(prompt));
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        savedStates.remove(player.getUUID());
        if (twoFA != null) twoFA.clearPending(player.getUUID());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COMMANDS — /login, /register, /changepassword, /logout
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Подключаем NeoForge-команды.
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        // /login <password>
        dispatcher.register(
                Commands.literal("login")
                        .then(Commands.argument("password", StringArgumentType.string())
                                .executes(ctx -> handleLogin(ctx)))
        );

        // /register <password> [<passwordConfirm>]
        dispatcher.register(
                Commands.literal("register")
                        .then(Commands.argument("password", StringArgumentType.string())
                                .executes(ctx -> handleRegister(ctx, false))
                                .then(Commands.argument("passwordConfirm", StringArgumentType.string())
                                        .executes(ctx -> handleRegister(ctx, true))))
        );

        // /changepassword <oldpassword> <newpassword>
        dispatcher.register(
                Commands.literal("changepassword")
                        .then(Commands.argument("oldPassword", StringArgumentType.string())
                                .then(Commands.argument("newPassword", StringArgumentType.string())
                                        .executes(ctx -> handleChangePassword(ctx))))
        );

        // /logout
        dispatcher.register(
                Commands.literal("logout")
                        .executes(ctx -> handleLogout(ctx))
        );

        ConsoleLogger.info("[Auth] Commands registered: /login, /register, /changepassword, /logout.");
    }

    private int handleLogin(CommandContext<CommandSourceStack> ctx) {
        if (authState == null || database == null) return 0;
        ServerPlayer player = sourceAsPlayer(ctx.getSource());
        if (player == null) return 0;
        if (!authState.needsAuth(player)) {
            send(player, "§cВы уже авторизованы.");
            return 0;
        }
        if (!authState.checkCooldown(player)) return 0;
        if (!database.isRegistered(player.getUUID())) {
            send(player, "§cВы не зарегистрированы. Используйте §f/register <пароль>");
            return 0;
        }

        String password = StringArgumentType.getString(ctx, "password");
        if (password.length() < authState.getMinPasswordLength()) {
            send(player, "§cПароль слишком короткий (мин. " + authState.getMinPasswordLength() + ")");
            return 0;
        }

        database.verifyPasswordAsync(player.getUUID(), password.toCharArray())
                .thenAccept(ok -> runOnServerThread(player, () -> {
                    if (ok) onAuthSuccess(player);
                    else onAuthFailure(player);
                }));
        send(player, "§7Проверка пароля…");
        return 1;
    }

    private int handleRegister(CommandContext<CommandSourceStack> ctx, boolean withConfirm) {
        if (authState == null || database == null) return 0;
        ServerPlayer player = sourceAsPlayer(ctx.getSource());
        if (player == null) return 0;
        if (!authState.needsAuth(player)) {
            send(player, "§cВы уже авторизованы.");
            return 0;
        }
        if (!authState.checkCooldown(player)) return 0;
        if (database.isRegistered(player.getUUID())) {
            send(player, "§cВы уже зарегистрированы. Используйте §f/login <пароль>");
            return 0;
        }

        String password = StringArgumentType.getString(ctx, "password");
        String confirm = withConfirm ? StringArgumentType.getString(ctx, "passwordConfirm") : password;
        if (!password.equals(confirm)) {
            send(player, "§cПароли не совпадают.");
            return 0;
        }
        if (password.length() < authState.getMinPasswordLength()) {
            send(player, "§cПароль слишком короткий (мин. " + authState.getMinPasswordLength() + ")");
            return 0;
        }
        if (password.length() > authState.getMaxPasswordLength()) {
            send(player, "§cПароль слишком длинный (макс. " + authState.getMaxPasswordLength() + ")");
            return 0;
        }

        database.hashPasswordAsync(password.toCharArray())
                .thenAccept(hashed -> runOnServerThread(player, () -> {
                    if (hashed == null) {
                        send(player, "§cОшибка хеширования. Попробуйте позже.");
                        return;
                    }
                    String ip = playerIp(player);
                    if (database.register(player.getUUID(), hashed, ip)) {
                        authState.setAuthenticated(player);
                        unfreezePlayer(player);
                        send(player, "§aРегистрация успешна! Вы авторизованы.");
                    } else {
                        send(player, "§cОшибка регистрации в БД.");
                    }
                }));
        send(player, "§7Регистрация…");
        return 1;
    }

    private int handleChangePassword(CommandContext<CommandSourceStack> ctx) {
        if (authState == null || database == null) return 0;
        ServerPlayer player = sourceAsPlayer(ctx.getSource());
        if (player == null) return 0;
        if (!authState.isAuthenticated(player)) {
            send(player, "§cСначала авторизуйтесь.");
            return 0;
        }
        if (!authState.checkCooldown(player)) return 0;

        String oldPw = StringArgumentType.getString(ctx, "oldPassword");
        String newPw = StringArgumentType.getString(ctx, "newPassword");
        if (newPw.length() < authState.getMinPasswordLength()
                || newPw.length() > authState.getMaxPasswordLength()) {
            send(player, "§cНовый пароль должен быть от "
                    + authState.getMinPasswordLength() + " до "
                    + authState.getMaxPasswordLength() + " символов.");
            return 0;
        }

        database.verifyPasswordAsync(player.getUUID(), oldPw.toCharArray())
                .thenCompose(ok -> {
                    if (!ok) {
                        sendSafe(player, "§cСтарый пароль неверный.");
                        return java.util.concurrent.CompletableFuture.completedFuture(false);
                    }
                    return database.hashPasswordAsync(newPw.toCharArray())
                            .thenApply(hashed -> {
                                if (hashed == null) {
                                    sendSafe(player, "§cОшибка хеширования.");
                                    return false;
                                }
                                if (database.adminResetPassword(player.getUUID(), hashed)) {
                                    sendSafe(player, "§aПароль изменён.");
                                    return true;
                                }
                                sendSafe(player, "§cОшибка сохранения.");
                                return false;
                            });
                });
        send(player, "§7Смена пароля…");
        return 1;
    }

    private int handleLogout(CommandContext<CommandSourceStack> ctx) {
        if (authState == null || database == null) return 0;
        ServerPlayer player = sourceAsPlayer(ctx.getSource());
        if (player == null) return 0;
        if (!authState.isAuthenticated(player)) {
            send(player, "§cВы и так не авторизованы.");
            return 0;
        }
        authState.removePlayer(player.getUUID());
        freezePlayer(player);
        authState.setPendingAuth(player);
        database.updateLastLogin(player.getUUID()); // Save old session end implicitly
        send(player, "§eВы вышли из аккаунта. Введите §f/login <пароль>§e.");
        return 1;
    }

    /**
     * Запускает указанное действие в главном потоке сервера.
     * Используется, чтобы безопасно мутировать ServerPlayer из async-колбэков HASH_EXECUTOR.
     * <p>
     * Защищает от race: если игрок отключился к моменту выполнения runnable
     * (между окончанием хеша и тиком сервера), runnable просто пропускается — не NPE.
     */
    private static void runOnServerThread(ServerPlayer player, Runnable runnable) {
        if (player == null) {
            runnable.run();
            return;
        }
        Runnable safeRunnable = () -> {
            if (player.isRemoved() || player.connection == null) return;
            runnable.run();
        };
        var server = player.getServer();
        if (server != null) {
            server.execute(safeRunnable);
        } else {
            safeRunnable.run();
        }
    }

    /**
     * Безопасная отправка сообщения из любого потока.
     */
    private static void sendSafe(ServerPlayer player, String msg) {
        runOnServerThread(player, () -> send(player, msg));
    }

    private static ServerPlayer sourceAsPlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer p) return p;
        return null;
    }

    private static String playerIp(ServerPlayer player) {
        try {
            var addr = player.connection.getConnection().getRemoteAddress();
            return addr != null ? addr.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  AUTH SUCCESS / FAILURE
    // ══════════════════════════════════════════════════════════════════════

    private void onAuthSuccess(ServerPlayer player) {
        if (authState == null || database == null) return;
        UUID uuid = player.getUUID();
        String ip = playerIp(player);
        try {
            if (authState.isIpCheckEnabled()) {
                database.setStoredIp(uuid, ip);
            }
            database.updateLastLogin(uuid);
            authState.resetWrongAttempts(player);
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth] onAuthSuccess DB error: " + e.getMessage());
            send(player, "§cОшибка сохранения сессии.");
            return;
        }

        // 2FA challenge
        if (twoFA != null && twoFA.isEnabled(uuid)) {
            try {
                twoFA.sendConfirmation(player);
                send(player, "§e2FA: подтвердите вход через Telegram-бот (ждём до 5 минут)…");
            } catch (Exception e) {
                ConsoleLogger.warn("[Auth] 2FA send failed: " + e.getMessage());
                authState.setAuthenticated(player);
                unfreezePlayer(player);
                send(player, "§aАвторизация успешна! (2FA недоступно)");
            }
            // polling в onServerTickPost
        } else {
            authState.setAuthenticated(player);
            unfreezePlayer(player);
            send(player, "§aАвторизация успешна!");
        }
    }

    private void onAuthFailure(ServerPlayer player) {
        if (authState == null) return;
        int attempts = authState.incrementWrongAttempts(player);
        send(player, "§cНеверный пароль! Попытка " + attempts + "/"
                + authState.getMaxWrongAttempts() + ".");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2FA POLLING — каждый тик проверяем approved-статус
    // ══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (twoFA == null || authState == null) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (authState.isAuthenticated(player)) continue;

            Auth2FAFeature.ConfirmationStatus s = twoFA.checkConfirmation(uuid);
            if (s == Auth2FAFeature.ConfirmationStatus.APPROVED) {
                authState.setAuthenticated(player);
                unfreezePlayer(player);
                send(player, "§a2FA: подтверждено. Вы авторизованы!");
            } else if (s == Auth2FAFeature.ConfirmationStatus.DENIED) {
                authState.incrementWrongAttempts(player);
                send(player, "§c2FA: отклонено.");
            } else if (s == Auth2FAFeature.ConfirmationStatus.TIMEOUT) {
                authState.incrementWrongAttempts(player);
                send(player, "§c2FA: время истекло.");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FREEZE / UNFREEZE
    // ══════════════════════════════════════════════════════════════════════

    private void freezePlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // Сохранить оригинальное состояние (один раз)
        savedStates.computeIfAbsent(uuid, k -> new SavedPlayerState(
                player.gameMode.getGameModeForPlayer(),
                player.getAbilities().flying,
                player.getAbilities().mayfly,
                player.getAbilities().invulnerable,
                player.getAbilities().getWalkingSpeed(),
                player.getAbilities().getFlyingSpeed()
        ));
        player.setGameMode(GameType.ADVENTURE);
        var a = player.getAbilities();
        a.flying = false;
        a.mayfly = false;
        a.invulnerable = false;
        a.setWalkingSpeed(0f);
        a.setFlyingSpeed(0f);
        player.onUpdateAbilities();
    }

    private void unfreezePlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        SavedPlayerState s = savedStates.remove(uuid);
        if (s == null) return;
        player.setGameMode(s.gameMode());
        var a = player.getAbilities();
        a.flying = s.flying();
        a.mayfly = s.mayfly();
        a.invulnerable = s.invulnerable();
        a.setWalkingSpeed(s.walkingSpeed());
        a.setFlyingSpeed(s.flyingSpeed());
        player.onUpdateAbilities();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACTION BLOCKING — пока не авторизовался, /login и т.п. — единственное ОК
    // ══════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (authState == null) return;
        if (!(event.getParseResults().getContext().getSource()
                .getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (authState.isAuthenticated(player)) return;

        // Достаем имя команды строго из Brigadier AST — не позволяет
        // обойти блокировку через /loginxyz, /loginevil и т.п.
        var nodes = event.getParseResults().getContext().getNodes();
        if (nodes.isEmpty()) return;
        String cmdName = nodes.get(0).getNode().getName();

        // Разрешить только /login, /register, /logout, /changepassword, /keyauth
        if (!Set.of("login", "register", "logout", "changepassword", "keyauth").contains(cmdName)) {
            event.setCanceled(true);
            send(player, "§cСначала авторизуйтесь (§f/login§c или §f/register§c).");
        }
    }

    private static void send(ServerPlayer player, String msg) {
        player.sendSystemMessage(Component.literal(msg));
    }

    private record SavedPlayerState(GameType gameMode, boolean flying, boolean mayfly,
                                    boolean invulnerable, float walkingSpeed, float flyingSpeed) {
    }
}
