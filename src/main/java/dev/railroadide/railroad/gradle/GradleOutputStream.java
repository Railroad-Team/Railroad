package dev.railroadide.railroad.gradle;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

/**
 * An output stream that captures Gradle output and forwards it to a consumer.
 */
public class GradleOutputStream extends ByteArrayOutputStream {
    private final Consumer<String> outputConsumer;

    /**
     * Creates a new Gradle output stream.
     *
     * @param outputConsumer the consumer to forward output to
     */
    public GradleOutputStream(Consumer<String> outputConsumer) {
        this.outputConsumer = outputConsumer;
    }

    @Override
    public void writeBytes(byte @NotNull [] bytes) {
        super.writeBytes(bytes);
        flushOutput();
    }

    private void flushOutput() {
        String output = toString();
        if (!output.isEmpty()) {
            outputConsumer.accept(output);
            reset();
        }
    }
}
