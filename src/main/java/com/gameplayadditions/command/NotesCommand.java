package com.gameplayadditions.command;

import com.gameplayadditions.mechanics.notes.NotesDatabase;
import com.gameplayadditions.util.MessageUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * NotesCommand — просмотр и сохранение заметок через чат.
 * <p>
 * В MC-Plugin была сложная GUI-система с книгами.
 * Для NeoForge делаем упрощённую чат-версию:
 * <pre>
 * /ga notes            — список заметок
 * /ga notes <num>      — просмотр заметки
 * /ga notes <num> <text> — сохранить/обновить заметку
 * /ga notes delete <num> — удалить заметку
 * </pre>
 */
public class NotesCommand implements SubCommand {

    private static final int MAX_NOTES = 54;

    @Override
    public String getName() { return "notes"; }

    @Override
    public String getDescription() {
        return "View and manage your notes.";
    }

    @Override
    public String getUsage() {
        return "/ga notes [number] [content]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context, String[] args) {
        var source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(MessageUtil.legacy("&c❌ Only players can use notes!"));
            return 0;
        }

        UUID uuid = player.getUUID();

        // /ga notes
        if (args.length == 1) {
            return listNotes(player, uuid);
        }

        String sub = args[1].toLowerCase();

        // /ga notes delete <num>
        if (sub.equals("delete") && args.length >= 3) {
            return deleteNote(player, uuid, args[2]);
        }

        // /ga notes <num> [content]
        int noteNum;
        try {
            noteNum = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            source.sendFailure(MessageUtil.legacy("&c❌ Usage: &f" + getUsage()));
            return 0;
        }

        if (noteNum < 1 || noteNum > MAX_NOTES) {
            source.sendFailure(MessageUtil.legacy("&c❌ Note number must be 1-" + MAX_NOTES + "!"));
            return 0;
        }

        if (args.length >= 3) {
            // /ga notes <num> <content>
            StringBuilder content = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) content.append(" ");
                content.append(args[i]);
            }

            if (NotesDatabase.saveNote(uuid, noteNum, content.toString())) {
                source.sendSuccess(() -> MessageUtil.legacy("&a✔ &fNote #" + noteNum + " saved!"), false);
            } else {
                source.sendFailure(MessageUtil.legacy("&c❌ Cooldown! Wait 5 seconds between saves."));
            }
            return 1;
        }

        // /ga notes <num>
        return viewNote(player, uuid, noteNum);
    }

    private int listNotes(ServerPlayer player, UUID uuid) {
        var slots = NotesDatabase.getNoteSlots(uuid);

        player.sendSystemMessage(MessageUtil.legacy("&6═══════════════════════════════════"));
        player.sendSystemMessage(MessageUtil.legacy("&6  &e✦ &fYour Notes &7(" + slots.size() + "/" + MAX_NOTES + ")"));
        player.sendSystemMessage(MessageUtil.legacy("&6═══════════════════════════════════"));

        if (slots.isEmpty()) {
            player.sendSystemMessage(MessageUtil.legacy("&eℹ &fNo notes yet."));
            player.sendSystemMessage(MessageUtil.legacy("&7Use &e/ga notes <num> <text> &7to create one."));
        } else {
            for (int slot : slots) {
                String content = NotesDatabase.loadNote(uuid, slot);
                if (content != null) {
                    String preview = content.length() > 40 ? content.substring(0, 40) + "..." : content;
                    player.sendSystemMessage(MessageUtil.legacy(
                            "&7┃ &e#" + slot + " &7" + preview.replace("\n", " ")));
                }
            }
        }

        player.sendSystemMessage(MessageUtil.legacy("&6═══════════════════════════════════"));
        return 1;
    }

    private int viewNote(ServerPlayer player, UUID uuid, int noteNum) {
        String content = NotesDatabase.loadNote(uuid, noteNum);

        player.sendSystemMessage(MessageUtil.legacy("&6═══════════════════════════════════"));
        player.sendSystemMessage(MessageUtil.legacy("&6  &e✦ &fNote #" + noteNum));
        player.sendSystemMessage(MessageUtil.legacy("&6═══════════════════════════════════"));

        if (content == null || content.isEmpty()) {
            player.sendSystemMessage(MessageUtil.legacy("&8(empty)"));
        } else {
            // Разбиваем на строки для читаемости
            String[] lines = content.split("\n");
            for (String line : lines) {
                player.sendSystemMessage(MessageUtil.legacy("&7" + line));
            }
        }

        player.sendSystemMessage(MessageUtil.legacy("&6═══════════════════════════════════"));
        player.sendSystemMessage(MessageUtil.legacy("&7Edit: &e/ga notes " + noteNum + " <text>"));
        player.sendSystemMessage(MessageUtil.legacy("&7Delete: &e/ga notes delete " + noteNum));
        return 1;
    }

    private int deleteNote(ServerPlayer player, UUID uuid, String numStr) {
        int noteNum;
        try {
            noteNum = Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            player.sendSystemMessage(MessageUtil.legacy("&c❌ Invalid number!"));
            return 0;
        }

        NotesDatabase.saveNote(uuid, noteNum, "");
        player.sendSystemMessage(MessageUtil.legacy("&a✔ &fNote #" + noteNum + " deleted."));
        return 1;
    }
}
