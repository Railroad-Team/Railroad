package dev.railroadide.railroad.ide.language.impl;

import dev.railroadide.railroad.ide.language.BaseBinaryLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.ui.ImageViewerPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;

import java.nio.file.Path;

public class ImageLanguageSupport extends BaseBinaryLanguageSupport {
    public static final ImageLanguageSupport INSTANCE = new ImageLanguageSupport();

    private ImageLanguageSupport() {
        super("image", "Image");
    }

    @Override
    public boolean supports(Path path) {
        return FileUtils.isImageFile(path);
    }

    @Override
    public EditorOpenView open(Project project, Path file) {
        return new EditorOpenView(
            new ImageViewerPane(file),
            null,
            languageId()
        );
    }
}
