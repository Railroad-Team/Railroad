package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.Services;
import javafx.scene.layout.BorderPane;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * A pane component for displaying Java compilation diagnostics (errors and warnings).
 * Provides a structured layout with location information and diagnostic messages.
 * Automatically applies styling based on the diagnostic kind (error or warning).
 */
public class DiagnosticPane extends BorderPane {
    /**
     * Constructs a new DiagnosticPane for a single diagnostic.
     *
     * @param diagnostic the diagnostic to display
     */
    public DiagnosticPane(EditorDiagnostic diagnostic) {
        this(diagnostic, null);
    }

    /**
     * Constructs a pane for a diagnostic with navigation to its source file.
     *
     * @param diagnostic the diagnostic to display
     * @param sourcePath the source file, or {@code null} when navigation is unavailable
     */
    public DiagnosticPane(EditorDiagnostic diagnostic, Path sourcePath) {
        var message = diagnostic.message(null);

        var line = 0;
        var column = diagnostic.location().range().start;

        if (diagnostic instanceof TextEditorDiagnostic textDiagnostic) {
            line = textDiagnostic.location().line() + 1;
            column = textDiagnostic.location().column() + 1;
        }

        var messageText = new Text(message);
        var locationText = new Text("Line " + line + ", Column " + column);

        setTop(locationText);
        setCenter(messageText);
        getStyleClass().add("diagnostic-pane");
        if (diagnostic.kind() == Diagnostic.Kind.ERROR) {
            getStyleClass().add("error");
        } else {
            getStyleClass().add("warning");
        }
        if (sourcePath != null) {
            installSourceNavigation(sourcePath);
        }
    }

    /**
     * Constructs a new DiagnosticPane for multiple diagnostics.
     *
     * @param diagnostics the collection of diagnostics to display
     */
    public DiagnosticPane(Collection<Diagnostic<? extends JavaFileObject>> diagnostics) {
        var message = new StringBuilder();
        for (var diagnostic : diagnostics) {
            message.append(diagnostic.getMessage(null)).append("\n");
        }

        var messageText = new Text(message.toString());
        var locationText = new Text("Multiple errors");

        setTop(locationText);
        setCenter(messageText);
        getStyleClass().add("diagnostic-pane");
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)) {
            getStyleClass().add("error");
        } else {
            getStyleClass().add("warning");
        }
    }

    /**
     * Constructs a new DiagnosticPane for multiple diagnostics using varargs.
     *
     * @param diagnostics the diagnostics to display
     */
    @SafeVarargs
    public DiagnosticPane(Diagnostic<? extends JavaFileObject>... diagnostics) {
        this(List.of(diagnostics));
    }

    private void installSourceNavigation(Path sourcePath) {
        setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.isConsumed())
                return;

            if (event.getClickCount() == 1) {
                Services.EDITOR_TAB_MANAGER.openPreview(sourcePath);
            } else if (event.getClickCount() == 2) {
                Services.EDITOR_TAB_MANAGER.open(sourcePath);
                event.consume();
            }
        });
    }
}
