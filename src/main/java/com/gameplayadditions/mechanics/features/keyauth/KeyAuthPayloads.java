package com.gameplayadditions.mechanics.features.keyauth;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Сетевой контракт KeyAuth:
 * <ul>
 *   <li>{@link OpenGuiPayload} (S2C) — сервер сообщает клиенту «открой KeyAuth GUI».</li>
 *   <li>{@link LoginPayload}   (C2S) — клиент отправляет содержимое своего .key файла.</li>
 * </ul>
 * Регистрируется из {@link KeyAuthFeature#register(net.neoforged.bus.api.IEventBus)}.
 */
public final class KeyAuthPayloads {

    public static final ResourceLocation OPEN_GUI_ID =
            ResourceLocation.fromNamespaceAndPath("gameplayadditions", "keyauth_open_gui");

    public static final ResourceLocation LOGIN_ID =
            ResourceLocation.fromNamespaceAndPath("gameplayadditions", "keyauth_login");

    private KeyAuthPayloads() {}

    // ─── S2C: открыть GUI ──────────────────────────────────────────────────
    public record OpenGuiPayload(boolean alreadyRegistered, String defaultPath) implements CustomPacketPayload {
        public static final Type<OpenGuiPayload> TYPE = new Type<>(OPEN_GUI_ID);

        public static final StreamCodec<FriendlyByteBuf, OpenGuiPayload> STREAM_CODEC =
                StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.BOOL, OpenGuiPayload::alreadyRegistered,
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, OpenGuiPayload::defaultPath,
                        OpenGuiPayload::new);

        @Override public Type<OpenGuiPayload> type() { return TYPE; }
    }

    // ─── C2S: логин с ключом ──────────────────────────────────────────────
    public record LoginPayload(UUID playerUuid, String key) implements CustomPacketPayload {
        public static final Type<LoginPayload> TYPE = new Type<>(LOGIN_ID);

        public static final StreamCodec<FriendlyByteBuf, LoginPayload> STREAM_CODEC =
                StreamCodec.composite(
                        UuidCodec.INSTANCE, LoginPayload::playerUuid,
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, LoginPayload::key,
                        LoginPayload::new);

        @Override public Type<LoginPayload> type() { return TYPE; }
    }

    /**
     * UUID codec, обёрнутый через {@link FriendlyByteBuf#writeUUID}/{@link FriendlyByteBuf#readUUID}.
     */
    private static final class UuidCodec implements StreamCodec<FriendlyByteBuf, UUID> {
        static final UuidCodec INSTANCE = new UuidCodec();

        @Override public UUID decode(FriendlyByteBuf buf) { return buf.readUUID(); }

        @Override public void encode(FriendlyByteBuf buf, UUID uuid) { buf.writeUUID(uuid); }
    }
}
