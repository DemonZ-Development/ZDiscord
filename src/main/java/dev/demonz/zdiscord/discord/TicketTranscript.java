package dev.demonz.zdiscord.discord;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TicketTranscript implements AutoCloseable {
    private final Path directory;
    private final RandomAccessFile spool;
    private final List<Long> offsets = new ArrayList<>();
    private final List<Path> parts = new ArrayList<>();
    private final String name;
    private boolean closed;

    public TicketTranscript(String name) throws IOException {
        this.name = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        directory = Files.createTempDirectory("zdiscord-transcript-");
        spool = new RandomAccessFile(directory.resolve("history.bin").toFile(), "rw");
    }

    public synchronized void append(String entry) throws IOException {
        if (closed) throw new IOException("Transcript is closed");
        byte[] bytes = entry.getBytes(StandardCharsets.UTF_8);
        offsets.add(spool.getFilePointer());
        spool.writeInt(bytes.length);
        spool.write(bytes);
    }

    public synchronized int size() { return offsets.size(); }

    public synchronized List<Path> finish(long maxBytes) throws IOException {
        if (closed) throw new IOException("Transcript is closed");
        byte[] header = ("# Ticket Transcript - " + name + "\n\n").getBytes(StandardCharsets.UTF_8);
        if (maxBytes <= header.length) throw new IOException("Upload size limit is too small");
        OutputStream output = null;
        long used = 0;
        try {
            for (int i = offsets.size() - 1; i >= 0; i--) {
                spool.seek(offsets.get(i));
                byte[] entry = new byte[spool.readInt()];
                spool.readFully(entry);
                if (header.length + (long) entry.length > maxBytes)
                    throw new IOException("A transcript entry exceeds the upload size limit");
                if (output == null || used + entry.length > maxBytes) {
                    if (output != null) output.close();
                    Path part = directory.resolve("transcript-" + name + "-" + (parts.size() + 1) + ".md");
                    parts.add(part);
                    output = Files.newOutputStream(part);
                    output.write(header);
                    used = header.length;
                }
                output.write(entry);
                used += entry.length;
            }
        } finally { if (output != null) output.close(); }
        return List.copyOf(parts);
    }

    public static String format(Message message) {
        StringBuilder text = new StringBuilder();
        text.append("**").append(message.getAuthor().getEffectiveName()).append("** (`")
                .append(message.getAuthor().getId()).append("`, ")
                .append(message.getTimeCreated().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .append(", message `").append(message.getId()).append("`):\n")
                .append(message.getContentDisplay()).append('\n');
        if (message.isEdited()) text.append("[Edited]\n");
        var reference = message.getMessageReference();
        if (reference != null) text.append("Reply to message: ").append(reference.getMessageId()).append('\n');
        for (var attachment : message.getAttachments())
            text.append("Attachment: ").append(attachment.getFileName()).append(" — ")
                    .append(attachment.getUrl()).append('\n');
        for (var sticker : message.getStickers())
            text.append("Sticker: ").append(sticker.getName()).append(" — ").append(sticker.getIconUrl()).append('\n');
        for (MessageEmbed embed : message.getEmbeds()) {
            if (embed.getTitle() != null) text.append("Embed: ").append(embed.getTitle()).append('\n');
            if (embed.getDescription() != null) text.append(embed.getDescription()).append('\n');
            if (embed.getUrl() != null) text.append(embed.getUrl()).append('\n');
            for (var field : embed.getFields())
                text.append(field.getName()).append(": ").append(field.getValue()).append('\n');
            if (embed.getImage() != null) text.append("Image: ").append(embed.getImage().getUrl()).append('\n');
            if (embed.getThumbnail() != null) text.append("Thumbnail: ").append(embed.getThumbnail().getUrl()).append('\n');
        }
        return text.append('\n').toString();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { spool.close(); }
        finally {
            try (var paths = Files.list(directory)) {
                for (Path path : paths.toList()) Files.deleteIfExists(path);
            } finally { Files.deleteIfExists(directory); }
        }
    }
}
