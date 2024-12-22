package io.github.railroad.discord.event;

import io.github.railroad.discord.DiscordCore;
import io.github.railroad.discord.data.DiscordUser;
import lombok.Getter;

public class DiscordReadyEvent {
    public static class Data {
        private int v;
        private Config config;
        private DiscordUser user;

        @Override
        public String toString() {
            return "ReadyData{" +
                    "v=" + v +
                    ", config=" + config +
                    ", user=" + user +
                    '}';
        }

        static class Config {
            private final String cdn_host;
            private final String api_endpoint;
            @Getter
            private final String environment;

            public Config(String cdn_host, String api_endpoint, String environment) {
                this.cdn_host = cdn_host;
                this.api_endpoint = api_endpoint;
                this.environment = environment;
            }

            public String getCdnHost() {
                return cdn_host;
            }

            public String getApiEndpoint() {
                return api_endpoint;
            }

            @Override
            public String toString() {
                return "ReadyConfig{" +
                        "cdn_host='" + cdn_host + '\'' +
                        ", api_endpoint='" + api_endpoint + '\'' +
                        ", environment='" + environment + '\'' +
                        '}';
            }
        }
    }

    public static class Handler extends DiscordEventHandler<Data> {
        public Handler(DiscordCore core) {
            super(core);
        }

        @Override
        public void handle(DiscordCommand command, Data data) {
            this.core.onReady();
            this.core.setCurrentUser(data.user);
        }

        @Override
        public Class<Data> getDataClass() {
            return Data.class;
        }

        @Override
        public boolean shouldRegister() {
            return false;
        }
    }
}
