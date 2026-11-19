package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.Location.BinaryLocation;
import dev.railroadide.railroad.ide.sst.document.api.Location.TextLocation;
import dev.railroadide.railroad.ide.sst.document.api.RelatedLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, language-neutral diagnostic published by all editor-facing providers.
 * Locations identify the exact document revision; a result for an older revision must not be displayed.
 * Source and origin distinguish overlapping reports without silently discarding their provenance.
 * Opaque data is a provider-owned immutable string, interpreted only by its originating provider.
 *
 * @param <L> the location type identifying the affected document revision and range
 */
public sealed interface EditorDiagnostic<L extends Location<?>>
    permits EditorDiagnostic.TextEditorDiagnostic, EditorDiagnostic.BinaryEditorDiagnostic {
    /**
     * Identifies the affected range and document revision.
     *
     * @return the versioned diagnostic location
     */
    public L location();

    /**
     * Returns the severity used to present this report.
     *
     * @return the diagnostic severity
     */
    public DiagnosticSeverity kind();

    /**
     * Returns the reporting provider's diagnostic code.
     *
     * @return the provider-defined code, or null when absent
     */
    public String code();

    /**
     * Returns the diagnostic message for presentation.
     *
     * @param locale requested locale; implementations may return their stored message unchanged
     * @return the diagnostic message
     */
    public String message(Locale locale);

    /**
     * Returns the stable identifier of the provider that produced this report.
     *
     * @return the nonblank provider identifier
     */
    public String source();

    /**
     * Identifies the analysis stage that produced the report.
     *
     * @return the diagnostic origin
     */
    public DiagnosticOrigin origin();

    /**
     * Returns additional presentation classifications.
     *
     * @return an immutable set of diagnostic tags
     */
    public Set<DiagnosticTag> tags();

    /**
     * Returns locations that provide supporting diagnostic context.
     *
     * @return an immutable list of related locations
     */
    public List<RelatedLocation> relatedInformation();

    /**
     * Returns provider-owned opaque data, or null when no data is attached.
     *
     * @return opaque provider data, or null when absent
     */
    public String data();

    /**
     * A diagnostic referring to a text document revision.
     *
     * @param location affected range tied to an exact document revision
     * @param kind non-null severity used to present the diagnostic
     * @param code provider-defined diagnostic code, or null if none is available
     * @param message nonblank diagnostic message
     * @param source nonblank stable identifier of the reporting provider
     * @param origin analysis stage that produced the diagnostic
     * @param tags presentation tags, copied into an immutable set
     * @param relatedInformation related versioned locations, copied into an immutable list
     * @param data provider-owned opaque data, or null when absent
     */
    public record TextEditorDiagnostic(
        TextLocation location,
        DiagnosticSeverity kind,
        String code,
        String message,
        String source,
        DiagnosticOrigin origin,
        Set<DiagnosticTag> tags,
        List<RelatedLocation> relatedInformation,
        String data
    ) implements EditorDiagnostic<TextLocation> {
        /**
         * Creates a diagnostic, validating its required fields and copying its collections.
         *
         * @param location affected range tied to an exact document revision
         * @param kind non-null severity used to present the diagnostic
         * @param code provider-defined diagnostic code, or null if none is available
         * @param message nonblank diagnostic message
         * @param source nonblank stable identifier of the reporting provider
         * @param origin analysis stage that produced the diagnostic
         * @param tags presentation tags, copied into an immutable set
         * @param relatedInformation related versioned locations, copied into an immutable list
         * @param data provider-owned opaque data, or null when absent
         * @throws IllegalArgumentException if the message or provider identifier is blank
         */
        public TextEditorDiagnostic {
            validate(location, kind, message, source, origin);
            tags = Set.copyOf(tags);
            relatedInformation = List.copyOf(relatedInformation);
        }

        /**
         * Creates a semantic diagnostic with source {@code unspecified}, no tags or related locations, and no opaque
         * data.
         *
         * @param location affected range tied to an exact document revision
         * @param kind non-null severity used to present the diagnostic
         * @param code provider-defined diagnostic code, or null if none is available
         * @param message nonblank diagnostic message
         */
        public TextEditorDiagnostic(TextLocation location, DiagnosticSeverity kind, String code, String message) {
            this(location, kind, code, message, "unspecified", DiagnosticOrigin.SEMANTIC, Set.of(), List.of(), null);
        }

        @Override
        public String message(Locale locale) {
            return message;
        }
    }

    /**
     * A diagnostic referring to a binary document revision.
     *
     * @param location affected range tied to an exact document revision
     * @param kind non-null severity used to present the diagnostic
     * @param code provider-defined diagnostic code, or null if none is available
     * @param message nonblank diagnostic message
     * @param source nonblank stable identifier of the reporting provider
     * @param origin analysis stage that produced the diagnostic
     * @param tags presentation tags, copied into an immutable set
     * @param relatedInformation related versioned locations, copied into an immutable list
     * @param data provider-owned opaque data, or null when absent
     */
    public record BinaryEditorDiagnostic(
        BinaryLocation location,
        DiagnosticSeverity kind,
        String code,
        String message,
        String source,
        DiagnosticOrigin origin,
        Set<DiagnosticTag> tags,
        List<RelatedLocation> relatedInformation,
        String data
    ) implements EditorDiagnostic<BinaryLocation> {
        /**
         * Creates a diagnostic, validating its required fields and copying its collections.
         *
         * @param location affected range tied to an exact document revision
         * @param kind non-null severity used to present the diagnostic
         * @param code provider-defined diagnostic code, or null if none is available
         * @param message nonblank diagnostic message
         * @param source nonblank stable identifier of the reporting provider
         * @param origin analysis stage that produced the diagnostic
         * @param tags presentation tags, copied into an immutable set
         * @param relatedInformation related versioned locations, copied into an immutable list
         * @param data provider-owned opaque data, or null when absent
         * @throws IllegalArgumentException if the message or provider identifier is blank
         */
        public BinaryEditorDiagnostic {
            validate(location, kind, message, source, origin);
            tags = Set.copyOf(tags);
            relatedInformation = List.copyOf(relatedInformation);
        }

        /**
         * Creates a semantic diagnostic with source {@code unspecified}, no tags or related locations, and no opaque
         * data.
         *
         * @param location affected range tied to an exact document revision
         * @param kind non-null severity used to present the diagnostic
         * @param code provider-defined diagnostic code, or null if none is available
         * @param message nonblank diagnostic message
         */
        public BinaryEditorDiagnostic(BinaryLocation location, DiagnosticSeverity kind, String code, String message) {
            this(location, kind, code, message, "unspecified", DiagnosticOrigin.SEMANTIC, Set.of(), List.of(), null);
        }

        @Override
        public String message(Locale locale) {
            return message;
        }
    }

    private static void validate(
        Location<?> location,
        DiagnosticSeverity kind,
        String message,
        String source,
        DiagnosticOrigin origin
    ) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(origin, "origin");
        if (message.isBlank())
            throw new IllegalArgumentException("Diagnostic message must not be blank");
        if (source.isBlank())
            throw new IllegalArgumentException("Diagnostic source must not be blank");
    }
}
