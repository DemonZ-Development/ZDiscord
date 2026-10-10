package dev.demonz.zdiscord.discord;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TicketTranscriptService {
    private TicketTranscriptService() { }

    public record Result(int messages, List<String> urls) { }

    public static CompletableFuture<Result> export(TextChannel channel, Executor executor) {
        final TicketTranscript transcript;
        try { transcript = new TicketTranscript(channel.getName()); }
        catch (IOException error) { return CompletableFuture.failedFuture(error); }
        String latest = channel.getLatestMessageId();
        long upper = latest == null ? Long.MAX_VALUE : Long.parseUnsignedLong(latest);
        try {
            return channel.getIterableHistory().cache(false).forEachAsync(message -> {
                if (Long.compareUnsigned(message.getIdLong(), upper) <= 0) {
                    try { transcript.append(TicketTranscript.format(message)); }
                    catch (IOException error) { throw new UncheckedIOException(error); }
                }
                return true;
            }).thenApplyAsync(ignored -> {
                try { return transcript.finish(Math.min(8_000_000L, channel.getGuild().getMaxFileSize())); }
                catch (IOException error) { throw new UncheckedIOException(error); }
            }, executor).thenCompose(parts -> {
                CompletableFuture<List<String>> uploads = CompletableFuture.completedFuture(new ArrayList<>());
                for (var part : parts) {
                    uploads = uploads.thenCompose(urls -> {
                        FileUpload upload = FileUpload.fromData(part);
                        try {
                            return channel.sendFiles(upload).submit().whenComplete((sent, error) -> {
                                try { upload.close(); } catch (IOException ignored) { }
                            }).thenApply(sent -> {
                                if (sent.getAttachments().isEmpty()) throw new IllegalStateException("Transcript upload has no attachment");
                                urls.add(sent.getAttachments().get(0).getUrl());
                                return urls;
                            });
                        } catch (RuntimeException error) {
                            try { upload.close(); } catch (IOException ignored) { }
                            return CompletableFuture.failedFuture(error);
                        }
                    });
                }
                return uploads.thenApply(urls -> new Result(transcript.size(), List.copyOf(urls)));
            }).whenComplete((result, error) -> {
                try { transcript.close(); } catch (IOException ignored) { }
            });
        } catch (RuntimeException error) {
            try { transcript.close(); } catch (IOException ignored) { }
            return CompletableFuture.failedFuture(error);
        }
    }
}
