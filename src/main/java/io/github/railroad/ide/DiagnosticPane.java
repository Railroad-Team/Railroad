package io.github.railroad.ide;

import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Collection;
import java.util.List;

public class DiagnosticPane extends BorderPane {
    public DiagnosticPane(Diagnostic<? extends JavaFileObject> diagnostic) {
        var message = diagnostic.getMessage(null);
        var line = diagnostic.getLineNumber();
        var column = diagnostic.getColumnNumber();

        var messageText = new Text(message);
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
        if (diagnostics.stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
            getStyleClass().add("error");
        } else {
            getStyleClass().add("warning");
        }
    }

    @SafeVarargs
    public DiagnosticPane(Diagnostic<? extends JavaFileObject>... diagnostics) {
        this(List.of(diagnostics));
    }
}
