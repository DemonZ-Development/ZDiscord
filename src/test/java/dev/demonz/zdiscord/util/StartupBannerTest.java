package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartupBannerTest {

    @Test
    void moduleCountIncludesRecentlyAddedModules() {
        Object[] modules = new Object[16];
        modules[1] = new Object();
        modules[14] = new Object();
        modules[15] = new Object();

        assertEquals(new StartupBanner.ModuleCount(3, 16), StartupBanner.countModules(modules));
    }
}
