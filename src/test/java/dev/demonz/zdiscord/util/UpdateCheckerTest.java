/*
 * Copyright 2024 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
