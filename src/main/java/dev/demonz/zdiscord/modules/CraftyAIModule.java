package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.config.ConfigManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/** Opt-in, text-only CraftyAI integration. Never dispatches a Minecraft command. */
public final class CraftyAIModule implements AutoCloseable {
    private final ZDiscord plugin;
    private final ThreadPoolExecutor requests = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), r -> { Thread t = new Thread(r, "ZDiscord-CraftyAI"); t.setDaemon(true); return t; });
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private volatile Settings settings;
    private record Settings(boolean enabled, String endpoint, String key, String guild, Set<String> channels, Set<String> roles) {}

    public CraftyAIModule(ZDiscord plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        ConfigManager c = plugin.getConfigManager();
        settings = new Settings(c.getBoolean("craftyai.enabled", false),
                c.getString("craftyai.endpoint", "https://craftyai-gateway.craftyauth.workers.dev/v1/developer/chat"),
                c.getString("craftyai.api-key", ""), c.getString("bot.guild-id", ""),
                Set.copyOf(c.getStringList("craftyai.allowed-channels")), Set.copyOf(c.getStringList("craftyai.allowed-roles")));
    }

    public void handle(SlashCommandInteractionEvent event) {
        Settings s = settings;
        if (!s.enabled) { reject(event, "CraftyAI is disabled. Ask your server administrator to enable it."); return; }
        if (event.getGuild() == null || !s.guild.equals(event.getGuild().getId()) || event.getMember() == null) {
            reject(event, "Use this command in the configured server."); return;
        }
        if (!s.channels.isEmpty() && !s.channels.contains(event.getChannel().getId())) {
            reject(event, "CraftyAI is not enabled in this channel."); return;
        }
        if (!s.roles.isEmpty() && event.getMember().getRoles().stream().noneMatch(r -> s.roles.contains(r.getId()))) {
            reject(event, "Your roles do not allow CraftyAI access."); return;
        }
        String prompt = event.getOption("question") == null ? "" : event.getOption("question").getAsString().trim();
        if (prompt.isEmpty() || prompt.length() > 4000) { reject(event, "Use a question of 1 to 4000 characters."); return; }
        if (!s.key.matches("^cai_[a-zA-Z0-9_-]{16,128}$")) { reject(event, "CraftyAI needs a valid API key in the server configuration."); return; }
        long now = System.currentTimeMillis();
        synchronized (cooldowns) {
            cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
            String user = event.getUser().getId();
            if (cooldowns.containsKey(user)) { reject(event, "Please wait 15 seconds between CraftyAI questions."); return; }
            if (cooldowns.size() >= 4096 || requests.getQueue().remainingCapacity() == 0) { reject(event, "CraftyAI is busy. Try again shortly."); return; }
            cooldowns.put(user, now + 15_000);
        }
        event.deferReply(true).queue(hook -> {
            try {
                requests.execute(() -> {
                    String answer;
                    try { answer = ask(s.endpoint, s.key, prompt); }
                    catch (Exception e) { answer = "CraftyAI is unavailable. Try again shortly or ask an administrator to check the integration."; }
                    if (!requests.isShutdown()) hook.editOriginal(answer.length() > 1900 ? answer.substring(0, 1897) + "..." : answer)
                            .setAllowedMentions(Collections.emptyList()).queue(null, e -> plugin.getLogger().fine("CraftyAI reply could not be delivered."));
                });
            } catch (RejectedExecutionException e) {
                hook.editOriginal("CraftyAI is busy. Try again shortly.").queue(null, ignored -> {});
            }
        }, e -> plugin.getLogger().fine("CraftyAI interaction could not be deferred."));
    }

    private static void reject(SlashCommandInteractionEvent e, String message) {
        e.reply(message).setEphemeral(true).setAllowedMentions(Collections.emptyList()).queue(null, ignored -> {});
    }

    // Package-visible for contract tests. Redirects must never receive the bearer key.
    @SuppressWarnings("unchecked")
    static String ask(String endpoint, String key, String prompt) throws Exception {
        URI uri = URI.create(endpoint);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("A valid HTTPS endpoint is required");
        }
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000); conn.setReadTimeout(30000);
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("User-Agent", "ZDiscord-CraftyAI/1.4.2");
            JSONObject payload = new JSONObject(); payload.put("prompt", prompt);
            try (var out = conn.getOutputStream()) { out.write(payload.toJSONString().getBytes(StandardCharsets.UTF_8)); }
            int status = conn.getResponseCode();
            if (status == 429) return "CraftyAI's request quota has been reached. Please try again later.";
            if (status != 200) throw new IllegalStateException("CraftyAI request failed");
            try (var in = conn.getInputStream()) {
                byte[] bytes = in.readNBytes(65537);
                if (bytes.length > 65536) throw new IllegalStateException("Response too large");
                Object parsed = new JSONParser().parse(new String(bytes, StandardCharsets.UTF_8));
                if (!(parsed instanceof JSONObject json) || !(json.get("answer") instanceof String answer) || answer.isBlank()) {
                    throw new IllegalStateException("Invalid CraftyAI response");
                }
                return answer;
            }
        } finally { conn.disconnect(); }
    }

    @Override public void close() { requests.shutdownNow(); cooldowns.clear(); }
}
