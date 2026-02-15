package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.Bukkit;
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
import java.util.concurrent.CompletableFuture;

public class UpdateChecker implements Listener {

    private final ZDiscord plugin;
    private final String projectSlug = "zdiscord";
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(ZDiscord plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        checkForUpdates();
    }

    private void checkForUpdates() {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL("https://api.modrinth.com/v2/project/" + projectSlug + "/version");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "DemonZ-Development/ZDiscord");

                if (connection.getResponseCode() == 200) {
                    InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                    JSONArray versions = (JSONArray) new JSONParser().parse(reader);

                    if (!versions.isEmpty()) {
                        JSONObject latest = (JSONObject) versions.get(0);
                        latestVersion = (String) latest.get("version_number");
                        String currentVersion = plugin.getDescription().getVersion();

                        if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                            updateAvailable = true;
                            plugin.getServer().getConsoleSender().sendMessage("");
                            plugin.getServer().getConsoleSender()
                                    .sendMessage("§b[ZDiscord] §aA new update is available: §ev" + latestVersion);
                            plugin.getServer().getConsoleSender().sendMessage(
                                    "§b[ZDiscord] §7Download it at: §fhttps://modrinth.com/project/" + projectSlug);
                            plugin.getServer().getConsoleSender().sendMessage("");
                        }
                    }
                }
            } catch (Exception e) {
                plugin.debug("Failed to check for updates: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (updateAvailable && event.getPlayer().hasPermission("zdiscord.admin")) {
            Player player = event.getPlayer();
            player.sendMessage("§b[ZDiscord] §aA new update is available: §ev" + latestVersion);
            player.sendMessage("§b[ZDiscord] §7Download it at: §fhttps://modrinth.com/project/" + projectSlug);
        }
    }
}
