package io.github.railroad.discord.activity;

public class DiscordActivityButton {
    private String label;
    private String url;

    public DiscordActivityButton() {
    }

    public DiscordActivityButton(String label, String url) {
        this.label = label;
        this.url = url;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "DiscordActivityButton{" +
                "label='" + label + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
