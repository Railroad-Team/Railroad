package dev.railroadide.railroad.ide.language.impl;

import dev.railroadide.railroad.ide.language.BaseLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.syntaxhighlighting.JsonSyntaxHighlighting;
import dev.railroadide.railroad.ide.ui.JsonCodeEditorPane;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;
import java.util.Set;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.language.LanguageCapabilities;

/**
 * Provides JSON text editing with JSON syntax highlighting.
 */
public final class JsonLanguageSupport extends BaseLanguageSupport {
    /**
     * Creates language support for JSON source files.
     */
    public JsonLanguageSupport() {
        super("json", "JSON", Set.of("json"));
    }

    @Override
    public Set<CapabilityKey<?>> supportedCapabilities() {
        return Set.of(LanguageCapabilities.HIGHLIGHTING);
    }

    @Override
    public LanguageSession openSession(LanguageSessionContext context) {
        return new LanguageSession(CapabilityRegistry.builder()
            .add(LanguageCapabilities.HIGHLIGHTING, JsonSyntaxHighlighting::computeHighlighting).build());
    }

    @Override
    public EditorOpenView open(Project project, DocumentId documentId, Path file) {
        var editor = new JsonCodeEditorPane(
            project,
            documentId,
            file,
            CodeEditorConfig.fromLanguageSupport(project, file, this, JsonSyntaxHighlighting::computeHighlighting));
        return new EditorOpenView(
            editor,
            editor,
            languageId());
    }
}
