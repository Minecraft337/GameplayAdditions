package com.gameplayadditions.listener;

import com.gameplayadditions.GameplayAdditionsMod;
import com.gameplayadditions.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat filter — защита от нецензурной лексики в чате.
 * Портирован из MC-Plugin.
 */
public class ChatFilterManager {

    private static ChatFilterManager instance;
    private boolean enabled;
    private List<Pattern> compiledPatterns;
    private List<String> patternSources;
    private List<String> highlightWords;

    public ChatFilterManager() {
        instance = this;
        reloadConfig();
    }

    public static void reloadConfigStatic() {
        if (instance != null) instance.reloadConfig();
    }

    public void reloadConfig() {
        enabled = true;
        compiledPatterns = new ArrayList<>();
        patternSources = new ArrayList<>();
        highlightWords = new ArrayList<>();

        List<String> rawWords = List.of("badword", "swear");
        for (String raw : rawWords) {
            Pattern p = compileWordPattern(raw);
            if (p != null) {
                compiledPatterns.add(p);
                patternSources.add("word: " + raw);
                String clean = raw.trim().replace("*", "");
                if (!clean.isEmpty()) highlightWords.add(clean);
            }
        }
        ConsoleLogger.info("[CHAT-FILTER] Loaded " + compiledPatterns.size() + " pattern(s).");
    }

    private Pattern compileWordPattern(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String word = raw.trim();
        boolean startsWithStar = word.startsWith("*");
        boolean endsWithStar = word.endsWith("*");
        if (startsWithStar) word = word.substring(1);
        if (endsWithStar) word = word.substring(0, word.length() - (endsWithStar ? 1 : 0));
        if (word.isEmpty()) return null;
        String escaped = Pattern.quote(word);
        StringBuilder regex = new StringBuilder("(?i).*");
        if (!startsWithStar) regex.append("(?<!\\p{L})");
        regex.append(escaped);
        if (!endsWithStar) regex.append("(?!\\p{L})");
        regex.append(".*");
        try { return Pattern.compile(regex.toString()); }
        catch (PatternSyntaxException e) { return null; }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!enabled) return;
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString();

        for (int i = 0; i < compiledPatterns.size(); i++) {
            if (compiledPatterns.get(i).matcher(message).matches()) {
                event.setCanceled(true);
                ConsoleLogger.warn("[CHAT-FILTER] " + player.getName().getString() + " violated rule: " + patternSources.get(i));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYour message was blocked by chat filter."));
                return;
            }
        }
    }
}
