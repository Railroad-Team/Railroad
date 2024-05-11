package io.github.railroad.discord.activity;

public class DiscordActivitySecrets {
    private String match;
    private String join;
    private String spectate;

    public String getMatchSecret() {
        return match;
    }

    public void setMatchSecret(String match) {
        this.match = match;
    }

    public String getJoinSecret() {
        return join;
    }

    public void setJoinSecret(String join) {
        this.join = join;
    }

    public String getSpectateSecret() {
        return spectate;
    }

    public void setSpectateSecret(String spectate) {
        this.spectate = spectate;
    }

    @Override
    public String toString() {
        return "DiscordActivitySecrets{" +
                "match='" + match + '\'' +
                ", join='" + join + '\'' +
                ", spectate='" + spectate + '\'' +
                '}';
    }
}
