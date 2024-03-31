package io.github.railroad.github;

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
