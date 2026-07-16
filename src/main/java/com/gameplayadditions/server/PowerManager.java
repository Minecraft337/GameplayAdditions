package com.gameplayadditions.server;

import com.gameplayadditions.util.ConsoleLogger;
import com.gameplayadditions.util.MessageUtil;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * PowerManager — управление выключением/перезагрузкой сервера.
 * <p>
 * Порт {@code com.mcplugin.command.PowerManager} из MC-Plugin.
 * Двухэтапное подтверждение: запрос → подтверждение (только консоль) → отсчёт → shutdown/restart.
 */
public class PowerManager {

    private static PowerManager instance;
    private static MinecraftServer server;

    public enum RequestType { STOP, RESTART }

    private RequestType currentRequest;
    private UUID requesterUuid;
    private String requesterName;

    // ── Вложенные хендлеры (можно зарегистрировать/разрегистрировать) ──
    private TimeoutHandler timeoutHandler;
    private CountdownHandler countdownHandler;

    // ── Настройки ──
    private int countdownDuration = 10;
    private boolean bossbarEnabled = true;
    private boolean actionbarEnabled = true;
    private boolean soundEnabled = true;
    private float soundPitchBase = 1.2f;
    private float soundPitchMax = 2.0f;
    private int requestTimeoutTicks = 30 * 20;

    private String bossbarText = "&c\u26A1 Server {action} in &e{seconds} &csec";
    private String actionbarFormat = "&c\u26A1 Server {action} in &e{seconds} &cseconds";

    // ══════════════════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════════════════

    public static void init(MinecraftServer srv) {
        instance = new PowerManager();
        server = srv;
        ConsoleLogger.info("[Power] Manager initialized.");
    }

    public static PowerManager getInstance() { return instance; }

    // ══════════════════════════════════════════════════════════════════════════
    // REQUESTS
    // ══════════════════════════════════════════════════════════════════════════

    public boolean hasPendingRequest() { return currentRequest != null; }
    public RequestType getCurrentRequestType() { return currentRequest; }
    public String getRequesterName() { return requesterName; }
    public UUID getRequesterUuid() { return requesterUuid; }

    public void requestStop(String playerName, UUID playerUuid) {
        this.currentRequest = RequestType.STOP;
        this.requesterName = playerName;
        this.requesterUuid = playerUuid;
        startTimeout();
    }

    public void requestRestart(String playerName, UUID playerUuid) {
        this.currentRequest = RequestType.RESTART;
        this.requesterName = playerName;
        this.requesterUuid = playerUuid;
        startTimeout();
    }

