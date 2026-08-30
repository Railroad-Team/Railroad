package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;

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
        var message = diagnostic.message(null);

        var line = 0;
        var column = diagnostic.location().range().start;

        if (diagnostic instanceof TextEditorDiagnostic textDiagnostic) {
            line = (int) textDiagnostic.location().line();
            column = (int) textDiagnostic.location().column();
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
}
