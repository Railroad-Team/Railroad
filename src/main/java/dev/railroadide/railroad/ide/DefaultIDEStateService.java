package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroadpluginapi.dto.Document;
import dev.railroadide.railroadpluginapi.dto.Project;
import dev.railroadide.railroadpluginapi.events.FileEvent;
import dev.railroadide.railroadpluginapi.events.ProjectEvent;
import dev.railroadide.railroadpluginapi.services.IDEStateService;
import lombok.Getter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class DefaultIDEStateService implements IDEStateService {
    private static DefaultIDEStateService instance;

    private DefaultIDEStateService() {
        Railroad.EVENT_BUS.subscribe(ProjectEvent.class, event -> {
            if (event.isOpened()) {
                setCurrentProject_internal(event.project());
            } else if (event.isClosed()) {
                setCurrentProject_internal(null);
            }
        });

        Railroad.EVENT_BUS.subscribe(FileEvent.class, event -> {
            Document document = event.file();
            if (event.isOpened()) {
                openDocument_internal(document);
            } else if (event.isClosed()) {
                closeDocument_internal(document);
            } else if (event.isActivated()) {
                setActiveDocument_internal(document);
            }
        });
    }

    public static synchronized DefaultIDEStateService getInstance() {
        if (instance == null) {
            instance = new DefaultIDEStateService();
        }

        return instance;
    }

    private final Map<Document, Long> openDocuments = new HashMap<>();
    private final Map<Path, Long> recentFiles = new HashMap<>();
    private Project currentProject;
    private long openedProjectAtMillis = -1L;
    private Document activeDocument;

    private void setCurrentProject_internal(Project project) {
        this.currentProject = project;
        this.openedProjectAtMillis = project != null ? System.currentTimeMillis() : -1L;
    }

    private void openDocument_internal(Document document) {
        openDocuments.put(document, System.currentTimeMillis());
    }

    private void closeDocument_internal(Document document) {
        openDocuments.remove(document);
        recentFiles.put(document.getPath(), System.currentTimeMillis());

        if (activeDocument != null && openDocuments.isEmpty()) {
            activeDocument = null;
        } else if (activeDocument != null && activeDocument.equals(document)) {
            activeDocument = null;
        }
    }

    private void setActiveDocument_internal(Document document) {
        if (openDocuments.containsKey(document)) {
            this.activeDocument = document;
        } else {
            this.activeDocument = null;
        }
    }

    @Override
    public void setActiveDocument(Document document) {
        // NO-OP
        // TODO
    }

    @Override
    public void setOpenDocuments(List<Document> list) {
        // NO-OP
        // TODO
    }

    @Override
    public void clearOpenDocuments() {
        // NO-OP
        // TODO
    }

    @Override
    public long getProjectOpenedTimestamp() {
        return openedProjectAtMillis;
    }

    @Override
    public long getDocumentOpenedTimestamp(Document document) {
        return openDocuments.getOrDefault(document, -1L);
    }

    public List<Document> getOpenDocuments() {
        return List.copyOf(openDocuments.keySet());
    }

    @Override
    public Document getActiveDocument() {
        return activeDocument;
    }

    @Override
    public void setCurrentProject(Project project) {
        // NO-OP
        // TODO
    }

    @Override
    public void openDocument(Document document) {
        // NO-OP
        // TODO
    }

    @Override
    public void closeDocument(Document document) {
        // NO-OP
        // TODO
    }
}