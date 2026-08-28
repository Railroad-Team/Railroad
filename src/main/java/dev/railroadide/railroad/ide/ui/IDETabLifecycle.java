package dev.railroadide.railroad.ide.ui;

import javafx.event.Event;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.Objects;

/** Shared close lifecycle for IDE-owned tabs, including detached-window tabs. */
public final class IDETabLifecycle {
    private IDETabLifecycle() {
    }

    /**
     * Requests closure, honors cancellation, removes the tab from its current pane, and
     * emits the normal closed event exactly once for this request.
     *
     * @param tab tab to close
     * @return whether the close completed
     */
    public static boolean requestClose(Tab tab) {
        tab = Objects.requireNonNull(tab, "Tab cannot be null");
        var closeRequest = new Event(tab, tab, Tab.TAB_CLOSE_REQUEST_EVENT);
        Event.fireEvent(tab, closeRequest);
        if (closeRequest.isConsumed())
            return false;

        TabPane tabPane = tab.getTabPane();
        if (tabPane != null && !tabPane.getTabs().remove(tab))
            return false;

        Event.fireEvent(tab, new Event(tab, tab, Tab.CLOSED_EVENT));
        return true;
    }
}
