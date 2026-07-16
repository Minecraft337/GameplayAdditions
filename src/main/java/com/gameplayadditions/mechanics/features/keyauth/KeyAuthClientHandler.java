package com.gameplayadditions.mechanics.features.keyauth;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Клиентский мост KeyAuth:
 * <ul>
 *   <li>Регистрирует S2C OpenGuiPayload → открывает {@link KeyAuthScreen}.</li>
 *   <li>Аннотирован {@code @OnlyIn(Dist.CLIENT)} — компилируется только на клиенте.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "gameplayadditions",
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public class KeyAuthClientHandler {

    /**
     * Регистрация S2C-канала OpenGuiPayload на клиенте.
     * <p>
     * Mod bus вызывается на обеих сторонах; {@code playToClient} но клиент
     * фактически консумирует пакет только здесь (на сервере — пропускается).
     */
    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("gameplayadditions").playToClient(
                KeyAuthPayloads.OpenGuiPayload.TYPE,
                KeyAuthPayloads.OpenGuiPayload.STREAM_CODEC,
                new net.neoforged.neoforge.network.handling.IPayloadHandler<KeyAuthPayloads.OpenGuiPayload>() {
                    @Override
                    public void handle(KeyAuthPayloads.OpenGuiPayload payload,
                                       net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
                        ctx.enqueueWork(() -> openScreen(payload));
                    }
                }
        );
    }

    /**
     * Открывает GUI на клиенте — безопасный поток (Minecraft.execute).
     */
    public static void openScreen(KeyAuthPayloads.OpenGuiPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> client.setScreen(
                new KeyAuthScreen(payload.alreadyRegistered(), payload.defaultPath())));
    }
}
