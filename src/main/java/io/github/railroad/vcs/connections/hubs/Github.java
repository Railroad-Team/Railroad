package io.github.railroad.vcs.connections.hubs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.railroad.Railroad;
import io.github.railroad.vcs.Repository;
import io.github.railroad.vcs.RepositoryTypes;
import io.github.railroad.vcs.connections.AbstractConnection;
import io.github.railroad.vcs.connections.Profile;
import javafx.scene.image.Image;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Github extends AbstractConnection {
    private Profile account;

    public Github(Profile profile) {
        this.account = profile;
    }

    private List<String> ReadHTTP(String method, String postixurl, String body) {
        List<String> result = new ArrayList<>();
        String request_url = "https://api.github.com/" + postixurl;
        boolean finished = false;
        try {
            while (!finished) {
                URL url = new URL(request_url);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod(method.toUpperCase());
                con.setRequestProperty("Accept", "application/vnd.github+json");
                con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer content = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                if (con.getHeaderField("link") != null) {
                    finished = true;
                    for (String el : con.getHeaderField("link").split(",")) {
                        if (el.contains("rel=\"next\"")) {
                            request_url = el.substring(el.indexOf("<")+1, el.indexOf(">"));
                            System.out.println("Not finished next url: " + request_url);
                            finished = false;
                        }
                    }
                } else {
                    finished = true;
                }
                in.close();
                result.add(content.toString());
            }
            return result;

        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Repository> getWriteAccessRepos() {
        List<Repository> repositoryList = new ArrayList<>();
        try {
            System.out.println("Downloading Github Repos");
            List<String> output = ReadHTTP("GET", "user/repos?per_page=20", "");
            if (!output.isEmpty()) {
                for (String http_response : output) {
                    if (!http_response.isBlank()) {
                        JsonArray repos = Railroad.GSON.fromJson(http_response, JsonArray.class);
                        for (JsonElement element : repos) {
                            if (element.isJsonObject()) {
                                Repository repository = new Repository(RepositoryTypes.git);
                                repository.setRepositoryName(element.getAsJsonObject().get("name").getAsString());
                                repository.setRepositoryURL(element.getAsJsonObject().get("url").getAsString());
                                repository.setRepositoryCloneURL(element.getAsJsonObject().get("clone_url").getAsString());
                                repository.setIcon(Optional.of(new Image(element.getAsJsonObject().get("owner").getAsJsonObject().get("avatar_url").getAsString())));
                                repositoryList.add(repository);
                            }
                        }
                    }
                }
            }
        } catch (Exception exception) {
            Railroad.LOGGER.error(exception.getMessage());
        }
        return repositoryList;
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
