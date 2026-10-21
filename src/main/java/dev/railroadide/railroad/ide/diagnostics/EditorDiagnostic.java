package dev.railroadide.railroad.ide.diagnostics;

import javax.tools.Diagnostic;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.Location.BinaryLocation;
import dev.railroadide.railroad.ide.sst.document.api.Location.TextLocation;

import java.util.Locale;

/**
 * Immutable diagnostic representation understood by the editor UI.
 *
 * @param <L> the location type identifying the affected document revision and range
 */
public sealed interface EditorDiagnostic<L extends Location<?>>
    permits EditorDiagnostic.TextEditorDiagnostic, EditorDiagnostic.BinaryEditorDiagnostic {
    /**
     * Returns the document revision and range affected by this diagnostic.
     *
     * @return the diagnostic location
     */
    public L location();

    /**
     * Returns the diagnostic severity.
     *
     * @return the severity category
     */
    public Diagnostic.Kind kind();

    /**
     * Returns the provider's diagnostic identifier.
     *
     * @return the diagnostic code
     */
    public String code();

    /**
     * Returns the diagnostic message for the requested locale.
     *
     * @param locale the requested locale, or {@code null} for the default
     * @return the human-readable diagnostic message
     */
    public String message(Locale locale);

    /**
     * A diagnostic referring to a text document revision.
     *
     * @param location the affected text range and document revision
     * @param kind the diagnostic severity
     * @param code the provider's diagnostic identifier
     * @param message the human-readable diagnostic message
     */
    public final record TextEditorDiagnostic(
        TextLocation location,
        Diagnostic.Kind kind,
        String code,
        String message
    ) implements EditorDiagnostic<TextLocation> {

        @Override
        public String message(Locale locale) {
            return this.message;
        }
    }

    /**
     * A diagnostic referring to a binary document revision.
     *
     * @param location the affected byte range and document revision
     * @param kind the diagnostic severity
     * @param code the provider's diagnostic identifier
     * @param message the human-readable diagnostic message
     */
    public final record BinaryEditorDiagnostic(
        BinaryLocation location,
        Diagnostic.Kind kind,
        String code,
        String message
    ) implements EditorDiagnostic<BinaryLocation> {

        @Override
        public String message(Locale locale) {
            return this.message;
        }
    }
}
