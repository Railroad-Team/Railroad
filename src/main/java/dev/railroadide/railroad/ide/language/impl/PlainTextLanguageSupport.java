package dev.railroadide.railroad.ide.language.impl;

import dev.railroadide.railroad.ide.language.BaseLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;
import java.util.Set;

public class PlainTextLanguageSupport extends BaseLanguageSupport {
    public static final PlainTextLanguageSupport INSTANCE = new PlainTextLanguageSupport();

    private PlainTextLanguageSupport() {
        super("plaintext", "Plain Text", Set.of("txt"));
    }

    @Override
    public EditorOpenView open(Project project, Path file) {
        var editor = new TextEditorPane(file, languageId());
        return new EditorOpenView(
            editor,
            editor,
            languageId()
        );
    }
}
