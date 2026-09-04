package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentityRegistry;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.plugin.spi.services.IDEStateService;
import lombok.Getter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Getter
public class DefaultIDEStateService implements IDEStateService {
    private static DefaultIDEStateService instance;
    private final Map<Document, Long> openDocuments = new LinkedHashMap<>();
    private final Map<Path, Long> recentFiles = new HashMap<>();
    private DocumentIdentityRegistry documentIdentities = new DocumentIdentityRegistry();
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
        if (project == null) {
            clearOpenDocuments_internal();
            documentIdentities = new DocumentIdentityRegistry();
        }
    }

    private void openDocument_internal(Document document) {
        Objects.requireNonNull(document, "Document cannot be null");
        documentIdentities.identify(document.getUri());
        if (findOpenDocument(document).isPresent())
            return;

        openDocuments.put(document, System.currentTimeMillis());
        Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.OPENED));
    }

    private void closeDocument_internal(Document document) {
        if (document == null)
            return;

        Document openDocument = findOpenDocument(document).orElse(null);
        if (openDocument == null)
            return;

        if (documentsMatch(activeDocument, openDocument)) {
            setActiveDocument_internal(null);
        }

        openDocuments.remove(openDocument);
        Railroad.EVENT_BUS.publish(new DocumentEvent(openDocument, DocumentEvent.EventType.CLOSED));
        openDocument.getUri().filePath()
            .ifPresent(path -> recentFiles.put(path, System.currentTimeMillis()));
        // TODO: Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.RECENT_FILE_ADDED));
    }

    private void setActiveDocument_internal(Document document) {
        Document previousDocument = this.activeDocument;
        Document nextDocument = document == null ? null : findOpenDocument(document).orElse(null);
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
        Objects.requireNonNull(list, "Documents cannot be null");
        clearOpenDocuments_internal();
        for (Document document : list) {
            openDocument_internal(document);
        }
    }

    private Optional<Document> findOpenDocument(Document document) {
        return openDocuments.keySet().stream()
            .filter(openDocument -> documentsMatch(openDocument, document))
            .findFirst();
    }

    private boolean documentsMatch(Document first, Document second) {
        if (first == null || second == null)
            return false;

        return documentIdentities.findIdentity(first.getUri())
            .flatMap(firstIdentity -> documentIdentities.findIdentity(second.getUri())
                .map(secondIdentity -> firstIdentity.id().equals(secondIdentity.id())))
            .orElse(false);
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
        return findOpenDocument(document)
            .map(openDocuments::get)
            .orElse(-1L);
    }

    @Override
    public DocumentIdentity identifyDocument(Document document) {
        Objects.requireNonNull(document, "Document cannot be null");
        return documentIdentities.identify(document.getUri());
    }

    @Override
    public Optional<DocumentIdentity> findDocumentIdentity(DocumentUri uri) {
        return documentIdentities.findIdentity(Objects.requireNonNull(uri, "Document URI cannot be null"));
    }

    @Override
    public DocumentIdentity rebindDocument(DocumentIdentity identity, DocumentUri newUri) {
        Objects.requireNonNull(identity, "Document identity cannot be null");
        Objects.requireNonNull(newUri, "New document URI cannot be null");
        documentIdentities.rebind(identity.id(), identity.uri(), newUri);
        return documentIdentities.findIdentity(newUri).orElseThrow();
    }

    @Override
    public DocumentIdentity restoreDocumentIdentity(DocumentIdentity identity) {
        Objects.requireNonNull(identity, "Document identity cannot be null");
        Optional<DocumentIdentity> existing = documentIdentities.findIdentity(identity.uri());
        if (existing.isPresent())
            return existing.get();

        documentIdentities.restoreVersion(identity.id(), DocumentVersion.initial());
        documentIdentities.associate(identity.id(), identity.uri());
        return documentIdentities.findIdentity(identity.uri()).orElseThrow();
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

}
