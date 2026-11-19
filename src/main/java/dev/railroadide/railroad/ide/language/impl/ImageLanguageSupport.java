package dev.railroadide.railroad.ide.language.impl;

import dev.railroadide.railroad.ide.language.BaseBinaryLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.ui.ImageViewerPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;

import java.nio.file.Path;
import dev.railroadide.railroad.ide.sst.provider.api.LanguageMatcher;

/**
 * Opens recognized image formats in an image viewer.
 */
public class ImageLanguageSupport extends BaseBinaryLanguageSupport {
    /**
     * Shared instance of this language support implementation.
     */
    public static final ImageLanguageSupport INSTANCE = new ImageLanguageSupport();

    private ImageLanguageSupport() {
        super("image", "Image");
    }

    @Override
    public LanguageMatcher matcher() {
        return LanguageMatcher.content(input -> FileUtils.isImageFile(input.path()));
    }

    @Override
    public EditorOpenView open(Project project, DocumentId documentId, Path file) {
        return new EditorOpenView(
            new ImageViewerPane(file),
            null,
            languageId());
    }
}
