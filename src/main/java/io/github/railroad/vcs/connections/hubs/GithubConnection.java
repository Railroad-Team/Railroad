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

public class GithubConnection extends AbstractConnection {
    private Profile account;

    public GithubConnection(Profile profile) {
        this.account = profile;
    }

    private List<HttpResponse> ReadHTTP(String method, String postixurl, String body) throws Exception {
        return ReadHTTP(method, postixurl, body, true);
    }

    private List<HttpResponse> ReadHTTP(String method, String postixurl, String body, boolean enable_pages) throws Exception {
        if (account.getAccessToken().isEmpty()) {
            throw new Exception("Missing Access Token");
        }
        List<HttpResponse> result = new ArrayList<>();

        String request_url = "https://api.github.com/" + postixurl;
        boolean finished = false;
        try {
            try {

                while (!finished) {
                    URL url = new URL(request_url);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod(method.toUpperCase());
                    con.setRequestProperty("Accept", "application/vnd.github+json");
                    con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                    con.setRequestProperty("Authorization", "Bearer " + account.getAccessToken());
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(con.getInputStream()));
                    String inputLine;
                    StringBuffer content = new StringBuffer();
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

    private List<Repository> getWriteAccessRepos() {
        List<Repository> repositoryList = new ArrayList<>();
        try {
            Railroad.LOGGER.debug("VCS - Github - Downloading repos");
            List<HttpResponse> output = ReadHTTP("GET", "user/repos?per_page=20", "");
            if (!output.isEmpty()) {
                for (HttpResponse http_response : output) {
                    if (http_response.getStatusCode() == 200) {
                        if (!http_response.getContent().isBlank()) {
                            JsonArray repos = Railroad.GSON.fromJson(http_response.content, JsonArray.class);
                            for (JsonElement element : repos) {
                                if (element.isJsonObject()) {
                                    Repository repository = new Repository(RepositoryTypes.git);
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
        this.getRepositories().setAll(getWriteAccessRepos());
    }

    @Override
    public boolean updateRepo(Repository repo) {
        return false;
    }

    @Override
    public void cloneRepo(Repository repository) {
        if (repository.getRepositoryType() == RepositoryTypes.git) {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("git", "clone", repository.getRepositoryCloneURL(), "/home/romeo/Gits/" + repository.getRepositoryName());

            try {
                Process process = processBuilder.start();
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                         BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            //updateOutput(line);
                        }
                        while ((line = errorReader.readLine()) != null) {
                            //updateOutput(line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    //updateOutput("Repository cloned successfully.");
                } else {
                    //updateOutput("Failed to clone the repository.");
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean validateProfile() {
        Railroad.LOGGER.debug("VCS - Github - Validating profile");
        List<HttpResponse> output = null;
        try {
            output = ReadHTTP("GET", "user/repos?per_page=1", "", false);
        } catch (Exception e) {
            Railroad.LOGGER.error("Github Validation - " + e.getMessage());
            return false;
        }
        if (output.isEmpty()) {
            return false;
        } else {
            if (output.get(0).getStatusCode() == 200) {
                return true;
            } else {
                return false;
            }
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