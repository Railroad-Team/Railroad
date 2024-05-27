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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GithubConnection extends AbstractConnection {
    private Profile account;

    public GithubConnection(Profile profile) {
        this.account = profile;
    }

    private List<HttpResponse> readHTTP(String method, String postixurl, String body) throws Exception {
        return readHTTP(method, postixurl, body, true);
    }

    private List<HttpResponse> readHTTP(String method, String postixurl, String body, boolean enable_pages) throws Exception {
        if (account.getAccessToken().isEmpty()) {
            throw new RuntimeException("Missing Access Token");
        }
        
        List<HttpResponse> result = new ArrayList<>();

        String request_url = "https://api.github.com/" + postixurl;
        boolean finished = false;
        try {
            try {

                while (!finished) {
                    URL url = new URL(request_url);
                    var con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod(method.toUpperCase());
                    con.setRequestProperty("Accept", "application/vnd.github+json");
                    con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                    con.setRequestProperty("Authorization", "Bearer " + account.getAccessToken());
                    var in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    String inputLine;
                    var content = new StringBuffer();
                    var statusCode = con.getResponseCode();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    
                    if (enable_pages) {
                        if (con.getHeaderField("link") != null) {
                            finished = true;
                            for (String el : con.getHeaderField("link").split(",")) {
                                if (el.contains("rel=\"next\"")) {
                                    request_url = el.substring(el.indexOf("<") + 1, el.indexOf(">"));
                                    finished = false;
                                }
                            }
                        } else {
                            finished = true;
                        }
                    } else {
                        finished = true;
                    }
                    
                    in.close();
                    result.add(new HttpResponse(content.toString(), statusCode));
                }

            } catch (ProtocolException e) {
                throw new RuntimeException(e);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } finally {
            return result;
        }
    }

    private List<Repository> getUserRepos() {
        List<Repository> repositoryList = new ArrayList<>();
        try {
            Railroad.LOGGER.debug("VCS - Github - Downloading repos");
            List<HttpResponse> output = readHTTP("GET", "user/repos?per_page=20", "");
            if (!output.isEmpty()) {
                for (HttpResponse http_response : output) {
                    if (http_response.getStatusCode() == 200) {
                        if (!http_response.getContent().isBlank()) {
                            JsonArray repos = Railroad.GSON.fromJson(http_response.content, JsonArray.class);
                            for (JsonElement element : repos) {
                                if (element.isJsonObject()) {
                                    var repository = new Repository(RepositoryTypes.GIT);
                                    repository.setRepositoryName(element.getAsJsonObject().get("name").getAsString());
                                    repository.setRepositoryURL(element.getAsJsonObject().get("url").getAsString());
                                    repository.setRepositoryCloneURL(element.getAsJsonObject().get("clone_url").getAsString());
                                    repository.setIcon(Optional.of(new Image(element.getAsJsonObject().get("owner").getAsJsonObject().get("avatar_url").getAsString())));
                                    repository.setConnection(this);
                                    repositoryList.add(repository);
                                }
                            }
                        }
                    }

                }
            }
        } catch (Exception exception) {
            Railroad.LOGGER.error("Github Download repos " + exception.getMessage());
        }
        
        return repositoryList;
    }

    @Override
    public void downloadRepositories() {
        getRepositories().setAll(getUserRepos());
    }

    @Override
    public boolean updateRepo(Repository repo) {
        return false;
    }

    @Override
    public boolean cloneRepo(Repository repository, Path path) {
        if (repository.getRepositoryType() == RepositoryTypes.GIT) {
            Railroad.LOGGER.info("Cloning Repo:" + repository.getRepositoryCloneURL() + " to:" + path.toAbsolutePath());
            var processBuilder = new ProcessBuilder();
            processBuilder.command("git", "clone", repository.getRepositoryCloneURL(), path.toAbsolutePath().resolve(repository.getRepositoryName()).toString());

            try {
                Process process = processBuilder.start();
                new Thread(() -> {
                    try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                         var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            //updateOutput(line);
                        }
                        while ((line = errorReader.readLine()) != null) {
                            //updateOutput(line);
                        }
                    } catch (IOException exception) {
                        Railroad.LOGGER.error("Something went wrong trying to clone a github repo", exception);
                    }
                }).start();

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    Railroad.LOGGER.info("Repository cloned successfully.");
                    return true;
                } else {
                    Railroad.LOGGER.error("Failed to clone the repository.");
                    return false;
                }
            } catch (IOException | InterruptedException exception) {
                Railroad.LOGGER.error("Something went wrong!", exception);
                return false;
            }
        }
        
        return false;
    }

    @Override
    public boolean validateProfile() {
        Railroad.LOGGER.debug("VCS - Github - Validating profile");
        List<HttpResponse> output = null;
        try {
            output = readHTTP("GET", "user/repos?per_page=1", "", false);
        } catch (Exception e) {
            Railroad.LOGGER.error("Github Validation - " + e.getMessage());
            return false;
        }
        
        if (output.isEmpty()) {
            return false;
        } else {
            return output.get(0).getStatusCode() == 200;
        }
    }

    @Override
    public Profile getProfile() {
        return this.account;
    }

    private class HttpResponse {
        private String content;
        private int statusCode;

        public HttpResponse(String content, int statusCode) {
            this.content = content;
            this.statusCode = statusCode;
        }

        public String getContent() {
            return content;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
