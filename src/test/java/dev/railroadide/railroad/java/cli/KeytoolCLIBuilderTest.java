package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.cli.impl.KeytoolCLIBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link KeytoolCLIBuilder} class, ensuring correct argument construction and behavior
 * for the `keytool` command-line utility.
 */
class KeytoolCLIBuilderTest {

    @Test
    void commandSelectionIsExclusive() {
        KeytoolCLIBuilder builder = KeytoolCLIBuilder.create(TestJdks.create(21));
        builder.listEntries();
        assertThrows(IllegalStateException.class, builder::generateKeyPair);
    }

    @Test
    void keystorePathAddsExpectedArgument() {
        KeytoolCLIBuilder builder = KeytoolCLIBuilder.create(TestJdks.create(21));
        Path keystorePath = Path.of("certs", "keys.jks");
        builder.keystore(keystorePath);

        List<String> arguments = CLIReflection.readField(builder, "arguments", List.class);
        assertTrue(arguments.contains("-keystore " + keystorePath));
    }

    @Test
    void tlsInfoAndSslServerArgumentsAreFormatted() {
        KeytoolCLIBuilder builder = KeytoolCLIBuilder.create(TestJdks.create(21));
        builder.tlsInfo("TLSv1.3");
        builder.sslServer("example.com:443");

        List<String> arguments = CLIReflection.readField(builder, "arguments", List.class);
        assertTrue(arguments.contains("-tls TLSv1.3"));
        assertTrue(arguments.contains("-sslserver example.com:443"));
    }

    @Test
    void timeoutCannotBeNegative() {
        KeytoolCLIBuilder builder = KeytoolCLIBuilder.create(TestJdks.create(21));
        assertThrows(IllegalArgumentException.class, () -> builder.setTimeout(-1, TimeUnit.SECONDS));
    }
}
