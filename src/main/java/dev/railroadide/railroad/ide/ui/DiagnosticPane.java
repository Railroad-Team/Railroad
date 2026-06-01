package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ui.RRTextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Collection;
import java.util.List;

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
    public DiagnosticPane(Diagnostic<? extends JavaFileObject> diagnostic) {
        var message = diagnostic.getMessage(null);
        var line = diagnostic.getLineNumber();
        var column = diagnostic.getColumnNumber();

        var messageText = createMessageArea(message);
        var locationText = new Text("Line " + line + ", Column " + column);

        setTop(locationText);
        setCenter(messageText);
        getStyleClass().add("diagnostic-pane");
        if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
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

        var messageText = createMessageArea(message.toString().stripTrailing());
        var locationText = new Text("Multiple errors");

        setTop(locationText);
        setCenter(messageText);
        getStyleClass().add("diagnostic-pane");
        if (diagnostics.stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
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

    private static RRTextArea createMessageArea(String message) {
        var messageArea = new RRTextArea();
        messageArea.setText(message);
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setFocusTraversable(true);
        messageArea.setPrefColumnCount(50);
        messageArea.setPrefRowCount(Math.clamp(message.lines().toArray().length + 1, 2, 8));
        messageArea.getStyleClass().add("diagnostic-pane-message");
        return messageArea;
    }
}
