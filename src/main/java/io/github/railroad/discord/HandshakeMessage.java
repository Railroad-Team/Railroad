package io.github.railroad.discord;

public class HandshakeMessage {
    private final int v;
    private final String client_id;

    public HandshakeMessage(int v, String client_id) {
        this.v = v;
        this.client_id = client_id;
    }

    public HandshakeMessage(String client_id) {
        this(1, client_id);
    }

    public int getV() {
        return v;
    }

    public String getClientId() {
        return client_id;
    }
}
