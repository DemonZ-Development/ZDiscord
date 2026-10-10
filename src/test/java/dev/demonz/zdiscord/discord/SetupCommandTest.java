package dev.demonz.zdiscord.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupCommandTest {
    private static Member member(String guildId, boolean administrator) {
        Guild guild = (Guild) Proxy.newProxyInstance(Guild.class.getClassLoader(), new Class<?>[]{Guild.class},
                (proxy, method, args) -> method.getName().equals("getId") ? guildId : null);
        return (Member) Proxy.newProxyInstance(Member.class.getClassLoader(), new Class<?>[]{Member.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGuild" -> guild;
                    case "hasPermission" -> administrator;
                    default -> null;
                });
    }

    @Test void configurationRequiresAdministratorInConfiguredGuild() {
        assertTrue(SetupCommand.canConfigure(member("guild", true), "guild"));
        assertFalse(SetupCommand.canConfigure(member("other-guild", true), "guild"));
        assertFalse(SetupCommand.canConfigure(member("guild", false), "guild"));
        assertFalse(SetupCommand.canConfigure(null, "guild"));
    }

    @Test void categoryIdsCannotInjectPathsOrExceedComponentLimits() {
        assertTrue(SetupCommand.validCategoryId("bug-reports_2"));
        assertFalse(SetupCommand.validCategoryId("billing.admin"));
        assertFalse(SetupCommand.validCategoryId("billing:other"));
        assertFalse(SetupCommand.validCategoryId("with spaces"));
        assertFalse(SetupCommand.validCategoryId("a".repeat(33)));
        assertFalse(SetupCommand.validCategoryId(null));
    }
}
