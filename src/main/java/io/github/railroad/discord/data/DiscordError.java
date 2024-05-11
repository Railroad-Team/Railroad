package io.github.railroad.discord.data;

public class DiscordError {
    private int code;
    private String message;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "Error " + getCode() + ": " + getMessage();
    }
}
