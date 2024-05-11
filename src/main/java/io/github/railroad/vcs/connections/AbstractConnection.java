package io.github.railroad.vcs.connections;

import io.github.railroad.vcs.Repository;

import java.util.List;

abstract class AbstractConnection {
    public abstract List<Repository> getRepositories();
    public abstract boolean updateRepo(Repository repo);
}
