package dev.demonz.zdiscord.discord;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class PanelMessageTest {
    private record Request(boolean create, Consumer<Message> success, Consumer<Throwable> failure) { }
    private final List<Request> requests = new ArrayList<>();
    private final List<String> persisted = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private <T> T action(Class<T> type, boolean create) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("queue")) {
                requests.add(new Request(create, (Consumer<Message>) args[0], (Consumer<Throwable>) args[1]));
                return null;
            }
            return proxy;
        });
    }

    private TextChannel channel(String id) {
        return (TextChannel) Proxy.newProxyInstance(TextChannel.class.getClassLoader(), new Class<?>[]{TextChannel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "sendMessageEmbeds" -> action(MessageCreateAction.class, true);
                    case "editMessageEmbedsById" -> action(MessageEditAction.class, false);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Message message(String id) {
        return (Message) Proxy.newProxyInstance(Message.class.getClassLoader(), new Class<?>[]{Message.class},
                (proxy, method, args) -> method.getName().equals("getId") ? id : null);
    }

    private PanelMessage panel(String id) { return new PanelMessage(id, persisted::add, ignored -> { }); }

    @Test void slowCreationDoesNotQueueDuplicateMessages() {
        var panel = panel(null); var channel = channel("first");
        panel.update(channel, List.of()); panel.update(channel, List.of());
        assertEquals(1, requests.size());
        requests.get(0).success.accept(message("created"));
        panel.update(channel, List.of());
        assertEquals(2, requests.size());
        assertFalse(requests.get(1).create);
        assertEquals(List.of("created"), persisted);
    }

    @Test void transientFailuresRetryTheExistingMessage() {
        var panel = panel("existing"); var channel = channel("first");
        panel.update(channel, List.of());
        requests.get(0).failure.accept(new IOException("temporary outage"));
        panel.update(channel, List.of());
        assertEquals(2, requests.size());
        assertTrue(requests.stream().noneMatch(Request::create));
        assertEquals("existing", panel.messageId());
    }

    @Test void oldChannelCallbacksCannotOverwriteTheNewChannel() {
        var panel = panel(null);
        panel.update(channel("old-channel"), List.of());
        panel.update(channel("new-channel"), List.of());
        requests.get(0).success.accept(message("old-message"));
        assertTrue(persisted.isEmpty());
        requests.get(1).success.accept(message("new-message"));
        assertEquals(List.of("new-message"), persisted);
        assertEquals("new-message", panel.messageId());
    }

    @Test void stoppedPanelsIgnoreOutstandingCallbacks() {
        var panel = panel(null); var channel = channel("first");
        panel.update(channel, List.of()); panel.stop();
        requests.get(0).success.accept(message("too-late"));
        panel.update(channel, List.of());
        assertEquals(1, requests.size());
        assertTrue(persisted.isEmpty());
    }
}
