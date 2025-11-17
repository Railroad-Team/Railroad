package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.cli.impl.JarCLIBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link JarCLIBuilder} class, ensuring correct argument construction and behavior
 * for the `jar` command-line tool.
 */
class JarCLIBuilderTest {

    @Test
    void releaseEntriesRejectsVersionsBelowNine() {
        JarCLIBuilder builder = JarCLIBuilder.create(TestJdks.create(21));
        assertThrows(IllegalArgumentException.class, () -> builder.releaseEntries(8));
        assertDoesNotThrow(() -> builder.releaseEntries(9));
    }

    @Test
    void generateIndexSetsModeAndTarget() {
        JarCLIBuilder builder = JarCLIBuilder.create(TestJdks.create(21));
        Path jarPath = Path.of("libs", "app.jar");
        builder.generateIndex(jarPath);

        assertEquals(JarCLIBuilder.OperationMode.GENERATE_INDEX,
            CLIReflection.readField(builder, "operationMode", JarCLIBuilder.OperationMode.class));
        assertEquals(jarPath.toString(), CLIReflection.readField(builder, "generateIndexTarget", String.class));
    }

    @Test
    void modulePathUsesPlatformSeparator() {
        JarCLIBuilder builder = JarCLIBuilder.create(TestJdks.create(21));
        builder.modulePath("mods/one", "mods/two");

        List<String> arguments = CLIReflection.readField(builder, "arguments", List.class);
        assertTrue(arguments.contains("--module-path mods/one" + File.pathSeparator + "mods/two"));
    }

    @Test
    void runRequiresOperationMode() {
        JarCLIBuilder builder = JarCLIBuilder.create(TestJdks.create(21));
        assertThrows(IllegalStateException.class, builder::run);
    }
}
