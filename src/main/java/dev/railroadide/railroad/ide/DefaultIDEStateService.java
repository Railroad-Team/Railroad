package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.plugin.spi.services.IDEStateService;
import javafx.application.Platform;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Tracks the current project and open documents and publishes their lifecycle events.
 * Owns one document store for the current project session, releasing it on project close.
 */
@Getter
public class DefaultIDEStateService implements IDEStateService {
    private static DefaultIDEStateService instance;
    private final Map<Document, Long> openDocuments = new LinkedHashMap<>();
    private final Map<Path, Long> recentFiles = new HashMap<>();
    private DocumentIdentityRegistry documentIdentities = new DocumentIdentityRegistry();
    private volatile DocumentStore documentStore;
    private volatile Project currentProject;
    private ProjectWorkspaceConfiguration workspaceConfiguration;
    @Getter(lombok.AccessLevel.NONE)
    private ProjectDocumentSynchronizer documentSynchronizer;
    private long openedProjectAtMillis = -1L;
    private Document activeDocument;

    /**
     * Reports project watcher coverage for editor presentation ownership. Call on the editor UI thread.
     *
     * @param path physical document location
     * @return whether the current project synchronizer covers the path
     */
    public boolean isDocumentWatched(Path path) {
        return documentSynchronizer != null && documentSynchronizer.covers(path);
    }

    private DefaultIDEStateService() {
        Railroad.EVENT_BUS.subscribe(ProjectEvent.class, event -> {
            if (event.isOpened()) {
                setCurrentProject_internal(event.project());
            } else if (event.isClosed()) {
                setCurrentProject_internal(null);
            }
        });
    }

    /**
     * Returns the lazily initialized application IDE state service.
     *
     * @return shared IDE state service
     */
    public static synchronized DefaultIDEStateService getInstance() {
        if (instance == null) {
            instance = new DefaultIDEStateService();
        }

        return instance;
    }

    private void setCurrentProject_internal(Project project) {
        if (project != currentProject) {
            var previousSession = Services.PROJECT_LANGUAGE_INDEX_SERVICE.session(currentProject);
            Services.PROJECT_LANGUAGE_INDEX_SERVICE.closeProject(previousSession);
            JavaLanguageSupport.analysisContextProvider()
                .closeSession(previousSession);
            if (project != null) {
                Services.PROJECT_LANGUAGE_INDEX_SERVICE.openProject(project);
            }
            if (documentSynchronizer != null) {
                documentSynchronizer.close();
                documentSynchronizer = null;
            }
            WorkspaceConfigurationSnapshot initialConfiguration = project == null
                ? null
                : ProjectWorkspaceConfiguration.initial(project);
            if (initialConfiguration != null) {
                var values = new HashMap<>(initialConfiguration.settings());
                values.put("index.session", Services.PROJECT_LANGUAGE_INDEX_SERVICE.session(project).id());
                values.put("index.binary.epoch", "pending");
                initialConfiguration = new WorkspaceConfigurationSnapshot(0, initialConfiguration.moduleIds(), values);
            }
            if (workspaceConfiguration != null) {
                workspaceConfiguration.close();
                workspaceConfiguration = null;
            }
            documentStore = project == null
                ? null
                : new DefaultDocumentStore(project.getId(), initialConfiguration);
            if (project != null) {
                workspaceConfiguration = new ProjectWorkspaceConfiguration(project, documentStore);
                try {
                    documentSynchronizer = new ProjectDocumentSynchronizer(project.getPath(), documentStore,
                        documentIdentities,
                        id -> Services.EDITOR_TAB_MANAGER.hasOpenTextEditor(id),
                        Platform::runLater);
                } catch (IOException exception) {
                    Railroad.LOGGER.error("Unable to monitor workspace documents", exception);
                }
            }
        }
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
    public Optional<DocumentStore> getDocumentStore() {
        return Optional.ofNullable(documentStore);
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

    /**
     * Returns a snapshot of documents currently open in the IDE.
     *
     * @return open documents in opening order
     */
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
