package dev.railroadide.railroad.project;

import dev.railroadide.railroad.ide.IDELayoutState;
import dev.railroadide.railroad.ide.ui.editor.EditorTabSessionState;
import dev.railroadide.railroad.ide.ui.editor.EditorWorkspaceSessionState;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class ProjectConfig {
    private List<Path> openDocuments;
    private Path activeDocument;
    private List<EditorTabSessionState> editorTabs;
    private EditorWorkspaceSessionState editorWorkspace;
    private IDELayoutState ideLayoutState;
}
