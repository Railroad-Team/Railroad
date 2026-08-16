package dev.railroadide.railroad.ide;

import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IDEViewModeControllerTest {
    @Test
    void rejectsUnavailableRequestsAndLegacyStateWrites() {
        var state = new SimpleObjectProperty<>(IDEViewMode.GIT);
        var gitAvailable = new AtomicBoolean(false);
        var controller = new IDEViewModeController(
            state,
            mode -> mode != IDEViewMode.GIT || gitAvailable.get(),
            Runnable::run
        );

        assertEquals(IDEViewMode.CODE, controller.getCurrentViewMode());
        assertEquals(IDEViewMode.CODE, state.get());
        assertFalse(controller.requestViewMode(IDEViewMode.GIT));

        state.set(IDEViewMode.GIT);
        assertEquals(IDEViewMode.CODE, controller.getCurrentViewMode());
        assertEquals(IDEViewMode.CODE, state.get());
    }

    @Test
    void deliversAcceptedTransitionsThroughOnePath() {
        var state = new SimpleObjectProperty<>(IDEViewMode.CODE);
        var gitAvailable = new AtomicBoolean(true);
        var controller = new IDEViewModeController(
            state,
            mode -> mode != IDEViewMode.GIT || gitAvailable.get(),
            Runnable::run
        );
        List<IDEViewMode> deliveredModes = new ArrayList<>();
        controller.onViewModeChanged(deliveredModes::add);

        assertTrue(controller.requestViewMode(IDEViewMode.GIT));

        assertEquals(IDEViewMode.GIT, state.get());
        assertEquals(IDEViewMode.GIT, controller.getCurrentViewMode());
        assertEquals(List.of(IDEViewMode.CODE, IDEViewMode.GIT), deliveredModes);
    }
}
