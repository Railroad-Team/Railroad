package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.vcs.git.execution.GitOutputListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GitActivityProgressTest {
    @Test
    public void parsesCountsAndResetsProgressAtPhaseBoundaries() {
        var registry = new BackgroundActivities();
        try (var activity = registry.start("fetch")) {
            var git = GitListeners.withProgress(GitOutputListener.NO_OP, GitListeners.activity(activity), "fetch");
            git.onStderr("remote: Receiving objects:  42% (1234/2938), 1.23 MiB | 4.56 MiB/s");
            var receiving = registry.running().getFirst();
            assertEquals("Receiving objects", receiving.phase());
            assertEquals(1234, receiving.completed());
            assertEquals(2938, receiving.total());
            git.onStderr("Resolving deltas: 10%");
            var resolving = registry.running().getFirst();
            assertEquals(ActivityProgress.Unit.PERCENT, resolving.unit());
            assertEquals(0.1, resolving.fraction());
            git.onStderr("Enumerating objects: 250, done.");
            assertEquals("Enumerating objects", registry.running().getFirst().phase());
            assertEquals(-1, registry.running().getFirst().fraction());
        }
    }
}
