package io.github.railroad.vcs;

import io.github.railroad.vcs.connections.AbstractConnection;

import java.util.ArrayList;
import java.util.List;

public class RepositoryManager {
    private List<Repository> repositoryList;
    private List<AbstractConnection> connections;

    public RepositoryManager() {
        this.connections = new ArrayList<AbstractConnection>();
    }
    public List<Repository> getRepositoryList() {
        return repositoryList;
    }
    public void updateRepositories() {
        for (AbstractConnection abstractConnection : connections) {
            abstractConnection.getRepositories();
        }
    }

    public boolean addConnection(AbstractConnection connection) {
        this.connections.add(connection);
        return true;
    }

}
