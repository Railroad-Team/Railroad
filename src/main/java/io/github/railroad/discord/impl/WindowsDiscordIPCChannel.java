package io.github.railroad.discord.impl;

import io.github.railroad.discord.DiscordIPCChannel;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class WindowsDiscordIPCChannel implements DiscordIPCChannel {
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private boolean blocking = true;

    public WindowsDiscordIPCChannel() throws IOException {
        String path = System.getenv("DISCORD_IPC_PATH");
        if (path == null) {
            String instance = System.getenv("DISCORD_INSTANCE_ID");
            int pid = 0;
            if (instance != null) {
                try {
                    pid = Integer.parseInt(instance);
                } catch (NumberFormatException exception) {
                    throw new IOException("Failed to parse DISCORD_INSTANCE_ID", exception);
                }
            }

            path = "\\\\?\\pipe\\discord-ipc-" + pid;
        }

        this.raf = new RandomAccessFile(path, "rw");
        this.channel = this.raf.getChannel();
    }

    @Override
    public void close() throws IOException {
        this.raf.close();
    }

    @Override
    public void configureBlocking(boolean block) {
        this.blocking = block;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int result = 0;
        if (this.blocking || (this.channel.size() - this.channel.position()) >= dst.remaining()) {
            result = this.channel.read(dst);
        }

        return result;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long result = 0;
        long remaining = 0;
        for (int i = offset; !this.blocking && i < offset + length; i++) {
            remaining += dsts[i].remaining();
        }

        if (this.blocking || (this.channel.size() - this.channel.position()) >= remaining) {
            result = this.channel.read(dsts, offset, length);
        }

        return result;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int result = this.channel.write(src);
        this.channel.force(false);
        return result;
    }
}
