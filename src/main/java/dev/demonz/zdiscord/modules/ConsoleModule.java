package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;


public class ConsoleModule {

    private static final long FLUSH_INTERVAL_TICKS = 40L;
    private static final int MAX_BATCH_CHARS = 1900;
    private static final int MAX_LINE_CHARS = 200;
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(password|token|secret|key|credential|auth)[=: ]+\\S+");

    private final ZDiscord plugin;
    private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();
    private ConsoleHandler handler;
    private Logger registeredLogger;
    private volatile boolean running = true;

    public ConsoleModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String channelId = plugin.getConfigManager().getString("channels.console", "");
        if (channelId.isEmpty() || channelId.startsWith("YOUR_")) {
            return;
        }

        handler = new ConsoleHandler();
        registeredLogger = plugin.getServer().getLogger();
        if (registeredLogger != null) {

            for (Handler h : registeredLogger.getHandlers()) {
                if (h instanceof ConsoleHandler) {
                    registeredLogger.removeHandler(h);
                }
            }
            registeredLogger.addHandler(handler);
        }

        plugin.getPlatformAdapter().runAsyncTimer(this::flushBuffer, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
        plugin.debug("Console output streaming enabled.");
    }

    private void flushBuffer() {
        if (!running) return;
        if (buffer.isEmpty()) {
            return;
        }
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.console");
        if (channel == null) {
            buffer.clear();
            return;
        }

        StringBuilder batch = new StringBuilder();
        String line;
        while ((line = buffer.poll()) != null) {
            if (line.length() > MAX_LINE_CHARS) {
                line = line.substring(0, MAX_LINE_CHARS - 3) + "...";
            }
            if (batch.length() + line.length() + 1 > MAX_BATCH_CHARS) {
                sendBatch(channel, batch.toString());
                batch = new StringBuilder();
            }
            batch.append(line).append('\n');
        }
        if (batch.length() > 0) {
            sendBatch(channel, batch.toString());
        }
    }

    private void sendBatch(TextChannel channel, String content) {
        channel.sendMessage("```\n" + content + "```").queue(
                s -> { },
                err -> plugin.debug("Console batch failed: " + err.getMessage()));
    }

    public void reload() {
    }

    public void shutdown() {
        running = false;
        if (handler != null && registeredLogger != null) {
            registeredLogger.removeHandler(handler);
        }
        flushBuffer();
    }

    private final class ConsoleHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            if (record == null || record.getMessage() == null) {
                return;
            }
            String loggerName = record.getLoggerName();
            if (loggerName != null && loggerName.contains("ZDiscord")) {
                return;
            }
            String msg = "[" + record.getLevel().getName() + "] " + record.getMessage();
            msg = ColorUtil.stripColor(msg);
            if (SENSITIVE_PATTERN.matcher(msg).find()) {
                return;
            }
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
