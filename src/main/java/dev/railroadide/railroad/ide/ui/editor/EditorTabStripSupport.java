package dev.railroadide.railroad.ide.ui.editor;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Skin;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;

import java.util.Objects;
import java.util.function.Function;

public final class EditorTabStripSupport implements AutoCloseable {
    public static final String DRAG_ACTIVE_PROPERTY = "railroad:editor-tab-drag-active";
    private static final double AUTO_SCROLL_EDGE_SIZE = 48;
    private static final double AUTO_SCROLL_DELTA = 9;
    private static final long AUTO_SCROLL_INTERVAL_NANOS = 20_000_000L;
    private static final double MINIMUM_STABILIZED_LABEL_WIDTH = 32;

    private final DetachableTabPane tabPane;
    private final EditorAllTabsMenu allTabsMenu;
    private final LocalizedTooltip allTabsTooltip = new LocalizedTooltip("editor.tabs.dropdown.tooltip");
    private final EventHandler<MouseEvent> allTabsButtonHandler = this::handleAllTabsButtonMouseEvent;
    private final EventHandler<MouseEvent> mousePressedHandler = this::handleMousePressed;
    private final EventHandler<MouseEvent> mouseMovedHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseExitedHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseDraggedHandler = this::handleMouseDragged;
    private final EventHandler<MouseEvent> mouseReleasedHandler = _ -> stopAutoScroll();
    private final EventHandler<MouseEvent> dragDetectedHandler = this::handleDragDetected;
    private final EventHandler<DragEvent> dockingDragHandler = this::handleDockingDrag;
    private final EventHandler<ScrollEvent> scrollHandler = this::handleScroll;
    private final Runnable dragFinished;
    private final ChangeListener<Tab> selectionListener = (_, _, selectedTab) -> handleSelectionChanged(selectedTab);
    private final ChangeListener<Number> widthListener = (_, _, _) -> {
        scheduleSelectedTabVisibilityUpdate();
        scheduleAllTabsButtonInstall();
    };
    private final ChangeListener<Skin<?>> skinListener = (_, _, _) -> {
        uninstallAllTabsButton();
        scheduleSelectedTabVisibilityUpdate();
        scheduleAllTabsButtonInstall();
    };
    private final ChangeListener<Scene> sceneListener = (_, _, _) -> scheduleAllTabsButtonInstall();
    private final ListChangeListener<Tab> tabListListener = this::handleTabsChanged;
    private final AnimationTimer autoScrollTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (now - lastAutoScrollNanos < AUTO_SCROLL_INTERVAL_NANOS)
                return;

