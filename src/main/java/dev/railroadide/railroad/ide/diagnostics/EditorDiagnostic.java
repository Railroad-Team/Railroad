package dev.railroadide.railroad.ide.diagnostics;

import javax.tools.Diagnostic;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.Location.BinaryLocation;
import dev.railroadide.railroad.ide.sst.document.api.Location.TextLocation;

import java.util.Locale;

/**
 * Immutable diagnostic representation understood by the editor UI.
 */
public sealed interface EditorDiagnostic<L extends Location<?>>
    permits EditorDiagnostic.TextEditorDiagnostic, EditorDiagnostic.BinaryEditorDiagnostic
{
    public L location();

    public Diagnostic.Kind kind();

    public String code();

    public String message(Locale locale);

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
