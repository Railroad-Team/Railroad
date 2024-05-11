package io.github.railroad.vcs;

public class Repository {
    private RepositoryTypes repositoryType;
    private String repositoryURL;
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
}
