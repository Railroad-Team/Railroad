package dev.railroadide.railroad.ide.diagnostics;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Locale;

/**
 * Immutable diagnostic representation understood by the editor UI.
 */
public record EditorDiagnostic(
    Diagnostic.Kind kind,
    int start,
    int end,
    long line,
    long column,
    String message,
    String code,
    JavaFileObject source
) implements Diagnostic<JavaFileObject> {
    @Override
    public Diagnostic.Kind getKind() {
        return kind;
    }

    @Override
    public JavaFileObject getSource() {
        return source;
    }

    @Override
    public long getPosition() {
        return start;
    }

    @Override
    public long getStartPosition() {
        return start;
    }

    @Override
    public long getEndPosition() {
        return end;
    }

    @Override
    public long getLineNumber() {
        return line;
    }

    @Override
    public long getColumnNumber() {
        return column;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage(Locale locale) {
        return message;
    }
}
