package dev.railroadide.railroad.ide.activity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BackgroundActivitiesTest {
    @Test
    public void activitiesRunUntilClosedAndNotifyObserversUntilUnsubscribed() {
        var activities = new BackgroundActivities();
        var notifications = new AtomicInteger();
        var subscription = activities.observe(notifications::incrementAndGet);

        var indexing = activities.start("railroad.activity.indexing_project");
        try (var _ = activities.start("railroad.activity.indexing_jdk")) {
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
