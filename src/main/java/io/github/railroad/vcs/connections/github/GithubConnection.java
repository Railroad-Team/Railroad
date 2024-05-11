package io.github.railroad.vcs.connections.github;

import io.github.railroad.vcs.Repository;
import io.github.railroad.vcs.connections.AbstractConnection;
import io.github.railroad.vcs.connections.Profile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.List;

public class GithubConnection extends AbstractConnection {
    private Profile account;
    public GithubConnection(Profile profile) {
        this.account = profile;
    };
    private String ReadHTTP(String method, String postixurl, String body) {
        try {
            URL url = new URL("https://api.github.com/"+ postixurl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method.toUpperCase());
            con.setRequestProperty("Accept", "application/vnd.github+json");
            con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            return con.getResponseMessage();
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Repository> getWriteAccessRepos() {
        System.out.println("Downloading Github Repos");
        System.out.println(ReadHTTP("GET","user/repos",""));
        return List.of();
    }
    @Override
    public List<Repository> getRepositories() {
        return this.getWriteAccessRepos();
    }

    @Override
    public boolean updateRepo(Repository repo) {
        return false;
    }
}
