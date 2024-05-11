package io.github.railroad.discord.activity;

import java.time.Instant;

public class DiscordActivityTimestamps {
    private Long start, end;

    public Instant getStart() {
        return start == null ? null : Instant.ofEpochMilli(start);
    }

    public void setStart(Instant start) {
        this.start = start.toEpochMilli();
        this.end = null;
    }

    public Instant getEnd() {
        return end == null ? null : Instant.ofEpochMilli(end);
    }

    public void setEnd(Instant end) {
        this.start = null;
        this.end = end.toEpochMilli();
    }
}
