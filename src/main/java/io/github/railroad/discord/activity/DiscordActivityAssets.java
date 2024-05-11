package io.github.railroad.discord.activity;

public class DiscordActivityAssets {
    private String large_image, large_text, small_image, small_text;

    public void setLargeImage(String assetKey) {
        this.large_image = assetKey;
    }

    public void setLargeText(String text) {
        this.large_text = text;
    }

    public void setSmallImage(String assetKey) {
        this.small_image = assetKey;
    }

    public void setSmallText(String text) {
        this.small_text = text;
    }

    @Override
    public String toString() {
        return "DiscordActivityAssets{" +
                "large_image='" + large_image + '\'' +
                ", large_text='" + large_text + '\'' +
                ", small_image='" + small_image + '\'' +
                ", small_text='" + small_text + '\'' +
                '}';
    }
}
