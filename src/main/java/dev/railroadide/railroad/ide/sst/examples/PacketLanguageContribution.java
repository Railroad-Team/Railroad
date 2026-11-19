package dev.railroadide.railroad.ide.sst.examples;

import dev.railroadide.railroad.ide.diagnostics.*;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.BinaryEditorDiagnostic;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.semantic.api.SimpleSymbol;
import java.util.List;
import java.util.Set;

/** Minimal binary provider: a packet begins with the two ASCII bytes "RR". */
public final class PacketLanguageContribution implements LanguageContribution {
    /**
     * Namespaced identity of the example binary packet language.
     */
    public static final LanguageId ID = new LanguageId("example:packet");

    @Override
    public LanguageId id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Packet";
    }

    @Override
    public LanguageMatcher matcher() {
        return LanguageMatcher.extension("packet");
    }

    @Override
    public ContentKind contentKind() {
        return ContentKind.BINARY;
    }

    @Override
    public Set<CapabilityKey<?>> supportedCapabilities() {
        return Set.of(SstCapabilities.DIAGNOSTICS, SstCapabilities.INDEX_EXTRACTION);
    }

    @Override
    public LanguageSession openSession(LanguageSessionContext context) {
        DiagnosticsProvider<BinaryEditorDiagnostic> diagnostics = snapshot -> {
            BinaryDocumentSnapshot binary = requireBinary(snapshot);
            var bytes = binary.bytes();
            if (bytes.remaining() >= 2 && bytes.get(0) == 'R' && bytes.get(1) == 'R')
                return List.of();
            return List.of(new BinaryEditorDiagnostic(Location.BinaryLocation.from(binary, 0,
                Math.min(2, bytes.remaining())), DiagnosticSeverity.ERROR, "packet.header",
                "Expected RR packet header", ID.value(), DiagnosticOrigin.SYNTAX, Set.of(), List.of(), null));
        };
        return new LanguageSession(CapabilityRegistry.builder()
            .add(SstCapabilities.DIAGNOSTICS, diagnostics)
            .add(SstCapabilities.INDEX_EXTRACTION, request -> {
                request.checkActive();
                var binary = requireBinary(request.documentSnapshot());
                var symbol = new SimpleSymbol(() -> "example:packet.header", "header", null, null);
                var location = Location.BinaryLocation.from(binary, 0, Math.min(2, binary.bytes().remaining()));
                request.checkActive();
                return new FeatureResult<>(request, List.of(new SymbolOccurrence(symbol, location)));
            }).build());
    }

    private static BinaryDocumentSnapshot requireBinary(DocumentSnapshot snapshot) {
        if (!(snapshot instanceof BinaryDocumentSnapshot binary) || !snapshot.languageId().equals(ID.value()))
            throw new IllegalArgumentException("Packet requires a packet binary snapshot");
        return binary;
    }
}
