package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.plugin.spi.services.IDEStateService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
public class DefaultIDEStateService implements IDEStateService {
    private static DefaultIDEStateService instance;
    private final Map<Document, Long> openDocuments = new HashMap<>();
    private final Map<Path, Long> recentFiles = new HashMap<>();
    private final ObjectProperty<IDEViewMode> currentViewMode = new SimpleObjectProperty<>(IDEViewMode.CODE);

    private Project currentProject;
    private long openedProjectAtMillis = -1L;
    private Document activeDocument;

    private DefaultIDEStateService() {
        Railroad.EVENT_BUS.subscribe(ProjectEvent.class, event -> {
            if (event.isOpened()) {
                setCurrentProject_internal(event.project());
            } else if (event.isClosed()) {
                setCurrentProject_internal(null);
            }
        });
    }

    public static synchronized DefaultIDEStateService getInstance() {
        if (instance == null) {
            instance = new DefaultIDEStateService();
        }

        return instance;
    }

    private void setCurrentProject_internal(Project project) {
        this.currentProject = project;
        this.openedProjectAtMillis = project != null ? System.currentTimeMillis() : -1L;
        this.currentViewMode.set(IDEViewMode.CODE);

        if (project == null) {
            clearOpenDocuments_internal();
        }
    }

    private void openDocument_internal(Document document) {
        openDocuments.put(document, System.currentTimeMillis());
        Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.OPENED));
    }

    private void closeDocument_internal(Document document) {
        openDocuments.remove(document);
        Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.CLOSED));
        recentFiles.put(document.getPath(), System.currentTimeMillis());
        // TODO: Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.RECENT_FILE_ADDED));

        if ((activeDocument != null && openDocuments.isEmpty()) || (activeDocument != null && activeDocument.equals(document))) {
            setActiveDocument_internal(null);
        }
    }

    private void setActiveDocument_internal(Document document) {
        Document previousDocument = this.activeDocument;
        Document nextDocument = document != null && openDocuments.containsKey(document) ? document : null;
        if (Objects.equals(previousDocument, nextDocument))
            return;

        this.activeDocument = nextDocument;
        if (previousDocument != null) {
            Railroad.EVENT_BUS.publish(new DocumentEvent(previousDocument, DocumentEvent.EventType.DEACTIVATED));
        }
        if (nextDocument != null) {
            Railroad.EVENT_BUS.publish(new DocumentEvent(nextDocument, DocumentEvent.EventType.ACTIVATED));
        }
    }

    private void clearOpenDocuments_internal() {
        List.copyOf(openDocuments.keySet()).forEach(this::closeDocument_internal);
        setActiveDocument_internal(null);
    }

    private void setOpenDocuments_internal(List<Document> list) {
        clearOpenDocuments_internal();
        for (Document document : list) {
            openDocument_internal(document);
        }
    }

    @Override
    public void clearOpenDocuments() {
        clearOpenDocuments_internal();
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
    public void setOpenDocuments(List<Document> list) {
        setOpenDocuments_internal(list);
    }

    @Override
    public Document getActiveDocument() {
        return activeDocument;
    }

    @Override
    public IDEViewMode getCurrentViewMode() {
        return currentViewMode.get();
    }

    public ObjectProperty<IDEViewMode> currentViewModeProperty() {
        return currentViewMode;
    }

    @Override
    public void setActiveDocument(Document document) {
        setActiveDocument_internal(document);
    }

    @Override
    public void setCurrentProject(Project project) {
        setCurrentProject_internal(project);
    }

    @Override
    public void openDocument(Document document) {
        openDocument_internal(document);
    }

    @Override
    public void closeDocument(Document document) {
        closeDocument_internal(document);
    }

    @Override
    public void setCurrentViewMode(IDEViewMode viewMode) {
        currentViewMode.set(viewMode == null ? IDEViewMode.CODE : viewMode);
    }
}
