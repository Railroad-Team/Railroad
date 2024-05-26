package io.github.railroad.vcs.connections;

import io.github.railroad.vcs.Repository;

import java.util.List;

public abstract class AbstractConnection {
    public abstract List<Repository> getRepositories();

    public abstract boolean updateRepo(Repository repo);

    public abstract void cloneRepo(Repository repository);
}
