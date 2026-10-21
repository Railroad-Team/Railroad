package dev.railroadide.railroad.ide.language.impl;

import dev.railroadide.railroad.ide.language.BaseLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;
import java.util.Set;

/**
 * Opens Markdown files in a rendered preview pane.
 */
public final class MarkdownLanguageSupport extends BaseLanguageSupport {
    /**
     * Creates language support for Markdown source files.
     */
    public MarkdownLanguageSupport() {
        super("markdown", "Markdown", Set.of("md", "markdown"));
    }

    @Override
    public EditorOpenView open(Project project, Path file) {
        var editorPane = new MarkdownPreviewPane(file, project);
        return new EditorOpenView(editorPane, null, languageId());
    }
}
