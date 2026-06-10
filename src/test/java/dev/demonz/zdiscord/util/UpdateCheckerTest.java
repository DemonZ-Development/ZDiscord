package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void equalVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.2.3", "1.2.3"));
    }

    @Test
    void higherMajorIsNewer() {
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.99"));
    }

    @Test
    void higherMinorIsNewer() {
        assertTrue(UpdateChecker.isNewer("1.3.0", "1.2.9"));
    }

    @Test
    void higherPatchIsNewer() {
        assertTrue(UpdateChecker.isNewer("1.2.4", "1.2.3"));
    }

    @Test
    void lowerVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.1"));
        assertFalse(UpdateChecker.isNewer("0.9.0", "1.0.0"));
    }

    @Test
    void releaseIsNewerThanPrerelease() {
        assertTrue(UpdateChecker.isNewer("1.2.3", "1.2.3-beta"));
        assertTrue(UpdateChecker.isNewer("1.2.3", "1.2.3-beta.1"));
    }

    @Test
    void prereleaseIsNotNewerThanRelease() {
        assertFalse(UpdateChecker.isNewer("1.2.3-beta", "1.2.3"));
    }

    @Test
    void handlesTwoSegmentVersions() {
        assertTrue(UpdateChecker.isNewer("1.3", "1.2"));
        assertFalse(UpdateChecker.isNewer("1.2", "1.2"));
    }

    @Test
    void unparseableStringsReturnFalse() {
        assertFalse(UpdateChecker.isNewer("not-a-version", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "garbage"));
    }

    @Test
    void nullsReturnFalse() {
        assertFalse(UpdateChecker.isNewer(null, "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", null));
    }
}
