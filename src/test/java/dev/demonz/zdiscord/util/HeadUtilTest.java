package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadUtilTest {

    @Test
    void resolvesUuidPlaceholder() {
        UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        String url = HeadUtil.resolve(
                "https://crafatar.com/avatars/%uuid%?overlay=true", id, "Notch");
        assertEquals("https://crafatar.com/avatars/069a79f4-44e9-4726-a5be-fca90e38aaf5?overlay=true",
                url);
    }

    @Test
    void resolvesUuidNoDashes() {
        UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        String url = HeadUtil.resolve(
                "https://crafatar.com/avatars/%uuid_nodashes%", id, "Notch");
        assertEquals("https://crafatar.com/avatars/069a79f444e94726a5befca90e38aaf5", url);
    }

    @Test
    void resolvesNamePlaceholder() {
        UUID id = UUID.randomUUID();
        String url = HeadUtil.resolve("mc-heads.net/avatar/%name%/128", id, "jeb_");
        assertEquals("mc-heads.net/avatar/jeb_/128", url);
    }

    @Test
    void passesThroughPlainString() {
        String plain = "https://example.com/static.png";
        assertEquals(plain, HeadUtil.resolve(plain, UUID.randomUUID(), "x"));
    }

    @Test
    void nullFormatReturnsDefault() {
        UUID id = UUID.randomUUID();
        assertEquals("https://mc-heads.net/avatar/" + id + "/128", HeadUtil.resolve(null, id, "x"));
    }

    @Test
    void crafatarHelper() {
        UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertTrue(HeadUtil.crafatar(id).contains("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
    }

    @Test
    void handlesNullName() {
        UUID id = UUID.randomUUID();
        String url = HeadUtil.resolve("hi/%name%", id, null);
        assertEquals("hi/", url);
        assertNotNull(url);
    }
}
