/*
 * Copyright 2024 DemonZ Development
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

package dev.demonz.zdiscord.util;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.Instant;

/**
 * Common Discord embed builders used across the plugin.
 */
public final class EmbedUtil {

    private static final Color ERROR = new Color(0xE74C3C);
    private static final Color SUCCESS = new Color(0x2ECC71);
    private static final Color INFO = new Color(0x3498DB);
    private static final String FOOTER = "ZDiscord";

    private EmbedUtil() {
    }

    public static EmbedBuilder simple(String title, String description, Color color) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setFooter(FOOTER)
                .setTimestamp(Instant.now());
    }

    public static EmbedBuilder error(String message) {
        return new EmbedBuilder()
                .setTitle("Error")
                .setDescription(message)
                .setColor(ERROR)
                .setTimestamp(Instant.now());
    }

    public static EmbedBuilder success(String message) {
        return new EmbedBuilder()
                .setTitle("Success")
                .setDescription(message)
                .setColor(SUCCESS)
                .setTimestamp(Instant.now());
    }

    public static EmbedBuilder info(String title, String message) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(message)
                .setColor(INFO)
                .setTimestamp(Instant.now());
    }
}
