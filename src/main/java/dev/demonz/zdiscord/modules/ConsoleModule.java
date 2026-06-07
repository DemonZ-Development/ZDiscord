/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Streams server log output to a Discord channel. Lines are batched
 * every two seconds to stay within Discord rate limits. Only the
 * server's root logger is hooked; the Bukkit plugin logger is left
 * alone to avoid double-reporting.
 */
public class ConsoleModule {

    private static final long FLUSH_INTERVAL_TICKS = 40L;
    private static final int MAX_BATCH_CHARS = 1900;
    private static final int MAX_LINE_CHARS = 200;

    private final ZDiscord plugin;
    private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();
    private ConsoleHandler handler;
    private Logger registeredLogger;

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
            registeredLogger.addHandler(handler);
        }

        plugin.getPlatformAdapter().runAsyncTimer(this::flushBuffer, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
        plugin.debug("Console output streaming enabled.");
    }

    private void flushBuffer() {
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
