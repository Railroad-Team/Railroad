package dev.railroadide.railroad.ide.diagnostics;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Locale;

/**
 * Immutable diagnostic representation understood by the editor UI.
 *
 * @param kind diagnostic severity category
 * @param start inclusive start offset in the source
 * @param end exclusive end offset in the source
 * @param line one-based source line, or the diagnostic unknown-position value
 * @param column one-based source column, or the diagnostic unknown-position value
 * @param message human-readable diagnostic message
 * @param code diagnostic identifier
 * @param source source file associated with the diagnostic
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
