package io.github.railroad.discord;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface DiscordIPCChannel {
    void close() throws IOException;
    void configureBlocking(boolean block) throws IOException;
    int read(ByteBuffer dst) throws IOException;
    long read(ByteBuffer[] dsts, int offset, int length) throws IOException;
    int write(ByteBuffer src) throws IOException;
}
