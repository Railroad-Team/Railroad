package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;

/** Standard optional SST contracts. Providers may also publish their own typed keys. */
public final class SstCapabilities {
    /**
     * Reports versioned diagnostics for captured documents.
     */
    public static final CapabilityKey<DiagnosticsProvider> DIAGNOSTICS = new CapabilityKey<>("railroad:diagnostics",
        DiagnosticsProvider.class);
    /**
     * Computes completion candidates and their replacement offset.
     */
    public static final CapabilityKey<CompletionProvider> COMPLETION = new CapabilityKey<>("railroad:completion",
        CompletionProvider.class);
    /**
     * Resolves the callable signature and active parameter at the caret.
     */
    public static final CapabilityKey<SignatureHelpProvider> SIGNATURE_HELP = new CapabilityKey<>(
        "railroad:signature-help", SignatureHelpProvider.class);
    /**
     * Parses captured text into an immutable syntax tree.
     */
    public static final CapabilityKey<ParsingProvider> PARSING = new CapabilityKey<>("railroad:parsing",
        ParsingProvider.class);
    /**
     * Computes language-neutral semantic facts for captured text.
     */
    public static final CapabilityKey<SemanticProvider> SEMANTICS = new CapabilityKey<>("railroad:semantics",
        SemanticProvider.class);
    /**
     * Extracts declarations and versioned locations from a document.
     */
    public static final CapabilityKey<IndexExtractionProvider> INDEX_EXTRACTION = new CapabilityKey<>(
        "railroad:index-extraction", IndexExtractionProvider.class);
    /**
     * Finds named declarations in a captured workspace.
     */
    public static final CapabilityKey<IndexQueryProvider> INDEX_QUERY = new CapabilityKey<>("railroad:index-query",
        IndexQueryProvider.class);
    /**
     * Resolves source positions to versioned definition locations.
     */
    public static final CapabilityKey<NavigationProvider> NAVIGATION = new CapabilityKey<>("railroad:navigation",
        NavigationProvider.class);
    /**
     * Prepares and computes semantic renames as snapshot-bound workspace edits.
     */
    public static final CapabilityKey<RenameProvider> RENAME = new CapabilityKey<>("railroad:rename",
        RenameProvider.class);
    /**
     * Proposes snapshot-bound document edits for host application.
     */
    public static final CapabilityKey<CodeActionProvider> CODE_ACTIONS = new CapabilityKey<>("railroad:code-actions",
        CodeActionProvider.class);

    private SstCapabilities() {
    }
}
