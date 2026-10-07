package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartupBannerTest {

    @Test
    void moduleCountIncludesRecentlyAddedModules() {
        Object[] modules = new Object[17];
        modules[1] = new Object();
        modules[14] = new Object();
        modules[15] = new Object();
        modules[16] = new Object();

        assertEquals(new StartupBanner.ModuleCount(4, 17), StartupBanner.countModules(modules));
    }
}
