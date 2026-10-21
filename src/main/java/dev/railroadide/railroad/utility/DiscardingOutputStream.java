package dev.railroadide.railroad.utility;

import java.io.OutputStream;

/**
 * An OutputStream that discards all data written to it.
 */
public class DiscardingOutputStream extends OutputStream {
    @Override
    public void write(int b) {
        // Discard the byte
    }
}
