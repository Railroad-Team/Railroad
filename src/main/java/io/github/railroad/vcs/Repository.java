package io.github.railroad.vcs;

import io.github.railroad.vcs.connections.AbstractConnection;
import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.Optional;

public class Repository {
    private RepositoryTypes repositoryType;
    private String repositoryURL;
    private String repositoryCloneURL;
    private Optional<Image> icon;
    private String repositoryName;
    private AbstractConnection connection;

    public Repository(RepositoryTypes repositoryType) {
        setRepositoryType(repositoryType);
    }

    public String getRepositoryURL() {
        return repositoryURL;
    }

    public void setRepositoryURL(String repositoryURL) {
        this.repositoryURL = repositoryURL;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getRepositoryCloneURL() {
        return repositoryCloneURL;
    }

    public void setRepositoryCloneURL(String repositoryCloneURL) {
        this.repositoryCloneURL = repositoryCloneURL;
    }

    public Optional<Image> getIcon() {
        return icon;
    }

    public void setIcon(Optional<Image> icon) {
        this.icon = icon;
    }

    public RepositoryTypes getRepositoryType() {
        return repositoryType;
    }

    public void setRepositoryType(RepositoryTypes repositoryType) {
        this.repositoryType = repositoryType;
    }

    public AbstractConnection getConnection() {
        return connection;
    }

    public void setConnection(AbstractConnection connection) {
        this.connection = connection;
    }

    public boolean cloneRepo(Path path) {
        return this.connection.cloneRepo(this, path);
    }
}
