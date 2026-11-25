package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.language.impl.PlainTextLanguageSupport;
import dev.railroadide.railroad.ide.language.index.LanguageNotSupportedException;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import dev.railroadide.railroad.ide.sst.document.api.ContentKind;
import javafx.scene.control.Label;

/**
 * Host presentation and storage adapter over the single language runtime.
 */
public final class LanguageSupportRegistry {
    private static final LanguageRuntime RUNTIME = new LanguageRuntime();

    private LanguageSupportRegistry() {
    }

    /**
     * Returns the shared runtime used by host and plugin language contributions.
     *
     * @return the application language runtime
     */
    public static LanguageRuntime runtime() {
        return RUNTIME;
    }

    /**
     * Registers host language support under the Railroad owner.
     *
     * @param support language presentation and session factory to register
     */
    public static void register(LanguageSupport support) {
        RUNTIME.register("railroad", support);
    }

    /**
     * Registers each host language in collection order. Earlier registrations remain if a later one fails.
     *
     * @param supports language supports to register
     */
    public static void registerAll(Collection<? extends LanguageSupport> supports) {
        supports.forEach(LanguageSupportRegistry::register);
    }

    /**
     * Resolves a language and adapts independent contributions to host editor support.
     *
     * @param languageId host storage identifier or namespaced contribution identifier
     * @return the host language support, or empty if unregistered
     */
    public static Optional<LanguageSupport> get(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return RUNTIME.all().stream().filter(contribution -> contribution.id().value().equals(languageId)
            || hostLanguageId(contribution).equals(languageId)).findFirst().map(LanguageSupportRegistry::present);
    }

    /**
     * Resolves required language support.
     *
     * @param languageId host storage identifier or namespaced contribution identifier
     * @return the registered host language support
     * @throws LanguageNotSupportedException if the language is not registered
     */
    public static LanguageSupport getExpected(String languageId) {
        return get(languageId).orElseThrow(() -> new LanguageNotSupportedException(languageId));
    }

    /**
     * Checks whether language support is registered.
     *
     * @param languageId host storage identifier or namespaced contribution identifier
     * @return whether a matching registration exists
     */
    public static boolean contains(String languageId) {
        return get(languageId).isPresent();
    }

    /**
     * Selects host language support using runtime file recognition.
     *
     * @param path file to recognize
     * @return the matching language support, or empty if none matches
     */
    public static Optional<LanguageSupport> find(Path path) {
        return RUNTIME.find(path).map(LanguageSupportRegistry::present);
    }

    /**
     * Resolves the storage language identifier, falling back to binary or plain text when no contribution matches.
     *
     * @param path file to recognize
     * @return the selected or fallback host language identifier
     */
    public static String resolveLanguageId(Path path) {
        Objects.requireNonNull(path, "path");
        return find(path).map(LanguageSupport::languageId)
            .orElseGet(() -> Files.isRegularFile(path) && FileUtils.isBinaryFile(path)
                ? "binary"
                : PlainTextLanguageSupport.INSTANCE.languageId());
    }

    /**
     * Resolves the storage language identifier for known content, which need not exist on disk, falling back to binary
     * or plain text from that content when no contribution matches.
     *
     * @param path file location to recognize
     * @param content file content used for the fallback
     * @return the selected or fallback host language identifier
     */
    public static String resolveLanguageId(Path path, byte[] content) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
        return find(path).map(LanguageSupport::languageId)
            .orElseGet(() -> FileUtils.isBinaryContent(content, content.length)
                ? "binary"
                : PlainTextLanguageSupport.INSTANCE.languageId());
    }

    /**
     * Returns host presentations for all registered contributions.
     *
     * @return an immutable list ordered by contribution ID
     */
    public static List<LanguageSupport> all() {
        return RUNTIME.all().stream().map(LanguageSupportRegistry::present).toList();
    }

    /**
     * Clears all language registrations and closes their project sessions.
     */
    public static void clear() {
        RUNTIME.clear();
    }

    /**
     * Unregisters a language and closes its sessions; unknown identifiers have no effect.
     *
     * @param languageId host storage identifier or namespaced contribution identifier
     */
    public static void unregister(String languageId) {
        get(languageId).ifPresent(support -> RUNTIME.unregister(support.id()));
    }

    /**
     * Built-in storage names predate namespaced registration; independent languages retain their full ID.
     *
     * @param contribution contribution whose storage identity is needed
     * @return the host storage name or the independent contribution's namespaced ID
     */
    public static String hostLanguageId(LanguageContribution contribution) {
        return contribution instanceof LanguageSupport support ? support.languageId() : contribution.id().value();
    }

    private static LanguageSupport present(LanguageContribution contribution) {
        return contribution instanceof LanguageSupport support ? support : new ContributionEditor(contribution);
    }

    private record ContributionEditor(LanguageContribution contribution) implements LanguageSupport {
        @Override
        public LanguageId id() {
            return contribution.id();
        }

        @Override
        public String languageId() {
            return contribution.id().value();
        }

        @Override
        public String displayName() {
            return contribution.displayName();
        }

        @Override
        public Set<String> fileExtensions() {
            return Set.of();
        }

        @Override
        public LanguageMatcher matcher() {
            return contribution.matcher();
        }

        @Override
        public int priority() {
            return contribution.priority();
        }

        @Override
        public Set<CapabilityKey<?>> supportedCapabilities() {
            return contribution.supportedCapabilities();
        }

        @Override
        public LanguageSession openSession(LanguageSessionContext context) {
            return contribution.openSession(context);
        }

        @Override
        public ContentKind contentKind() {
            return contribution.contentKind();
        }

        @Override
        public EditorOpenView open(Project project, DocumentId documentId, Path file) {
            if (contentKind() != ContentKind.TEXT)
                return new EditorOpenView(new Label("Binary document: " + file.getFileName()),
                    null, languageId());

            var editor = new CodeEditorPane(project, documentId, file,
                CodeEditorConfig.fromLanguageSupport(project, file, this, null)) {
                @Override
                protected boolean shouldRequestSignatureHelp(String text, int caret, boolean textChanged) {
                    return textChanged;
                }
            };

            return new EditorOpenView(editor, editor, languageId());
        }
    }
}
