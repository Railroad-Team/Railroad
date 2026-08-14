package dev.railroadide.railroad.project;

import dev.railroadide.railroad.ide.IDELayoutState;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

// TODO: We need to switch Path out for a DocumentIdentity type class
@Data
public class ProjectConfig {
    private List<Path> openDocuments;
    private Path activeDocument;
    private IDELayoutState ideLayoutState;
}
