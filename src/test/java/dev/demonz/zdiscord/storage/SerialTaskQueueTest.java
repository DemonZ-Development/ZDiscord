package dev.demonz.zdiscord.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialTaskQueueTest {

    @Test
    void absoluteScoreWritesAndWindowMarkersPersistInSubmissionOrder() throws Exception {
        List<Runnable> workers = new ArrayList<>();
        List<Integer> persisted = new ArrayList<>();
        AtomicInteger score = new AtomicInteger(50);
        AtomicInteger scoreWhenMarkerWritten = new AtomicInteger(-1);
        SerialTaskQueue queue = new SerialTaskQueue(workers::add, failure -> {
            throw new AssertionError(failure);
        });
        queue.execute(() -> score.set(0));
        queue.execute(() -> scoreWhenMarkerWritten.set(score.get()));
        for (int value = 1; value <= 3; value++) {
            int next = value;
            queue.execute(() -> {
                score.set(next);
                persisted.add(next);
            });
        }

        assertEquals(1, workers.size());
        assertEquals(5, queue.pendingCount());
        workers.get(0).run();
        assertEquals(0, scoreWhenMarkerWritten.get());
        assertEquals(List.of(1, 2, 3), persisted);
        assertEquals(3, score.get());
        assertEquals(0, queue.pendingCount());
        assertTrue(queue.awaitIdle(0L));
    }

    @Test
    void concurrentProducersNeverOverlapWritesOrReverseTheirOwnUpdates() throws Exception {
        ExecutorService scheduler = Executors.newFixedThreadPool(4);
        ExecutorService producers = Executors.newFixedThreadPool(4);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        SerialTaskQueue queue = new SerialTaskQueue(scheduler::execute, failures::add);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        Map<Integer, List<Integer>> persisted = new ConcurrentHashMap<>();
        try {
            List<Future<?>> submitted = new ArrayList<>();
            for (int producer = 0; producer < 4; producer++) {
                int player = producer;
                submitted.add(producers.submit(() -> {
                    for (int value = 1; value <= 100; value++) {
                        int next = value;
                        queue.execute(() -> {
                            int writers = active.incrementAndGet();
                            maximumActive.accumulateAndGet(writers, Math::max);
                            try {
                                Thread.yield();
                                persisted.computeIfAbsent(player, key -> new ArrayList<>()).add(next);
                            } finally {
                                active.decrementAndGet();
                            }
                        });
                    }
                }));
            }
            for (Future<?> producer : submitted) producer.get(5L, TimeUnit.SECONDS);
            assertTrue(queue.awaitIdle(5000L));
            assertEquals(1, maximumActive.get());
            assertEquals(0, queue.pendingCount());
            assertEquals(List.of(), failures);
            for (int player = 0; player < 4; player++) {
                assertEquals(IntStream.rangeClosed(1, 100).boxed().toList(), persisted.get(player));
            }
        } finally {
            producers.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void failedWritesAndErrorCallbacksDoNotStrandFollowingWrites() throws Exception {
        List<Runnable> workers = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        AtomicBoolean laterWrite = new AtomicBoolean();
        SerialTaskQueue queue = new SerialTaskQueue(workers::add, failure -> {
            failures.add(failure);
            throw new IllegalStateException("Failed error callback");
        });
        queue.execute(() -> { throw new IllegalArgumentException("Failed write"); });
        queue.execute(() -> { throw new AssertionError("Failed second write"); });
        queue.execute(() -> laterWrite.set(true));
        workers.get(0).run();

        assertTrue(laterWrite.get());
        assertEquals(2, failures.size());
        assertEquals(0, queue.pendingCount());
        assertTrue(queue.awaitIdle(0L));
    }

    @Test
    void rejectedSchedulingDrainsWritesWithoutLeakingPendingCounts() {
        List<Throwable> failures = new ArrayList<>();
        List<Integer> persisted = new ArrayList<>();
        SerialTaskQueue queue = new SerialTaskQueue(task -> {
            throw new RejectedExecutionException("Plugin scheduler is unavailable");
        }, failures::add);
        queue.execute(() -> persisted.add(1));
        queue.execute(() -> persisted.add(2));

        assertEquals(List.of(1, 2), persisted);
        assertEquals(2, failures.size());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    void shutdownCanTakeOverAnUnstartedWorkerAndCloseAfterItsWrites() {
        List<Runnable> workers = new ArrayList<>();
        List<String> persisted = new ArrayList<>();
        SerialTaskQueue queue = new SerialTaskQueue(workers::add, failure -> {
            throw new AssertionError(failure);
        });
        queue.execute(() -> persisted.add("first"));
        queue.execute(() -> persisted.add("second"));
        queue.execute(() -> persisted.add("close pool"));
        queue.drainSynchronously();

        assertEquals(List.of("first", "second", "close pool"), persisted);
        assertEquals(0, queue.pendingCount());
        workers.get(0).run();
        assertEquals(3, persisted.size());
        queue.execute(() -> persisted.add("next worker"));
        workers.get(1).run();
        assertEquals(List.of("first", "second", "close pool", "next worker"), persisted);
    }

    @Test
    void shutdownWaitsForAnActiveWorkerAndPreservesItsDeferredPoolClose() throws Exception {
        ExecutorService scheduler = Executors.newSingleThreadExecutor();
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> persisted = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        SerialTaskQueue queue = new SerialTaskQueue(scheduler::execute, failures::add);
        try {
            queue.execute(() -> {
                writing.countDown();
                try {
                    if (!release.await(5L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test did not release active writer");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                persisted.add("first");
            });
            assertTrue(writing.await(5L, TimeUnit.SECONDS));
            queue.execute(() -> persisted.add("second"));
            queue.execute(() -> persisted.add("close pool"));
            queue.drainSynchronously();
            assertFalse(queue.awaitIdle(20L));
            assertEquals(List.of(), persisted);
            assertEquals(3, queue.pendingCount());
            release.countDown();

            assertTrue(queue.awaitIdle(5000L));
            assertEquals(List.of("first", "second", "close pool"), persisted);
            assertEquals(List.of(), failures);
        } finally {
            release.countDown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void reentrantSubmissionsJoinTheTailWithoutStartingAnOverlappingWorker() {
        List<Runnable> workers = new ArrayList<>();
        List<Integer> persisted = new ArrayList<>();
        SerialTaskQueue queue = new SerialTaskQueue(workers::add, failure -> {
            throw new AssertionError(failure);
        });
        queue.execute(() -> {
            persisted.add(1);
            queue.execute(() -> persisted.add(3));
        });
        queue.execute(() -> persisted.add(2));
        workers.get(0).run();

        assertEquals(List.of(1, 2, 3), persisted);
        assertEquals(1, workers.size());
        assertEquals(0, queue.pendingCount());
    }
}
