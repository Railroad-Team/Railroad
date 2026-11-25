package dev.railroadide.railroad.ide.activity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.concurrent.Future;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BackgroundActivitiesTest {
    @Test
    public void overlappingTasksUpdateOnlyTheirOwnActivity() throws Exception {
        var registry = new BackgroundActivities();
        var barrier = new CyclicBarrier(2);
        try (BackgroundActivities.Handle first = registry.start("first");
            BackgroundActivities.Handle second = registry.start("second");
            var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Runnable> firstCount = pool
                .submit(() -> ScopedValue.where(ActivityProgress.CURRENT, first).call(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return ActivityProgress.current().count("files", ActivityProgress.Unit.COUNT, 100);
                }));

            Future<Runnable> secondCount = pool
                .submit(() -> ScopedValue.where(ActivityProgress.CURRENT, second).call(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return ActivityProgress.current().count("modules", ActivityProgress.Unit.COUNT, 20);
                }));

            Runnable files = firstCount.get(5, TimeUnit.SECONDS);
            Runnable modules = secondCount.get(5, TimeUnit.SECONDS);
            IntStream.range(0, 100).parallel().forEach(_ -> files.run());
            modules.run();
            assertEquals(List.of(100L, 1L), registry.running().stream()
                .map(BackgroundActivities.Activity::completed).toList());
            assertEquals("second", registry.running().getLast().titleKey());
            assertEquals(ActivityProgress.NONE, ActivityProgress.current());
        }
    }

    @Test
    public void estimatesOnlyAdvancingMeasuredPhasesAndIgnoresLateCallbacks() {
        var clock = new AtomicLong();
        var registry = new BackgroundActivities(clock::get);
        BackgroundActivities.Handle activity = registry.start("download");
        LongConsumer bytes = activity.begin("file", ActivityProgress.Unit.BYTES, 400, 1000);
        bytes.accept(450);
        assertEquals(-1, registry.running().getFirst().remainingSeconds());
        clock.set(TimeUnit.SECONDS.toNanos(2));
        bytes.accept(500);
        assertEquals(10, registry.running().getFirst().remainingSeconds());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(3));
        assertEquals(7, registry.running().getFirst().remainingSeconds());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(8));
        assertEquals(-1, registry.running().getFirst().remainingSeconds());
        bytes.accept(420);
        assertEquals(500, registry.running().getFirst().completed());
        bytes.accept(1000);
        assertEquals(-1, registry.running().getFirst().remainingSeconds());
        LongConsumer steps = activity.begin("setup", ActivityProgress.Unit.STEPS, 0, 5);
        steps.accept(1);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
        steps.accept(2);
        assertEquals(-1, registry.running().getFirst().remainingSeconds());
        activity.indeterminate("saving");
        steps.accept(3);
        assertEquals(-1, registry.running().getFirst().fraction());
        activity.close();
        steps.accept(4);
        activity.indeterminate("late");
        assertTrue(registry.running().isEmpty());
    }

    @Test
    public void overlappingDownloadsKeepTheirOwnCountsAndHideUnknownTotals() {
        var registry = new BackgroundActivities();
        try (BackgroundActivities.Handle activity = registry.start("downloads")) {
            var downloads = new DownloadProgress(activity);
            String first = "https://repo.test/first.jar?token=hidden";
            String second = "https://repo.test/second.jar";
            downloads.update(first, 1, -1);
            assertEquals(-1, registry.running().getFirst().fraction());
            downloads.update(first, 2, 10);
            downloads.update(second, 50, 100);
            downloads.update(first, 3, -1);
            downloads.update(second, 60, -1);
            assertEquals("first.jar", registry.running().getFirst().phase());
            assertEquals(0.3, registry.running().getFirst().fraction());
            downloads.finish(first);
            assertEquals("second.jar", registry.running().getFirst().phase());
            assertEquals(0.6, registry.running().getFirst().fraction());
            assertEquals(-1, registry.running().getFirst().remainingSeconds());
            downloads.finish(second);
            assertEquals(-1, registry.running().getFirst().fraction());
        }
    }

    @Test
    public void activitiesRunUntilClosedAndNotifyObserversUntilUnsubscribed() {
        var activities = new BackgroundActivities();
        var notifications = new AtomicInteger();
        BackgroundActivities.Subscription subscription = activities.observe(notifications::incrementAndGet);

        BackgroundActivities.Handle indexing = activities.start("railroad.activity.indexing_project");
        try (BackgroundActivities.Handle _ = activities.start("railroad.activity.indexing_jdk")) {
            assertEquals(List.of("railroad.activity.indexing_project", "railroad.activity.indexing_jdk"),
                activities.running().stream().map(BackgroundActivities.Activity::titleKey).toList());
        }

        assertEquals(List.of("railroad.activity.indexing_project"),
            activities.running().stream().map(BackgroundActivities.Activity::titleKey).toList());

        indexing.close();
        indexing.close();
        assertTrue(activities.running().isEmpty());
        assertEquals(4, notifications.get());

        subscription.close();
        activities.start("railroad.activity.scanning_files").close();
        assertEquals(4, notifications.get());
    }
}
