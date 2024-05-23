package io.github.railroad.vcs;

import javafx.scene.image.Image;

import java.util.Optional;

public class Repository {
    private RepositoryTypes repositoryType;
    private String repositoryURL;
    private String repositoryCloneURL;
    private Optional<Image> icon;
    private String repositoryName;
    public Repository(RepositoryTypes repositoryType) {
        this.repositoryType = repositoryType;
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
}
