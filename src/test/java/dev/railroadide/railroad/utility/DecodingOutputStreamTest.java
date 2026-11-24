package dev.railroadide.railroad.utility;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecodingOutputStreamTest {
    @Test
    public void multibyteCharacterSplitAcrossWritesIsDecodedOnce() {
        var decoded = new ArrayList<String>();
        var stream = new DecodingOutputStream(StandardCharsets.UTF_8, decoded::add);
        byte[] bytes = "a€b".getBytes(StandardCharsets.UTF_8);

        stream.write(bytes, 0, 2);
        stream.write(bytes, 2, 1);
        stream.write(bytes, 3, bytes.length - 3);

        assertEquals("a€b", String.join("", decoded));
        assertEquals(List.of("a", "€b"), decoded);
    }

    @Test
    public void singleByteWritesDecodeSurrogatePairs() {
        var decoded = new StringBuilder();
        var stream = new DecodingOutputStream(StandardCharsets.UTF_8, decoded::append);
        for (byte b : "x😀y".getBytes(StandardCharsets.UTF_8)) {
            stream.write(b);
        }

        assertEquals("x😀y", decoded.toString());
    }

    @Test
    public void closeFlushesIncompleteSequenceAndIgnoresLaterWrites() {
        var decoded = new StringBuilder();
        var stream = new DecodingOutputStream(StandardCharsets.UTF_8, decoded::append);
        byte[] euro = "€".getBytes(StandardCharsets.UTF_8);
        stream.write(euro, 0, 2);
        assertEquals("", decoded.toString());

        stream.close();
        stream.write('z');

        assertEquals("�", decoded.toString());
    }

    @Test
    public void writesLargerThanTheBufferAreDecodedCompletely() throws Exception {
        var decoded = new StringBuilder();
        var stream = new DecodingOutputStream(StandardCharsets.UTF_8, decoded::append);
        String text = "é".repeat(20_000);

        stream.write(text.getBytes(StandardCharsets.UTF_8));

        assertEquals(text, decoded.toString());
    }
}
