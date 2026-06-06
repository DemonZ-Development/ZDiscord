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

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Polls the Modrinth API for a newer release of the plugin and
 * notifies admins on join.
 */
public class UpdateChecker implements Listener {

    private static final String PROJECT_SLUG = "zdiscord";
    private static final String API_URL = "https://api.modrinth.com/v2/project/" + PROJECT_SLUG + "/version";
    private static final String PAGE_URL = "https://modrinth.com/project/" + PROJECT_SLUG;

    private final ZDiscord plugin;
    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(ZDiscord plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        checkForUpdates();
    }

    private void checkForUpdates() {
        CompletableFuture.runAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "DemonZ-Development/ZDiscord");
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(5_000);

                if (connection.getResponseCode() != 200) {
                    return;
                }
                JSONArray versions = (JSONArray) new JSONParser().parse(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                if (versions.isEmpty()) {
                    return;
                }
                JSONObject latest = (JSONObject) versions.get(0);
                latestVersion = (String) latest.get("version_number");
                String currentVersion = plugin.getDescription().getVersion();
                if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                    updateAvailable = true;
                    plugin.getLogger().info("A new version of ZDiscord is available: v"
                            + latestVersion + " (" + PAGE_URL + ")");
                }
            } catch (Exception e) {
                plugin.debug("Update check failed: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!updateAvailable) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("zdiscord.admin")) {
            return;
        }
        player.sendMessage("A new version of ZDiscord is available: v" + latestVersion);
        player.sendMessage("Download: " + PAGE_URL);
    }
}
