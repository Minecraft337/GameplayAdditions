package com.gameplayadditions.mechanics.features.keyauth;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * {@code KeyAuthScreen} — кастомный модовый экран авторизации по ключу.
 * <p>
 * Состав:
 * <ul>
 *   <li>{@link EditBox} для пути к {@code .key} файлу.</li>
 *   <li>Кнопка «Сменить директорию» — фиксирует путь из EditBox на клиенте.</li>
 *   <li>Кнопка «Продолжить» — генерирует SecureRandom hex key (256-bit), пишет в файл, шлёт C2S.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class KeyAuthScreen extends Screen {

    private static final SecureRandom RNG = new SecureRandom();

    private final boolean alreadyRegistered;
    private EditBox pathEdit;
    private String currentPath;
    private String statusMessage = "";

    public KeyAuthScreen(boolean alreadyRegistered, String defaultPath) {
        super(Component.literal("KeyAuth — авторизация по ключу"));
        this.alreadyRegistered = alreadyRegistered;
        this.currentPath = defaultPath != null ? defaultPath : "/GameplayAdditions";
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;

        pathEdit = new EditBox(this.font, cx - 150, cy - 20, 300, 20,
                Component.literal("Path to .key file"));
        pathEdit.setValue(currentPath);
        pathEdit.setResponder(s -> currentPath = s);
        this.addRenderableWidget(pathEdit);

        // Кнопка «Сменить директорию» — фиксирует путь на клиенте.
        this.addRenderableWidget(Button.builder(
                Component.literal("Сменить директорию").withStyle(ChatFormatting.YELLOW),
                btn -> {
                    currentPath = pathEdit.getValue();
                    statusMessage = "§aДиректория обновлена: §f" + currentPath;
                })
                .bounds(cx - 150, cy + 10, 145, 20)
                .build());

        // Кнопка «Продолжить» — основной flow.
        this.addRenderableWidget(Button.builder(
                Component.literal(alreadyRegistered ? "Войти по ключу" : "Продолжить регистрацию")
                        .withStyle(ChatFormatting.GREEN),
                btn -> onContinue())
                .bounds(cx + 5, cy + 10, 145, 20)
                .build());

        // Close.
        this.addRenderableWidget(Button.builder(
                Component.literal("Закрыть").withStyle(ChatFormatting.RED),
                btn -> onClose())
                .bounds(cx - 60, cy + 40, 120, 20)
                .build());
    }

    private void onContinue() {
        try {
            String pathStr = pathEdit.getValue();
            if (pathStr == null || pathStr.isBlank()) {
                statusMessage = "§cПуть не указан.";
                return;
            }
            Path dir = Path.of(pathStr);
            Files.createDirectories(dir);

            // Получаем имя локального игрока.
            String playerName = net.minecraft.client.Minecraft.getInstance()
                    .getUser().getName().toLowerCase();
            Path file = dir.resolve(playerName + ".key");

            String key;
            if (alreadyRegistered) {
                // Зарегистрированный игрок: читаем существующий ключ.
                if (!Files.exists(file)) {
                    statusMessage = "§cКлюч не найден: §f" + file
                            + "§c. Перерегистрируйтесь через /keyauth logout на сервере.";
                    return;
                }
                key = Files.readString(file).trim();
                if (key.isEmpty()) {
                    statusMessage = "§cФайл ключа пустой: §f" + file;
                    return;
                }
                statusMessage = "§aКлюч прочитан. Отправляется на сервер…";
            } else {
                // Первый раз: генерируем и пишем.
                key = generateHexKey(64); // 64 hex chars = 256 bit
                Files.writeString(file, key);
                statusMessage = "§aФайл сохранён: §f" + file;
            }

            // UUID локального игрока (1.21: LocalPlayer.getUUID() возвращает UUID напрямую).
            UUID playerUuid = new UUID(0L, 0L);
            var localPlayer = net.minecraft.client.Minecraft.getInstance().player;
            if (localPlayer != null) {
                playerUuid = localPlayer.getUUID();
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new KeyAuthPayloads.LoginPayload(playerUuid, key));
        } catch (Exception e) {
            statusMessage = "§cОшибка: §f" + e.getMessage();
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gui, mouseX, mouseY, partialTicks);
        super.render(gui, mouseX, mouseY, partialTicks);

        int cx = this.width / 2;
        int cy = this.height / 2;

        Component[] lines = new Component[]{
                Component.literal("KeyAuth — авторизация через .key файл").withStyle(ChatFormatting.GOLD),
                Component.literal(alreadyRegistered
                        ? "Сохранённый ключ: путь ниже. Нажмите «Войти по ключу»."
                        : "Создаёт файл <ник>.key с уникальным ключом в выбранной директории."),
                Component.literal("Файл будет содержать: ed23... (256-bit hex).")
        };
        for (int i = 0; i < lines.length; i++) {
            gui.drawCenteredString(this.font, lines[i], cx, cy - 60 + i * 12, 0xFFFFFF);
        }

        if (statusMessage != null && !statusMessage.isEmpty()) {
            String[] rawLines = statusMessage.split("\n");
            for (int i = 0; i < rawLines.length; i++) {
                gui.drawCenteredString(this.font, Component.literal(rawLines[i]), cx, cy + 70 + i * 12, 0xFFFFFF);
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String generateHexKey(int hexChars) {
        byte[] bytes = new byte[hexChars / 2];
        RNG.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
