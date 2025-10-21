package dev.railroadide.railroad.utility;

import java.io.OutputStream;

public class DiscardingOutputStream extends OutputStream {
    @Override
    public void write(int b) {
        // Discard the byte
    }
}
