package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.storage.StorageManager;
import dev.demonz.zdiscord.storage.YamlStorage;
import dev.demonz.zdiscord.testsupport.SyncPlatformAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HalloweenHuntTest {

    private static final LocalDate WINDOW_START = LocalDate.of(2026, 10, 28);
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHARLIE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @TempDir
    Path dataFolder;

    private final List<YamlStorage> storages = new ArrayList<>();

    private YamlStorage openStorage() {
        YamlStorage storage = new YamlStorage(dataFolder.toFile(),
                Logger.getLogger("HalloweenHuntTest"), SyncPlatformAdapter.INSTANCE);
        storage.init();
        storages.add(storage);
        return storage;
    }

    private YamlStorage reopenStorage(YamlStorage storage) {
        storage.shutdown();
        storages.remove(storage);
        return openStorage();
    }

    @AfterEach
    void closeStorage() {
        storages.forEach(YamlStorage::shutdown);
    }

    @Test
    void existingScoresSurviveReloadInTheSameWindow() {
        YamlStorage storage = openStorage();
        storage.setData("halloween.window-start", WINDOW_START.toString());
        storage.saveStat(ALICE, "halloween_kills", 42L);
        storage.saveStat(ALICE, "kills", 100L);

        HalloweenHunt hunt = new HalloweenHunt(reopenStorage(storage));
        assertFalse(hunt.beginWindow(WINDOW_START));
        assertEquals(42L, hunt.score(ALICE));
        assertEquals(44L, hunt.addScore(ALICE, 2L));
        assertFalse(hunt.beginWindow(WINDOW_START));
        assertEquals(44L, hunt.score(ALICE));
    }

    @Test
    void aNewWindowResetsOnlySeasonalScoresAndPersistsItsMarker() {
        YamlStorage storage = openStorage();
        storage.saveStat(ALICE, "halloween_kills", 12L);
        storage.saveStat(BOB, "halloween_kills", 8L);
        storage.saveStat(ALICE, "kills", 100L);
        HalloweenHunt hunt = new HalloweenHunt(storage);

        assertTrue(hunt.beginWindow(WINDOW_START));
        assertEquals(0L, hunt.score(ALICE));
        assertEquals(0L, hunt.score(BOB));
        assertEquals(100L, storage.loadStats().get(ALICE).get("kills"));
        hunt.addScore(ALICE, 5L);

        YamlStorage restarted = reopenStorage(storage);
        HalloweenHunt resumed = new HalloweenHunt(restarted);
        assertEquals(WINDOW_START.toString(), restarted.getData("halloween.window-start"));
        assertFalse(resumed.beginWindow(WINDOW_START));
        assertEquals(5L, resumed.score(ALICE));
        assertEquals(0L, resumed.score(BOB));
        assertTrue(resumed.beginWindow(WINDOW_START.plusYears(1)));
        assertEquals(List.of(), resumed.standings(10));

        HalloweenHunt nextYear = new HalloweenHunt(reopenStorage(restarted));
        assertFalse(nextYear.beginWindow(WINDOW_START.plusYears(1)));
        assertEquals(0L, nextYear.score(ALICE));
    }

    @Test
    void standingsOrderPositiveScoresByRankThenUuidAndRespectLimits() {
        YamlStorage storage = openStorage();
        storage.saveStat(UUID.randomUUID(), "halloween_kills", -3L);
        storage.saveStat(UUID.randomUUID(), "halloween_kills", 0L);
        HalloweenHunt hunt = new HalloweenHunt(storage);
        hunt.addScore(CHARLIE, 10L);
        hunt.addScore(BOB, 20L);
        hunt.addScore(ALICE, 10L);

        assertEquals(List.of(Map.entry(BOB, 20L), Map.entry(ALICE, 10L),
                Map.entry(CHARLIE, 10L)), hunt.standings(10));
        assertEquals(List.of(Map.entry(BOB, 20L)), hunt.standings(1));
        assertEquals(List.of(), hunt.standings(0));
        assertEquals(List.of(), hunt.standings(-1));
    }

    @Test
    void nonPositivePointsAreIgnoredAndOverflowSaturates() {
        YamlStorage storage = openStorage();
        HalloweenHunt hunt = new HalloweenHunt(storage);
        assertEquals(0L, hunt.addScore(BOB, -1L));
        assertEquals(0L, hunt.addScore(BOB, 0L));
        assertFalse(storage.loadStats().containsKey(BOB));
        assertEquals(Long.MAX_VALUE - 2L, hunt.addScore(ALICE, Long.MAX_VALUE - 2L));
        assertEquals(Long.MAX_VALUE, hunt.addScore(ALICE, 5L));
        assertEquals(Long.MAX_VALUE, hunt.addScore(ALICE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, hunt.addScore(ALICE, -10L));

        HalloweenHunt restarted = new HalloweenHunt(reopenStorage(storage));
        assertEquals(Long.MAX_VALUE, restarted.score(ALICE));
    }

    @Test
    void milestoneJumpsReturnEveryCrossedPositiveUniqueTarget() {
        assertEquals(List.of(10L, 12L, 15L), HalloweenHunt.crossedMilestones(9L, 15L,
                Arrays.asList(15L, 10L, 10L, 0L, -1L, 12L, 9L, 20L, null)));
        assertEquals(List.of(), HalloweenHunt.crossedMilestones(15L, 15L, List.of(15L)));
        assertEquals(List.of(), HalloweenHunt.crossedMilestones(15L, 10L, List.of(12L)));
        assertEquals(List.of(), HalloweenHunt.crossedMilestones(0L, 10L, null));
    }

    @Test
    void aFinaleRequiresTheRecordedWindowAndActualParticipation() {
        YamlStorage storage = openStorage();
        HalloweenHunt hunt = new HalloweenHunt(storage);
        hunt.addScore(ALICE, 5L);
        assertFalse(hunt.claimFinale(WINDOW_START));
        assertFalse(hunt.claimFinale(null));
        assertFalse(hunt.beginWindow(null));

        assertTrue(hunt.beginWindow(WINDOW_START));
        assertFalse(hunt.claimFinale(WINDOW_START));
        hunt.addScore(ALICE, 5L);
        assertFalse(hunt.claimFinale(WINDOW_START.plusDays(1)));
        assertTrue(hunt.claimFinale(WINDOW_START));
        assertFalse(hunt.claimFinale(WINDOW_START));
        assertFalse(new HalloweenHunt(storage).claimFinale(WINDOW_START));

        HalloweenHunt restarted = new HalloweenHunt(reopenStorage(storage));
        assertFalse(restarted.claimFinale(WINDOW_START));
    }

    @Test
    void finaleMarkersRemainEventSpecificEvenWhenRevisitingAnOldWindow() {
        HalloweenHunt hunt = new HalloweenHunt(openStorage());
        assertTrue(hunt.beginWindow(WINDOW_START));
        hunt.addScore(ALICE, 1L);
        assertTrue(hunt.claimFinale(WINDOW_START));

        LocalDate nextStart = WINDOW_START.plusYears(1);
        assertTrue(hunt.beginWindow(nextStart));
        hunt.addScore(BOB, 2L);
        assertTrue(hunt.claimFinale(nextStart));
        assertTrue(hunt.beginWindow(WINDOW_START));
        hunt.addScore(ALICE, 1L);
        assertFalse(hunt.claimFinale(WINDOW_START));
    }

    @Test
    void repeatedWindowAndFinaleCallsDoNotDependOnImmediateBackendWrites() {
        YamlStorage storage = openStorage();
        List<Runnable> pendingMarkers = new ArrayList<>();
        StorageManager buffered = (StorageManager) Proxy.newProxyInstance(
                StorageManager.class.getClassLoader(), new Class<?>[]{StorageManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setData")) {
                        pendingMarkers.add(() -> storage.setData((String) args[0],
                                String.valueOf(args[1])));
                        return null;
                    }
                    return method.invoke(storage, args);
                });
        HalloweenHunt hunt = new HalloweenHunt(buffered);
        assertTrue(hunt.beginWindow(WINDOW_START));
        hunt.addScore(ALICE, 5L);
        assertFalse(hunt.beginWindow(WINDOW_START));
        assertEquals(5L, hunt.score(ALICE));
        assertTrue(hunt.claimFinale(WINDOW_START));
        assertFalse(hunt.claimFinale(WINDOW_START));
        assertEquals(2, pendingMarkers.size());

        pendingMarkers.forEach(Runnable::run);
        HalloweenHunt restarted = new HalloweenHunt(reopenStorage(storage));
        assertFalse(restarted.beginWindow(WINDOW_START));
        assertFalse(restarted.claimFinale(WINDOW_START));
    }

    @Test
    void concurrentKillsPersistTheWholeScore() throws Exception {
        YamlStorage storage = openStorage();
        HalloweenHunt hunt = new HalloweenHunt(storage);
        hunt.beginWindow(WINDOW_START);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                workers.add(executor.submit(() -> {
                    for (int kill = 0; kill < 100; kill++) hunt.addScore(ALICE, 1L);
                }));
            }
            for (Future<?> worker : workers) worker.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(400L, hunt.score(ALICE));
        assertEquals(400L, new HalloweenHunt(reopenStorage(storage)).score(ALICE));
    }
}
