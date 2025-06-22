package io.github.railroad.discord;

import io.github.railroad.Railroad;
import io.github.railroad.discord.activity.DiscordActivityManager;
import io.github.railroad.discord.data.*;
import io.github.railroad.discord.event.DiscordCommand;
import io.github.railroad.discord.event.DiscordEventHandler;
import io.github.railroad.discord.event.DiscordEvents;
import io.github.railroad.discord.impl.UnixDiscordIPCChannel;
import io.github.railroad.discord.impl.WindowsDiscordIPCChannel;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public final class DiscordCore implements AutoCloseable {
    public static final Consumer<DiscordResult> DEFAULT_CALLBACK = result -> {
        if (result != DiscordResult.OK)
            throw new DiscordException(result);
    };

    private final Queue<Pair<DiscordCommand, Consumer<DiscordCommand>>> commandQueue = new ArrayDeque<>();
    private final DiscordIPCChannel ipcChannel;
    private final String clientId;
    @Getter
    private final DiscordActivityManager activityManager;
    private final Map<String, Consumer<DiscordCommand>> handlers = new HashMap<>();
    private final DiscordEvents events;

    private long nonce;
    private DiscordConnectionState connectionState;
    @Setter
    @Getter
    private DiscordUser currentUser;
    @Setter
    @Getter
    private long pid = ProcessHandle.current().pid();
    private boolean isShuttingDown = false;

    public DiscordCore(String clientId) throws DiscordException {
        this.clientId = clientId;

        this.connectionState = DiscordConnectionState.HANDSHAKE;
        this.nonce = 0L;
        this.events = new DiscordEvents(this);

        try {
            this.ipcChannel = findIPCChannel();
            sendHandshake();
            runCallbacks();
            this.ipcChannel.configureBlocking(false);
        } catch (IOException exception) {
            //Railroad.showErrorAlert("Failed to connect to Discord", "Failed to connect to Discord IPC channel", null);
            throw new DiscordException(DiscordResult.SERVICE_UNAVAILABLE);
        }

        this.activityManager = new DiscordActivityManager(this);
    }

    public static DiscordIPCChannel findIPCChannel() throws IOException {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows") ?
                new WindowsDiscordIPCChannel() : new UnixDiscordIPCChannel();
    }

    private void runCallbacks() {
        var thread = new Thread(() -> {
            while (true) {
                if (this.isShuttingDown)
                    break;

                try {
                    var response = receiveString();
                    if (response == null)
                        continue;

                    DiscordCommand command = Railroad.GSON.fromJson(response.payload(), DiscordCommand.class);
                    if (command == null)
                        continue;

                    handleCommand(command);
                    Thread.sleep(100);
                } catch (ClosedChannelException exception) {
                    break;
                } catch (IOException | InterruptedException exception) {
                    throw new RuntimeException("Failed to receive command from Discord IPC channel", exception);
                }
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    private void handleCommand(DiscordCommand command) {
        if (command.isError()) {
            Railroad.LOGGER.error("Received error from Discord IPC channel: {}", command);
            return;
        }

        if (command.getNonce() != null) {
            this.handlers.remove(command.getNonce()).accept(command);
        } else if (command.getEvt() != null) {
            DiscordEventHandler<?> handler = this.events.getHandler(command.getEvt());
            Object data = Railroad.GSON.fromJson(command.getData(), handler.getDataClass());
            handler.handleObject(command, data);
        }
    }

    private DiscordResponse receiveString() throws IOException {
        var header = ByteBuffer.allocate(8);
        this.ipcChannel.read(header);
        header.flip();
        header.order(ByteOrder.LITTLE_ENDIAN);
        if (header.remaining() == 0)
            return null;

        int status = header.getInt();
        int length = header.getInt();

        var data = ByteBuffer.allocate(length);
        int read = 0;
        do {
            read += (int) this.ipcChannel.read(new ByteBuffer[]{data}, 0, 1);
        } while (read < length);

        var message = new String(data.flip().array(), StandardCharsets.UTF_8);
        var state = DiscordConnectionState.VALUES[status];
        return new DiscordResponse(state, message);
    }

    private void sendHandshake() throws IOException {
        var message = new HandshakeMessage(this.clientId);
        sendString(Railroad.GSON.toJson(message));
    }

    private void sendBytes(byte[] bytes) throws IOException {
        var buffer = ByteBuffer.allocate(8 + bytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(this.connectionState.ordinal());
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        this.ipcChannel.write(buffer.flip());
    }

    private void sendString(String string) throws IOException {
        sendBytes(string.getBytes(StandardCharsets.UTF_8));
    }

    public void onReady() {
        this.connectionState = DiscordConnectionState.CONNECTED;
        registerEvents();
        Railroad.LOGGER.info("Discord IPC channel is ready");

        while (!this.commandQueue.isEmpty()) {
            var pair = this.commandQueue.poll();
            sendCommand(pair.getKey(), pair.getValue());
        }
    }

    private void registerEvents() {
        for (Map.Entry<DiscordCommand.Event, DiscordEventHandler<?>> handler : events.getHandlers()) {
            DiscordCommand.Event event = handler.getKey();
            DiscordEventHandler<?> eventHandler = handler.getValue();
            if (!eventHandler.shouldRegister())
                continue;

            var command = new DiscordCommand();
            command.setCmd(DiscordCommand.Type.SUBSCRIBE);
            command.setEvt(event);
            command.setArgs(Railroad.GSON.toJsonTree(eventHandler.getRegistrationArgs()));
            command.setNonce(Long.toString(++this.nonce));
            sendCommand(command, response -> Railroad.LOGGER.debug("Registered event {}", event.name()));
        }
    }

    private void sendCommand(DiscordCommand command, Consumer<DiscordCommand> callback) {
        if (this.connectionState == DiscordConnectionState.HANDSHAKE && command.getEvt() != DiscordCommand.Event.READY) {
            this.commandQueue.add(new Pair<>(command, callback));
            return;
        }

        this.handlers.put(command.getNonce(), callback);

        try {
            sendString(Railroad.GSON.toJson(command));
        } catch (IOException exception) {
            throw new RuntimeException("Failed to send command to Discord IPC channel", exception);
        }
    }

    public DiscordResult checkError(DiscordCommand command) {
        if (command.getEvt() == DiscordCommand.Event.ERROR) {
            var error = Railroad.GSON.fromJson(command.getData(), DiscordError.class);
            Railroad.LOGGER.error("Received error from Discord IPC channel: {}", error.getMessage());

            return DiscordResult.fromCode(error.getCode());
        }

        return DiscordResult.OK;
    }

    public void sendCommand(DiscordCommand.Type type, Object args, Consumer<DiscordCommand> object) {
        var command = new DiscordCommand();
        command.setCmd(type);
        command.setArgs(Railroad.GSON.toJsonTree(args).getAsJsonObject());
        command.setNonce(Long.toString(++this.nonce));
        sendCommand(command, object);
    }

    @Override
    public void close() throws RuntimeException {
        try {
            this.ipcChannel.close();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to close Discord IPC channel", exception);
        }

        this.isShuttingDown = true;
    }
}
