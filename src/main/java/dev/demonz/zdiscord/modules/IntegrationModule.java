package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.ZLogger;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Locale;

/**
 * Optional, dependency-free bridges for other DemonZ plugins. Reflection keeps
 * ZDiscord loadable when Onlysleep or RedstoneReboot is not installed.
 */
public final class IntegrationModule {

    private static final int DISCORD_MESSAGE_LIMIT = 2000;

    private final ZDiscord plugin;
    private final Listener onlysleepListener = new Listener() { };
    private Object redstoneApi;
    private Object redstoneAdapter;

    public IntegrationModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        hookOnlysleep();
        hookRedstoneReboot();
    }

    public void reload() {
        shutdown();
        init();
    }

    public void shutdown() {
        HandlerList.unregisterAll(onlysleepListener);
        unregisterRedstoneAdapter();
    }

    @SuppressWarnings("unchecked")
    private void hookOnlysleep() {
        if (!plugin.getConfigManager().getBoolean("integrations.onlysleep.enabled", true)) return;

        Plugin dependency = plugin.getServer().getPluginManager().getPlugin("Onlysleep");
        if (dependency == null || !dependency.isEnabled()) return;

        try {
            ClassLoader loader = dependency.getClass().getClassLoader();
            registerOnlysleepEvent(loader, "com.demonzdevelopment.onlysleep.api.events.SleepStartEvent");
            registerOnlysleepEvent(loader, "com.demonzdevelopment.onlysleep.api.events.SleepCancelEvent");
            registerOnlysleepEvent(loader, "com.demonzdevelopment.onlysleep.api.events.NightSkipEvent");
            ZLogger.info(ZLogger.Category.MODULES, "Onlysleep event bridge enabled.");
        } catch (ReflectiveOperationException | LinkageError e) {
            ZLogger.warn(ZLogger.Category.MODULES,
                    "Onlysleep was found but its event API is incompatible: " + e.getMessage());
            HandlerList.unregisterAll(onlysleepListener);
        }
    }

    @SuppressWarnings("unchecked")
    private void registerOnlysleepEvent(ClassLoader loader, String className)
            throws ReflectiveOperationException {
        Class<?> raw = Class.forName(className, false, loader);
        if (!Event.class.isAssignableFrom(raw)) {
            throw new IllegalStateException(className + " is not a Bukkit event");
        }
        plugin.getServer().getPluginManager().registerEvent(
                (Class<? extends Event>) raw,
                onlysleepListener,
                EventPriority.MONITOR,
                (listener, event) -> forwardOnlysleep(event),
                plugin,
                true);
    }

    private void forwardOnlysleep(Event event) {
        String message;
        try {
            message = formatOnlysleepEvent(event);
        } catch (ReflectiveOperationException e) {
            ZLogger.debug(ZLogger.Category.MODULES,
                    "Could not format Onlysleep event " + event.getEventName() + ": " + e.getMessage());
            return;
        }
        sendConfigured("integrations.onlysleep.channel", message);
    }

    static String formatOnlysleepEvent(Object event) throws ReflectiveOperationException {
        String name = event.getClass().getSimpleName();
        if ("SleepStartEvent".equals(name)) {
            Player player = (Player) invoke(event, "getPlayer");
            int sleeping = ((Number) invoke(event, "getSleepingCount")).intValue();
            int required = ((Number) invoke(event, "getRequiredCount")).intValue();
            return "\uD83D\uDECF\uFE0F **Onlysleep** · " + safePlayerName(player)
                    + " is sleeping (`" + sleeping + "/" + required + "`).";
        }
        if ("SleepCancelEvent".equals(name)) {
            Player player = (Player) invoke(event, "getPlayer");
            Object cause = invoke(event, "getCause");
            int sleeping = ((Number) invoke(event, "getSleepingCount")).intValue();
            int required = ((Number) invoke(event, "getRequiredCount")).intValue();
            return "\u23F9\uFE0F **Onlysleep** · Sleep cancelled by " + safePlayerName(player)
                    + " (`" + sleeping + "/" + required + "`, "
                    + String.valueOf(cause).toLowerCase(Locale.ROOT).replace('_', ' ') + ").";
        }
        if ("NightSkipEvent".equals(name)) {
            World world = (World) invoke(event, "getWorld");
            Player initiator = (Player) invoke(event, "getInitiator");
            int sleeping = ((Number) invoke(event, "getSleepingCount")).intValue();
            int required = ((Number) invoke(event, "getRequiredCount")).intValue();
            return "\u2600\uFE0F **Onlysleep** · Night skipped in **"
                    + (world != null ? world.getName() : "unknown world") + "** by "
                    + safePlayerName(initiator) + " (`" + sleeping + "/" + required + "`).";
        }
        return null;
    }

    private void hookRedstoneReboot() {
        if (!plugin.getConfigManager().getBoolean("integrations.redstone-reboot.enabled", true)) return;

        Plugin dependency = plugin.getServer().getPluginManager().getPlugin("RedstoneReboot");
        if (dependency == null || !dependency.isEnabled()) return;

        try {
            ClassLoader loader = dependency.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(
                    "dev.demonz.redstonereboot.common.api.RedstoneRebootAPI", false, loader);
            Class<?> adapterClass = Class.forName(
                    "dev.demonz.redstonereboot.common.api.MessageAdapter", false, loader);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                ZLogger.warn(ZLogger.Category.MODULES,
                        "RedstoneReboot is enabled but its API is not ready; bridge skipped.");
                return;
            }

            Object adapter = Proxy.newProxyInstance(adapterClass.getClassLoader(),
                    new Class<?>[] {adapterClass}, (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "onMessage" -> {
                                if (args != null && args.length == 1 && args[0] != null) {
                                    forwardRedstoneMessage(args[0]);
                                }
                                yield null;
                            }
                            case "getName" -> "ZDiscord";
                            case "handlesPostponed", "handlesAlerts" -> true;
                            case "toString" -> "ZDiscord RedstoneReboot message adapter";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == (args != null && args.length == 1 ? args[0] : null);
                            default -> null;
                        };
                    });
            apiClass.getMethod("registerMessageAdapter", adapterClass).invoke(api, adapter);
            redstoneApi = api;
            redstoneAdapter = adapter;
            ZLogger.info(ZLogger.Category.MODULES, "RedstoneReboot message bridge enabled.");
        } catch (ReflectiveOperationException | LinkageError e) {
            ZLogger.warn(ZLogger.Category.MODULES,
                    "RedstoneReboot was found but its message API is incompatible: " + e.getMessage());
        }
    }

    private void forwardRedstoneMessage(Object context) {
        try {
            Object type = invoke(context, "getType");
            String plain = String.valueOf(invoke(context, "toPlainText"));
            String clean = ColorUtil.stripColor(plain).trim();
            if (clean.isEmpty()) return;
            String label = type == null
                    ? "Alert"
                    : String.valueOf(type).toLowerCase(Locale.ROOT).replace('_', ' ');
            sendConfigured("integrations.redstone-reboot.channel",
                    "\uD83D\uDD01 **RedstoneReboot · " + label + "**\n" + clean);
        } catch (ReflectiveOperationException e) {
            ZLogger.debug(ZLogger.Category.MODULES,
                    "Could not format RedstoneReboot message: " + e.getMessage());
        }
    }

    private void unregisterRedstoneAdapter() {
        if (redstoneApi == null || redstoneAdapter == null) return;
        try {
            Method unregister = null;
            for (Method method : redstoneApi.getClass().getMethods()) {
                if (method.getName().equals("unregisterMessageAdapter")
                        && method.getParameterCount() == 1) {
                    unregister = method;
                    break;
                }
            }
            if (unregister != null) unregister.invoke(redstoneApi, redstoneAdapter);
        } catch (ReflectiveOperationException e) {
            ZLogger.debug(ZLogger.Category.MODULES,
                    "Could not unregister RedstoneReboot bridge: " + e.getMessage());
        } finally {
            redstoneApi = null;
            redstoneAdapter = null;
        }
    }

    private void sendConfigured(String path, String message) {
        if (message == null || message.isBlank() || plugin.getBotManager() == null) return;
        TextChannel channel = plugin.getBotManager().getTextChannel(path);
        if (channel == null) channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null) return;

        String content = truncate(message, DISCORD_MESSAGE_LIMIT);
        channel.sendMessage(content)
                .setAllowedMentions(Collections.<Message.MentionType>emptyList())
                .queue(null, error -> ZLogger.warn(ZLogger.Category.MODULES,
                        "Integration message failed: " + error.getMessage()));
    }

    static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "\u2026";
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static String safePlayerName(Player player) {
        return player != null ? player.getName() : "the server";
    }
}
