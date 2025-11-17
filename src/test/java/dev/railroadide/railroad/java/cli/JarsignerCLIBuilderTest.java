package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.cli.impl.JarsignerCLIBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link JarsignerCLIBuilder} class, ensuring correct argument construction and behavior
 * for the `jarsigner` command-line tool.
 */
class JarsignerCLIBuilderTest {

    @Test
    void signConfiguresOperationAndClearsVerifyAliases() {
        JarsignerCLIBuilder builder = JarsignerCLIBuilder.create(TestJdks.create(21));
        builder.verify("old.jar", "alpha");
        builder.sign("app.jar", "releaseAlias");

        Enum<?> operation = CLIReflection.readField(builder, "operationMode", Enum.class);
        assertEquals("SIGN", operation.name());
        assertEquals("app.jar", CLIReflection.readField(builder, "jarFile", String.class));
        assertEquals("releaseAlias", CLIReflection.readField(builder, "signingAlias", String.class));
        List<String> verifyAliases = CLIReflection.readField(builder, "verifyAliases", List.class);
        assertTrue(verifyAliases.isEmpty(), "Verify aliases should be cleared when switching to sign mode");
    }

    @Test
    void verifyCollectsAliasesAndClearsSigningAlias() {
        JarsignerCLIBuilder builder = JarsignerCLIBuilder.create(TestJdks.create(21));
        builder.sign("app.jar", "releaseAlias");
        builder.verify("bundle.jar", "alpha", "beta");

        Enum<?> operation = CLIReflection.readField(builder, "operationMode", Enum.class);
        assertEquals("VERIFY", operation.name());
        assertEquals("bundle.jar", CLIReflection.readField(builder, "jarFile", String.class));
        assertNull(CLIReflection.readField(builder, "signingAlias", String.class));
        List<String> verifyAliases = CLIReflection.readField(builder, "verifyAliases", List.class);
        assertEquals(List.of("alpha", "beta"), verifyAliases);
    }

    @Test
    void storePasswordFromEnvironmentUsesExpectedSyntax() {
        JarsignerCLIBuilder builder = JarsignerCLIBuilder.create(TestJdks.create(21));
        builder.storePasswordFromEnv("STORE_PASS");

        List<String> arguments = CLIReflection.readField(builder, "arguments", List.class);
        assertEquals("-storepass:env STORE_PASS", arguments.getLast());
    }
}
