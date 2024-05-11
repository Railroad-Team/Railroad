package io.github.railroad.discord.activity;

public class DiscordSetActivity {
    private DiscordSetActivity() {}

    public static class Args {
        private long pid;
        private DiscordActivity activity;

        public Args(long pid, DiscordActivity activity) {
            this.pid = pid;
            this.activity = activity;
        }
    }
}
