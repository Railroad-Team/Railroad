package io.github.railroad.discord.impl;

import io.github.railroad.discord.DiscordIPCChannel;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class UnixDiscordIPCChannel implements DiscordIPCChannel {
    private final SocketChannel channel;

    public UnixDiscordIPCChannel() throws IOException {
        String path = System.getenv("DISCORD_IPC_PATH");
        if (path == null) {
            String instance = System.getProperty("DISCORD_INSTANCE_ID");
            int pid = 0;
            if (instance != null) {
                try {
                    pid = Integer.parseInt(instance);
                } catch (NumberFormatException exception) {
                    throw new IOException("Failed to parse DISCORD_INSTANCE_ID", exception);
                }
            }

            String[] sockets = getSockets();
            if (pid < 0 || pid >= sockets.length) {
                throw new IOException("Invalid pid: " + pid);
            }

            path = sockets[pid];
        }

        this.channel = SocketChannel.open(UnixDomainSocketAddress.of(path));
    }

    private static String[] getSockets() {
        List<String> candidates = new ArrayList<>();
        candidates.add(System.getenv("XDG_RUNTIME_DIR"));
        candidates.add(System.getenv("TMPDIR"));
        candidates.add(System.getenv("TMP"));
        candidates.add(System.getenv("TEMP"));

        candidates.removeIf(Objects::isNull);
        candidates.removeIf(path -> Files.notExists(Paths.get(path)));

        List<String> flatpackCandidates = candidates.stream().map(path -> path + "/app/com.discordapp.Discord").toList();
        List<String> snapCandidates = candidates.stream().map(path -> path + "/snap.discord").toList();
        List<String> additionalSnapCandidates = candidates.stream()
                .flatMap(pathStr -> {
                    try {
                        List<String> list = new ArrayList<>();
                        Path path = Paths.get(pathStr);
                        try (Stream<Path> stream = Files.list(path)) {
                            return stream.filter(p -> p.getFileName().toString().startsWith("snap.discord_"))
                                    .map(Path::toAbsolutePath)
                                    .map(Path::toString);
                        }
                    } catch (IOException exception) {
                        return Arrays.stream(new String[0]);
                    }
                }).toList();

        candidates.addAll(flatpackCandidates);
        candidates.addAll(snapCandidates);
        candidates.addAll(additionalSnapCandidates);

        candidates.removeIf(Objects::isNull);
        candidates.removeIf(path -> Files.notExists(Paths.get(path)));

        return candidates.stream()
                .flatMap(pathStr -> IntStream.iterate(0, i -> i + 1)
                        .mapToObj(i -> pathStr + "/discord-ipc-" + i)
                        .takeWhile(path -> Files.exists(Paths.get(path)))
                ).toArray(String[]::new);
    }

    @Override
    public void close() throws IOException {
        this.channel.close();
    }

    @Override
    public void configureBlocking(boolean block) throws IOException {
        this.channel.configureBlocking(block);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return this.channel.read(dst);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        return this.channel.read(dsts, offset, length);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return this.channel.write(src);
    }
}
