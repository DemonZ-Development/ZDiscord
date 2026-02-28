package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Streams server console output to a Discord channel.
 * Batches log lines every 2 seconds to avoid rate-limiting.
 */
public class ConsoleModule {

    private final ZDiscord plugin;
    private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();
    private ConsoleHandler handler;

    public ConsoleModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String channelId = plugin.getConfigManager().getString("channels.console", "");
        if (channelId == null || channelId.isEmpty() || channelId.startsWith("YOUR_"))
            return;

        // Attach a log handler to the server's root logger
        handler = new ConsoleHandler();
        org.bukkit.Bukkit.getLogger().addHandler(handler);

        // Also attach to the server logger parent
        java.util.logging.Logger serverLogger = plugin.getServer().getLogger();
        if (serverLogger != null) {
            serverLogger.addHandler(handler);
        }

        // Flush buffer every 2 seconds
        plugin.getPlatformAdapter().runAsyncTimer(this::flushBuffer, 40L, 40L);

        plugin.debug("Console output streaming enabled.");
    }

    private void flushBuffer() {
        if (buffer.isEmpty())
            return;

        TextChannel channel = plugin.getBotManager().getTextChannel("channels.console");
        if (channel == null)
            return;

        StringBuilder batch = new StringBuilder();
        String line;
        while ((line = buffer.poll()) != null) {
            // Truncate very long lines
            if (line.length() > 200) {
                line = line.substring(0, 197) + "...";
            }
            if (batch.length() + line.length() + 1 > 1900) {
                // Send current batch and start new one
                sendBatch(channel, batch.toString());
                batch = new StringBuilder();
            }
            batch.append(line).append("\n");
        }

        if (batch.length() > 0) {
            sendBatch(channel, batch.toString());
        }
    }

    private void sendBatch(TextChannel channel, String content) {
        channel.sendMessage("```\n" + content + "```").queue(
                s -> {
                },
                err -> plugin.debug("Console batch failed: " + err.getMessage()));
    }

    public void reload() {
        // Nothing specific to reload — channel is read on each flush
    }

    public void shutdown() {
        // Remove the log handler
        if (handler != null) {
            org.bukkit.Bukkit.getLogger().removeHandler(handler);
            java.util.logging.Logger serverLogger = plugin.getServer().getLogger();
            if (serverLogger != null) {
                serverLogger.removeHandler(handler);
            }
        }

        // Flush remaining buffer
        flushBuffer();
    }

    /**
     * Custom log handler that captures console output into our buffer.
     */
    private class ConsoleHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            if (record == null || record.getMessage() == null)
                return;

            // Skip our own messages to avoid loops
            String loggerName = record.getLoggerName();
            if (loggerName != null && loggerName.contains("ZDiscord"))
                return;

            String msg = String.format("[%s] %s",
                    record.getLevel().getName(),
                    record.getMessage());

            // Strip color codes
            msg = msg.replaceAll("§[0-9a-fk-or]", "");
            msg = msg.replaceAll("\u001B\\[[;\\d]*m", "");

            buffer.add(msg);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
