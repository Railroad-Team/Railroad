package dev.railroadide.railroad.utility;

import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Decodes written bytes incrementally and forwards the decoded text as soon as it is complete.
 * <p>
 * A multibyte character split across writes is held back until its remaining bytes arrive, so callers may write
 * arbitrary byte chunks. Malformed input is replaced rather than rejected. Closing the stream flushes any incomplete
 * trailing sequence as a replacement character; bytes written after that are discarded, because producers such as a
 * cancelled Gradle build may still write briefly after their consumer has finished.
 */
public final class DecodingOutputStream extends OutputStream {
    private static final int BUFFER_SIZE = 8192;

    private final CharsetDecoder decoder;
    private final Consumer<String> sink;
    private final ByteBuffer input = ByteBuffer.allocate(BUFFER_SIZE);
    private final CharBuffer output = CharBuffer.allocate(BUFFER_SIZE);
    private boolean closed;

    /**
     * Creates a stream that decodes with the supplied charset.
     *
     * @param charset charset the written bytes are encoded in
     * @param sink receiver of each decoded, non-empty chunk of text
     */
    public DecodingOutputStream(Charset charset, Consumer<String> sink) {
        this.decoder = Objects.requireNonNull(charset, "charset").newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void write(int b) {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public synchronized void write(byte @NotNull [] bytes, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (closed)
            return;

        while (length > 0) {
            int count = Math.min(length, input.remaining());
            input.put(bytes, offset, count);
            offset += count;
            length -= count;
            decode(false);
        }
    }

    @Override
    public synchronized void close() {
        if (closed)
            return;

        closed = true;
        decode(true);
        while (decoder.flush(output).isOverflow()) {
            drain();
        }
        drain();
    }

    private void decode(boolean endOfInput) {
        input.flip();
        CoderResult result;
        do {
            result = decoder.decode(input, output, endOfInput);
            if (result.isOverflow()) {
                drain();
            }
        } while (result.isOverflow());
        input.compact();
        drain();
    }

    private void drain() {
        if (output.position() == 0)
            return;

        output.flip();
        String text = output.toString();
        output.clear();
        sink.accept(text);
    }
}
