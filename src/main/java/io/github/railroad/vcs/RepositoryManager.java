package io.github.railroad.vcs;

import io.github.railroad.Railroad;
import io.github.railroad.vcs.connections.AbstractConnection;

import java.util.ArrayList;
import java.util.List;

public class RepositoryManager {
    private List<Repository> repositoryList;
    private List<AbstractConnection> connections;

    public RepositoryManager() {
        this.connections = new ArrayList<AbstractConnection>();
        this.repositoryList = new ArrayList<Repository>();
    }
    public List<Repository> getRepositoryList() {
        return repositoryList;
    }
    public void updateRepositories() {
        try {
            for (AbstractConnection abstractConnection : connections) {
                this.repositoryList.addAll(abstractConnection.getRepositories());
            }
            System.out.println(repositoryList);
        } catch (Exception exception) {
            Railroad.LOGGER.error(exception.getMessage());
        }
    }

    public boolean addConnection(AbstractConnection connection) {
        this.connections.add(connection);
        return true;
    }

}