            lastAutoScrollNanos = now;
            scrollAtPointer();
        }
    };

    private boolean draggingTab;
    private boolean dockingDragOver;
    private int autoScrollDirection;
    private double pointerSceneX;
    private double pointerSceneY;
    private double pointerScreenX;
    private double pointerScreenY;
    private long lastAutoScrollNanos;
    private boolean visibilityUpdateScheduled;
    private boolean closeStabilizationPending;
    private boolean closed;
    private boolean allTabsButtonInstallScheduled;
    private Node allTabsButton;
    private Tab pendingClosedTab;
    private double pendingCloseButtonSceneX;
    private Region stabilizedLabel;
    private double stabilizedLabelMinWidth;
    private double stabilizedLabelPrefWidth;
    private double stabilizedLabelMaxWidth;

    public EditorTabStripSupport(
        DetachableTabPane tabPane,
        Function<Tab, EditorTab> editorTabResolver,
        Runnable dragFinished
    ) {
        this.tabPane = tabPane;
        this.dragFinished = Objects.requireNonNull(dragFinished, "Drag-finished action cannot be null");
        this.allTabsMenu = new EditorAllTabsMenu(tabPane, editorTabResolver);
        tabPane.getStyleClass().add("editor-tab-pane");
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.FIXED);
        tabPane.addEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        tabPane.addEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        tabPane.addEventFilter(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        tabPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        tabPane.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        tabPane.addEventFilter(MouseEvent.DRAG_DETECTED, dragDetectedHandler);
        tabPane.addEventFilter(DragEvent.ANY, dockingDragHandler);
        tabPane.addEventHandler(ScrollEvent.SCROLL, scrollHandler);
        tabPane.getSelectionModel().selectedItemProperty().addListener(selectionListener);
        tabPane.widthProperty().addListener(widthListener);
        tabPane.skinProperty().addListener(skinListener);
        tabPane.sceneProperty().addListener(sceneListener);
        tabPane.getTabs().addListener(tabListListener);
        scheduleSelectedTabVisibilityUpdate();
        scheduleAllTabsButtonInstall();
    }

    private void scheduleAllTabsButtonInstall() {
        if (closed || allTabsButtonInstallScheduled)
            return;

        allTabsButtonInstallScheduled = true;
        Platform.runLater(() -> {
            allTabsButtonInstallScheduled = false;
            if (!closed) {
                installAllTabsButton();
            }
        });
    }

    private void installAllTabsButton() {
        Node button = tabPane.lookup(".tab-down-button");
        if (button == null || button == allTabsButton)
            return;

        uninstallAllTabsButton();
        allTabsButton = button;
        allTabsButton.addEventFilter(MouseEvent.ANY, allTabsButtonHandler);
        Tooltip.install(allTabsButton, allTabsTooltip);
        allTabsButton.accessibleTextProperty().bind(allTabsTooltip.textProperty());
    }

    private void handleAllTabsButtonMouseEvent(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY)
            return;

        if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            if (allTabsMenu.isShowing()) {
                allTabsMenu.hide();
            } else if (allTabsButton != null) {
                allTabsMenu.show(allTabsButton);
            }
            event.consume();
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED
            || event.getEventType() == MouseEvent.MOUSE_CLICKED) {
            event.consume();
        }
    }

    private void uninstallAllTabsButton() {
        if (allTabsButton == null)
            return;

        allTabsButton.removeEventFilter(MouseEvent.ANY, allTabsButtonHandler);
        Tooltip.uninstall(allTabsButton, allTabsTooltip);
        allTabsButton.accessibleTextProperty().unbind();
        allTabsButton = null;
    }

    private void handleMousePressed(MouseEvent event) {
        captureCloseGesture(event);
        draggingTab = event.getButton() == MouseButton.PRIMARY && isTabHeaderEvent(event);
        if (!draggingTab) {
            stopAutoScroll();
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        if ((closeStabilizationPending || stabilizedLabel != null) && !isInsideHeaderArea(event)) {
            clearCloseStabilization();
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!draggingTab || !event.isPrimaryButtonDown()) {
            stopAutoScroll();
            return;
        }

        pointerSceneX = event.getSceneX();
        pointerSceneY = event.getSceneY();
        pointerScreenX = event.getScreenX();
        pointerScreenY = event.getScreenY();
        updateAutoScrollDirection();
    }

    private void handleDragDetected(MouseEvent event) {
        if (draggingTab) {
            // TiwulFX owns the drag gesture so it can move tabs across panes and stages.
            // Keep the source group alive until its DRAG_DONE handler has redocked or
            // detached the tab.
            Node tabHeader = findAncestorWithStyle(event.getPickResult().getIntersectedNode(), "tab");
            if (tabHeader != null) {
                tabPane.getProperties().put(DRAG_ACTIVE_PROPERTY, true);
                @SuppressWarnings("unchecked")
                EventHandler<DragEvent>[] completion = new EventHandler[1];
                completion[0] = dragEvent -> {
                    tabHeader.removeEventFilter(DragEvent.DRAG_DONE, completion[0]);
                    handleDragDone();
                };
                tabHeader.addEventFilter(DragEvent.DRAG_DONE, completion[0]);
            }
        }
    }

    private void handleDragDone() {
        tabPane.getProperties().remove(DRAG_ACTIVE_PROPERTY);
        stopAutoScroll();
        Platform.runLater(dragFinished);
    }

    private void handleDockingDrag(DragEvent event) {
        if (event.getEventType() == DragEvent.DRAG_OVER
            && isInsideHeaderArea(event.getPickResult().getIntersectedNode())) {
            dockingDragOver = true;
            pointerSceneX = event.getSceneX();
            pointerSceneY = event.getSceneY();
            pointerScreenX = event.getScreenX();
            pointerScreenY = event.getScreenY();
            updateAutoScrollDirection();
        } else if (event.getEventType() == DragEvent.DRAG_EXITED
            || event.getEventType() == DragEvent.DRAG_DROPPED) {
            dockingDragOver = false;
            stopAutoScrollTimer();
        }
    }

    private void handleScroll(ScrollEvent event) {
        if (isInsideHeaderArea(event.getPickResult().getIntersectedNode())) {
            // TabPaneSkin performs the actual clamped scrolling. Keep the event in
            // the strip so an overflowing editor or an ancestor does not also scroll.
            event.consume();
        }
    }

    private void captureCloseGesture(MouseEvent event) {
        pendingClosedTab = null;
        closeStabilizationPending = false;
        if (event.getButton() != MouseButton.PRIMARY)
            return;

        Node actionSlot = findAncestorWithStyle(
            event.getPickResult().getIntersectedNode(),
            "editor-tab-action-slot");
        if (!(actionSlot instanceof Parent actionParent))
            return;

        Node closeIcon = actionParent.lookup(".editor-tab-close-icon");
        Node tabHeader = findAncestorWithStyle(actionSlot, "editor-tab");
        if (closeIcon == null || !closeIcon.isVisible() || tabHeader == null)
            return;

        pendingClosedTab = tabPane.getTabs().stream()
            .filter(tab -> tab.getId() != null && tab.getId().equals(tabHeader.getId()))
            .findFirst()
            .orElse(null);
        if (pendingClosedTab != null) {
            pendingCloseButtonSceneX = actionSlot.localToScene(actionSlot.getLayoutBounds()).getCenterX();
            closeStabilizationPending = true;
        }
    }

    private void handleTabsChanged(ListChangeListener.Change<? extends Tab> change) {
        scheduleAllTabsButtonInstall();
        boolean handledClose = false;
        while (change.next()) {
            if (!change.wasRemoved() || pendingClosedTab == null
                || !change.getRemoved().contains(pendingClosedTab))
                continue;

            int replacementIndex = change.getFrom();
            Tab replacement = replacementIndex < tabPane.getTabs().size()
                ? tabPane.getTabs().get(replacementIndex)
                : null;
            double closeButtonSceneX = pendingCloseButtonSceneX;
            pendingClosedTab = null;
            handledClose = true;
            Platform.runLater(() -> stabilizeReplacementCloseButton(replacement, closeButtonSceneX));
        }

        if (!handledClose) {
            scheduleSelectedTabVisibilityUpdate();
        }
    }

    private void stabilizeReplacementCloseButton(Tab replacement, double closeButtonSceneX) {
        closeStabilizationPending = false;
        if (closed || replacement == null || !tabPane.getTabs().contains(replacement)) {
            clearCloseStabilization();
            return;
        }

        Node replacementHeader = findTabHeader(replacement);
        if (replacementHeader == null) {
            clearCloseStabilization();
            return;
        }

        Node actionSlot = replacementHeader.lookup(".editor-tab-action-slot");
        Node labelNode = replacementHeader.lookup(".tab-label");
        if (actionSlot == null || !(labelNode instanceof Region label)) {
            clearCloseStabilization();
            return;
        }

        forgetRemovedStabilizedLabel();
        stabilizedLabel = label;
        stabilizedLabelMinWidth = label.getMinWidth();
        stabilizedLabelPrefWidth = label.getPrefWidth();
        stabilizedLabelMaxWidth = label.getMaxWidth();

        resizeLabelToAlign(actionSlot, label, closeButtonSceneX);
    }

    private void resizeLabelToAlign(Node actionSlot, Region label, double closeButtonSceneX) {
        double actionCenterX = actionSlot.localToScene(actionSlot.getLayoutBounds()).getCenterX();
        double targetWidth = Math.max(
            MINIMUM_STABILIZED_LABEL_WIDTH,
            label.getWidth() + closeButtonSceneX - actionCenterX);
        setFixedWidth(label, targetWidth);
        tabPane.requestLayout();
        tabPane.layout();

        double correction = closeButtonSceneX
            - actionSlot.localToScene(actionSlot.getLayoutBounds()).getCenterX();
        if (Math.abs(correction) >= 0.5) {
            setFixedWidth(label, Math.max(MINIMUM_STABILIZED_LABEL_WIDTH, targetWidth + correction));
            tabPane.requestLayout();
            tabPane.layout();
        }
    }

    private static void setFixedWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    private void forgetRemovedStabilizedLabel() {
        if (stabilizedLabel != null && stabilizedLabel.getScene() != null) {
            restoreStabilizedLabelWidth();
        } else {
            stabilizedLabel = null;
        }
    }

    private void clearCloseStabilization() {
        pendingClosedTab = null;
        closeStabilizationPending = false;
        restoreStabilizedLabelWidth();
        scheduleSelectedTabVisibilityUpdate();
    }

    private void restoreStabilizedLabelWidth() {
        if (stabilizedLabel == null)
            return;

        stabilizedLabel.setMinWidth(stabilizedLabelMinWidth);
        stabilizedLabel.setPrefWidth(stabilizedLabelPrefWidth);
        stabilizedLabel.setMaxWidth(stabilizedLabelMaxWidth);
        stabilizedLabel = null;
        tabPane.requestLayout();
    }

    private void handleSelectionChanged(Tab selectedTab) {
        if (!closeStabilizationPending && stabilizedLabel != null) {
            Node stableHeader = findAncestorWithStyle(stabilizedLabel, "editor-tab");
            if (stableHeader == null || selectedTab == null
                || !Objects.equals(selectedTab.getId(), stableHeader.getId())) {
                clearCloseStabilization();
                return;
            }
        }
        scheduleSelectedTabVisibilityUpdate();
    }

    private void updateAutoScrollDirection() {
        Node headerArea = headerArea();
        if (headerArea == null || !tabsOverflow(headerArea)) {
            stopAutoScrollTimer();
            return;
        }

        Bounds bounds = headerArea.localToScene(headerArea.getLayoutBounds());
        if (pointerSceneY < bounds.getMinY() || pointerSceneY > bounds.getMaxY()) {
            stopAutoScrollTimer();
            return;
        }

        if (pointerSceneX <= bounds.getMinX() + AUTO_SCROLL_EDGE_SIZE) {
            startAutoScroll(1);
        } else if (pointerSceneX >= bounds.getMaxX() - AUTO_SCROLL_EDGE_SIZE) {
            startAutoScroll(-1);
        } else {
            stopAutoScrollTimer();
        }
    }

    private void startAutoScroll(int direction) {
        if (autoScrollDirection == direction)
            return;

        autoScrollDirection = direction;
        lastAutoScrollNanos = 0;
        autoScrollTimer.start();
    }

    private void stopAutoScrollTimer() {
        autoScrollDirection = 0;
        autoScrollTimer.stop();
    }

    private void stopAutoScroll() {
        draggingTab = false;
        dockingDragOver = false;
        stopAutoScrollTimer();
    }

    private void scrollAtPointer() {
        Node headerArea = headerArea();
        if ((!draggingTab && !dockingDragOver) || autoScrollDirection == 0 || headerArea == null) {
            stopAutoScrollTimer();
            return;
        }

        Point2D local = headerArea.sceneToLocal(pointerSceneX, pointerSceneY);
        fireScroll(
            headerArea,
            autoScrollDirection * AUTO_SCROLL_DELTA,
            local,
            pointerScreenX,
            pointerScreenY);
    }

    private void scheduleSelectedTabVisibilityUpdate() {
        if (closed || visibilityUpdateScheduled || closeStabilizationPending || stabilizedLabel != null)
            return;

        visibilityUpdateScheduled = true;
        Platform.runLater(() -> {
            visibilityUpdateScheduled = false;
            if (!closed) {
                ensureSelectedTabVisible();
            }
        });
    }

    private void ensureSelectedTabVisible() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        Node headerArea = headerArea();
        Node headersNode = tabPane.lookup(".headers-region");
        if (selectedTab == null || headerArea == null || !(headersNode instanceof Parent headersRegion)
            || !tabsOverflow(headerArea))
            return;

        tabPane.applyCss();
        tabPane.layout();
        Node selectedHeader = headersRegion.getChildrenUnmodifiable().stream()
            .filter(header -> selectedTab.getId() != null && selectedTab.getId().equals(header.getId()))
            .findFirst()
            .orElse(null);
        if (selectedHeader == null)
            return;

        Bounds visibleBounds = headerArea.localToScene(headerArea.getLayoutBounds());
        double visibleMinX = visibleBounds.getMinX();
        double visibleMaxX = visibleBounds.getMaxX();
        Node controlButtons = tabPane.lookup(".control-buttons-tab");
        if (controlButtons != null && controlButtons.isVisible()) {
            visibleMaxX = Math.min(
                visibleMaxX,
                controlButtons.localToScene(controlButtons.getLayoutBounds()).getMinX());
        }

        Bounds selectedBounds = selectedHeader.localToScene(selectedHeader.getLayoutBounds());
        double delta = 0;
        if (selectedBounds.getMinX() < visibleMinX) {
            delta = visibleMinX - selectedBounds.getMinX();
        } else if (selectedBounds.getMaxX() > visibleMaxX) {
            delta = visibleMaxX - selectedBounds.getMaxX();
        }
        if (Math.abs(delta) < 0.5)
            return;

        var local = new Point2D(
            headerArea.getLayoutBounds().getCenterX(),
            headerArea.getLayoutBounds().getCenterY());
        Point2D screen = headerArea.localToScreen(local);
        if (screen != null) {
            fireScroll(headerArea, delta, local, screen.getX(), screen.getY());
        }
    }

    private static void fireScroll(
        Node headerArea,
        double delta,
        Point2D local,
        double screenX,
        double screenY
    ) {
        var scrollEvent = new ScrollEvent(
            ScrollEvent.SCROLL,
            local.getX(),
            local.getY(),
            screenX,
            screenY,
            false,
            false,
            false,
            false,
            false,
            false,
            0,
            delta,
            0,
            delta,
            ScrollEvent.HorizontalTextScrollUnits.NONE,
            0,
            ScrollEvent.VerticalTextScrollUnits.NONE,
            0,
            0,
            new PickResult(headerArea, local.getX(), local.getY()));
        Event.fireEvent(headerArea, scrollEvent);
    }

    private boolean tabsOverflow(Node headerArea) {
        Node headersRegion = tabPane.lookup(".headers-region");
        return headersRegion instanceof Region region
            && region.prefWidth(-1) > headerArea.getLayoutBounds().getWidth();
    }

    private Node headerArea() {
        return tabPane.lookup(".tab-header-area");
    }

    private Node findTabHeader(Tab tab) {
        Node headersNode = tabPane.lookup(".headers-region");
        if (!(headersNode instanceof Parent headersRegion))
            return null;

        return headersRegion.getChildrenUnmodifiable().stream()
            .filter(header -> Objects.equals(tab.getId(), header.getId()))
            .findFirst()
            .orElse(null);
    }

    private Node findAncestorWithStyle(Node node, String styleClass) {
        for (Node current = node; current != null && current != tabPane; current = current.getParent()) {
            if (current.getStyleClass().contains(styleClass))
                return current;
        }
        return null;
    }

    private boolean isInsideHeaderArea(MouseEvent event) {
        Node headerArea = headerArea();
        return headerArea != null
            && headerArea.localToScene(headerArea.getLayoutBounds()).contains(event.getSceneX(), event.getSceneY());
    }

    private boolean isTabHeaderEvent(MouseEvent event) {
        for (Node current = event.getPickResult().getIntersectedNode(); current != null
            && current != tabPane; current = current.getParent()) {
            if (current.getStyleClass().contains("tab"))
                return true;
        }

        return false;
    }

    private boolean isInsideHeaderArea(Node node) {
        for (Node current = node; current != null && current != tabPane; current = current.getParent()) {
            if (current.getStyleClass().contains("tab-header-area"))
                return true;
        }

        return false;
    }

    @Override
    public void close() {
        closed = true;
        stopAutoScroll();
        restoreStabilizedLabelWidth();
        tabPane.removeEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        tabPane.removeEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        tabPane.removeEventFilter(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        tabPane.removeEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        tabPane.removeEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        tabPane.removeEventFilter(MouseEvent.DRAG_DETECTED, dragDetectedHandler);
        tabPane.removeEventFilter(DragEvent.ANY, dockingDragHandler);
        tabPane.getProperties().remove(DRAG_ACTIVE_PROPERTY);
        tabPane.removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
        tabPane.getSelectionModel().selectedItemProperty().removeListener(selectionListener);
        tabPane.widthProperty().removeListener(widthListener);
        tabPane.skinProperty().removeListener(skinListener);
        tabPane.sceneProperty().removeListener(sceneListener);
        tabPane.getTabs().removeListener(tabListListener);
        uninstallAllTabsButton();
        allTabsMenu.close();
    }
}
