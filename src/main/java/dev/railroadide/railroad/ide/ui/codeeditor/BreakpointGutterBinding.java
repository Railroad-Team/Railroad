package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.debug.breakpoint.BreakpointEvent;
import dev.railroadide.railroad.debug.breakpoint.BreakpointListener;
import dev.railroadide.railroad.debug.breakpoint.BreakpointService;
import dev.railroadide.railroad.debug.breakpoint.SourceBreakpoint;
import dev.railroadide.railroad.debug.source.DebugSource;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.css.PseudoClass;
import javafx.scene.shape.Circle;
import org.jspecify.annotations.Nullable;
import org.fxmisc.richtext.CodeArea;
import org.reactfx.Subscription;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Connects a gutter's breakpoint markers and primary clicks to the shared breakpoint service.
 * Construct and close on the JavaFX application thread; service events may arrive on any thread.
 */
public final class BreakpointGutterBinding implements AutoCloseable {
    private static final String COLUMN_ID = "breakpoints";
    private final EditorGutter gutter;
    private final Supplier<Path> filePath;
    private final BreakpointService service;
    private final BreakpointListener listener = this::onBreakpointEvent;
    private final Subscription textSubscription;
    private BreakpointService.DocumentTracking documentTracking;
    private Path trackedPath;
    private String previousText;
    private boolean closed;

    /**
     * Installs a breakpoint column and observes changes made by other editor views.
     *
     * @param gutter gutter to decorate
     * @param editor editor whose text changes move breakpoint locations
     * @param filePath supplier of the editor's current backing path, including after Save As
     * @param service shared breakpoint state
     */
    public BreakpointGutterBinding(
        EditorGutter gutter,
        CodeArea editor,
        Supplier<Path> filePath,
        BreakpointService service
    ) {
        this.gutter = Objects.requireNonNull(gutter, "gutter");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.service = Objects.requireNonNull(service, "service");
        trackedPath = filePath.get().toAbsolutePath().normalize();
        previousText = editor.getText();
        documentTracking = service.trackDocument(trackedPath, previousText);
        textSubscription = editor.multiPlainChanges().subscribe(changes -> {
            Path currentPath = filePath.get().toAbsolutePath().normalize();
            if (!currentPath.equals(trackedPath)) {
                documentTracking.close();
                trackedPath = currentPath;
                documentTracking = service.trackDocument(trackedPath, previousText);
            }
            String text = editor.getText();
            documentTracking.update(previousText, text, changes.stream()
                .map(change -> new BreakpointService.TextChange(change.getPosition(), change.getRemoved(),
                    change.getInserted()))
                .toList());
            previousText = text;
        });
        service.addListener(listener);
        gutter.addColumn(new GutterColumn(COLUMN_ID, 16, this::createMarker, (line, event) -> {
            if (!closed && event.getButton() == MouseButton.PRIMARY) {
                service.toggle(filePath.get(), line);
                event.consume();
            }
        }));
    }

    private @Nullable Node createMarker(int line) {
        SourceBreakpoint breakpoint = service.get(filePath.get(), line).orElse(null);
        if (breakpoint == null)
            return null;

        var marker = new Circle(5);
        marker.getStyleClass().add("editor-breakpoint-marker");
        marker.pseudoClassStateChanged(PseudoClass.getPseudoClass("disabled"), !breakpoint.enabled());
        Tooltip.install(marker, new Tooltip("Breakpoint at line " + line + " (click to remove)"));
        return marker;
    }

    private void onBreakpointEvent(BreakpointEvent event) {
        SourceBreakpoint breakpoint = switch (event) {
            case BreakpointEvent.Added added -> added.breakpoint();
            case BreakpointEvent.Removed removed -> removed.breakpoint();
            case BreakpointEvent.Changed changed -> changed.breakpoint();
        };
        JavaFXUtils.runOnApplicationThread(() -> {
            if (!closed && breakpoint.source().matches(new DebugSource.FileSource(filePath.get()))) {
                gutter.refresh();
            }
        });
    }

    /** Removes the service listener and gutter column when the editor closes. */
    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        textSubscription.unsubscribe();
        documentTracking.close();
        service.removeListener(listener);
        gutter.removeColumn(COLUMN_ID);
    }
}
