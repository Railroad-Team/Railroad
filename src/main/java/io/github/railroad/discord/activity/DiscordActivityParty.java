package io.github.railroad.discord.activity;

public class DiscordActivityParty {
    private String id;
    private int[] size;

    public DiscordActivityParty() {
        this.size = null;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCurrentSize() {
        return size[0];
    }

    public void setCurrentSize(int size) {
        this.size[0] = size;
    }

    public int getMaxSize() {
        return size[1];
    }

    public void setMaxSize(int size) {
        this.size[1] = size;
    }

    public void setSize(int current, int max) {
        this.size = new int[]{current, max};
    }

    @Override
    public String toString() {
        return "DiscordActivityParty{" +
                "id='" + id + '\'' +
                ", size={min=" + size[0] + ", max=" + size[1] + '}' +
                '}';
    }
}