    private void startTimeout() {
        timeoutHandler = new TimeoutHandler(requestTimeoutTicks);
        NeoForge.EVENT_BUS.register(timeoutHandler);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONFIRM
    // ══════════════════════════════════════════════════════════════════════════

    public boolean confirmRequest() {
        if (currentRequest == null) return false;

        // Отменяем таймаут
        if (timeoutHandler != null) {
            NeoForge.EVENT_BUS.unregister(timeoutHandler);
            timeoutHandler = null;
        }

        RequestType type = currentRequest;
        currentRequest = null;
        requesterName = null;
        requesterUuid = null;

        String action = type == RequestType.STOP ? "shutting down" : "restarting";
        String actionTitle = type == RequestType.STOP ? "Shutdown" : "Restart";

        // BossBar
        ServerBossEvent bossBar = null;
        if (bossbarEnabled) {
            bossBar = new ServerBossEvent(
                    MessageUtil.legacy(bossbarText.replace("{action}", action).replace("{seconds}", String.valueOf(countdownDuration))),
                    BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            for (var player : server.getPlayerList().getPlayers()) {
                bossBar.addPlayer(player);
            }
        }

        broadcast("&8[&c\u26A0&8] &c" + actionTitle + " in &f" + countdownDuration + " &cseconds!");

        // Запускаем отсчёт
        countdownHandler = new CountdownHandler(type, action, actionTitle, countdownDuration, bossBar);
        NeoForge.EVENT_BUS.register(countdownHandler);

        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UNDO / CANCEL
    // ══════════════════════════════════════════════════════════════════════════

    public String undoRequest(String cancelerName) {
        if (currentRequest == null) return null;

        RequestType type = currentRequest;
        String requester = requesterName;
        UUID requesterId = requesterUuid;

        currentRequest = null;
        requesterName = null;
        requesterUuid = null;

        if (timeoutHandler != null) {
            NeoForge.EVENT_BUS.unregister(timeoutHandler);
            timeoutHandler = null;
        }

        String action = type == RequestType.STOP ? "Shutdown" : "Restart";

        if (requesterId != null) {
            var player = server.getPlayerList().getPlayer(requesterId);
            if (player != null) {
                player.sendSystemMessage(MessageUtil.legacy(
                        "&8[&c\u26A0&8] &cServer " + action + " was cancelled" +
                        (cancelerName != null && !cancelerName.equalsIgnoreCase(requester) ? " by " + cancelerName : "") + "."));
            }
        }

        ConsoleLogger.info("[Power] Server " + action + " cancelled" +
                (cancelerName != null ? " (" + cancelerName + ")" : "") +
                (requester != null ? ". Request from " + requester : "") + ".");
        return action;
    }

    public void cancelRequest(String reason) {
        if (currentRequest == null) return;

        RequestType type = currentRequest;
        String requester = requesterName;
        UUID requesterId = requesterUuid;

        currentRequest = null;
        requesterName = null;
        requesterUuid = null;

        if (timeoutHandler != null) {
            NeoForge.EVENT_BUS.unregister(timeoutHandler);
            timeoutHandler = null;
        }

        String action = type == RequestType.STOP ? "Shutdown" : "Restart";

        if (requesterId != null) {
            var player = server.getPlayerList().getPlayer(requesterId);
            if (player != null) {
                player.sendSystemMessage(MessageUtil.legacy(
                        "&8[&c\u26A0&8] &cServer " + action + " auto-cancelled: " + reason));
            }
        }

        ConsoleLogger.info("[Power] Server " + action + " cancelled" +
                (requester != null ? " (request from " + requester + ")" : "") + ": " + reason);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIRECT EXECUTION
    // ══════════════════════════════════════════════════════════════════════════

    public void executeDirect(boolean isRestart) {
        broadcast("&8[&c\u26A0&8] &cServer " + (isRestart ? "restarting" : "shutting down") + " (console)...");

        NeoForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onTick(ServerTickEvent.Post event) {
                server.close();
                if (isRestart) {
                    ConsoleLogger.info("[Power] Server halted for restart. Use a wrapper script to auto-restart.");
                }
                NeoForge.EVENT_BUS.unregister(this);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void broadcast(String msg) {
        var component = MessageUtil.legacy(msg);
        for (var player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
        ConsoleLogger.info(msg.replace("&", "\u00A7"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ⏱ ТАЙМАУТ (ожидание подтверждения)
    // ══════════════════════════════════════════════════════════════════════════

    private class TimeoutHandler {
        int ticks;

        TimeoutHandler(int ticks) {
            this.ticks = ticks;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            ticks--;
            if (ticks <= 0 && currentRequest != null) {
                cancelRequest("Timed out waiting for confirmation");
                // cancelRequest() already unregisters this handler
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ⏱ ОТСЧЁТ (после подтверждения)
    // ══════════════════════════════════════════════════════════════════════════

    private class CountdownHandler {
        final RequestType type;
        final String action;
        final String actionTitle;
        final int totalTicks;
        final ServerBossEvent bossBar;

        int tick = 0;
        int lastDisplaySecond = -1;
        int beepCounter = 0;

        CountdownHandler(RequestType type, String action, String actionTitle, int durationSec, ServerBossEvent bossBar) {
            this.type = type;
            this.action = action;
            this.actionTitle = actionTitle;
            this.totalTicks = durationSec * 20;
            this.bossBar = bossBar;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            int currentSecond = (totalTicks - tick) / 20;

            // Время вышло — выполняем
            if (currentSecond < 0) {
                executeAction();
                NeoForge.EVENT_BUS.unregister(this);
                return;
            }

            // Обновления раз в секунду
            if (currentSecond != lastDisplaySecond) {
                lastDisplaySecond = currentSecond;

                if (currentSecond <= 5 && currentSecond > 0) {
                    broadcast("&cServer " + action + " in &f" + currentSecond +
                            " &csecond" + (currentSecond != 1 ? "s" : "") + "...");
                }

                // ActionBar
                if (actionbarEnabled) {
                    var packet = new ClientboundSetActionBarTextPacket(MessageUtil.legacy(
                            actionbarFormat.replace("{action}", action).replace("{seconds}", String.valueOf(currentSecond))));
                    for (var player : server.getPlayerList().getPlayers()) {
                        player.connection.send(packet);
                    }
                }

                // BossBar title
                if (bossBar != null) {
                    bossBar.setName(MessageUtil.legacy(
                            bossbarText.replace("{action}", action).replace("{seconds}", String.valueOf(currentSecond))));
                }
            }

            // BossBar progress
            if (bossBar != null) {
                double progress = (double) (totalTicks - tick) / totalTicks;
                bossBar.setProgress((float) Math.max(0.0, Math.min(1.0, progress)));
                for (var player : server.getPlayerList().getPlayers()) {
                    if (!bossBar.getPlayers().contains(player)) {
                        bossBar.addPlayer(player);
                    }
                }
            }

            // Звуки
            if (soundEnabled) {
                double progress = (double) tick / totalTicks;
                int interval = Math.max(4, (int) (20 - 16 * Math.min(1.0, progress)));
                if (beepCounter >= interval) {
                    float pitch = (float) (soundPitchBase + (soundPitchMax - soundPitchBase) * Math.min(1.0, progress));
                    playBeepToAll(pitch);
                    beepCounter = 0;
                }
                beepCounter++;
            }

            tick++;
        }

        private void executeAction() {
            // Чистим BossBar
            if (bossBar != null) {
                bossBar.removeAllPlayers();
            }

            broadcast("&8[&c\u26A0&8] &cServer " + actionTitle.toLowerCase() + "...");

            // Запланировать выполнение на Post-тик
            NeoForge.EVENT_BUS.register(new Object() {
                @SubscribeEvent
                public void onPostTick(ServerTickEvent.Post event) {
                    server.close();
                    if (type == RequestType.RESTART) {
                        ConsoleLogger.info("[Power] Server halted for restart. Use a wrapper script to auto-restart.");
                    }
                    NeoForge.EVENT_BUS.unregister(this);
                }
            });
        }
    }

    private void playBeepToAll(float pitch) {
        // TODO: add sound effect when SoundUtil is ported
    }
}
