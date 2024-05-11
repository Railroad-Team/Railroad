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

public record GithubAccount(String username, String email, String profileImageUrl, String accessToken) {
    public static GithubAccount loadExisting() {
        // TODO: Load existing account from disk
        return null;
    }

    public static GithubAccount connect() {
        // TODO: Connect to GitHub
        return new GithubAccount("John Doe", "john.doe@gmail.com", "https://place-hold.it/100", "access-token");
    }

    public void disconnect() {
        // TODO: Disconnect from GitHub
    }
}

