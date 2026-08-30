package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinUtilTest {

    @Test
    void fallsBackToPlainHeadWithoutSkinsRestorer() {
        UUID id = UUID.randomUUID();
        String url = SkinUtil.avatar(null, id, "Notch", 128);
        assertEquals(HeadUtil.avatar(id, 128), url);
    }

    @Test
    void fallsBackToPlainHeadWithNullName() {
        UUID id = UUID.randomUUID();
        String url = SkinUtil.avatar(null, id, null, 64);
        assertEquals(HeadUtil.avatar(id, 64), url);
    }

    @Test
    void reportsSkinsRestorerUnavailable() {
        assertFalse(SkinUtil.isAvailable());
    }

    @Test
    void initIsSafeWithoutSkinsRestorer() {
        SkinUtil.init(null);
        assertFalse(SkinUtil.isAvailable());
    }

    @Test
    void floodgateUuidsAreRecognised() {
        UUID bedrock = UUID.fromString("00000000-0000-0000-0009-01f04c8e2f6f");
        UUID java = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertTrue(SkinUtil.isBedrockUuid(bedrock));
        assertFalse(SkinUtil.isBedrockUuid(java));
        assertFalse(SkinUtil.isBedrockUuid(null));
    }

    @Test
    void bedrockPlayersFallBackToNameBasedAvatar() {
        UUID bedrock = UUID.fromString("00000000-0000-0000-0009-01f04c8e2f6f");
        String url = SkinUtil.avatar(null, bedrock, "BedrockSteve", 128);
        assertEquals("https://mc-heads.net/avatar/BedrockSteve/128", url);
    }
}