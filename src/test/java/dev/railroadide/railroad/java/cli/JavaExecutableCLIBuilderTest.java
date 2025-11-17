package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.cli.impl.JavaExecutableCLIBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link JavaExecutableCLIBuilder} class, ensuring correct argument construction and behavior
 * for the `java` command-line executable.
 */
class JavaExecutableCLIBuilderTest {

    @Test
    void classFileRequiresClassExtension() {
        assertThrows(IllegalArgumentException.class,
            () -> JavaExecutableCLIBuilder.classFile(TestJdks.create(21), Path.of("Main.txt")));
    }

    @Test
    void jarFileRequiresJarExtension() {
        assertThrows(IllegalArgumentException.class,
            () -> JavaExecutableCLIBuilder.jarFile(TestJdks.create(21), Path.of("launcher.zip")));
    }

    @Test
    void sourceFileRequiresJavaExtension() {
        assertThrows(IllegalArgumentException.class,
            () -> JavaExecutableCLIBuilder.sourceFile(TestJdks.create(21), Path.of("Main.txt")));
    }

    @Test
    void agentlibArgumentsInsertedAtBeginning() {
        var builder = JavaExecutableCLIBuilder.module(TestJdks.create(21), "module");
        builder.addArgument("--existing");
        builder.agentlib("jdwp", "transport=dt_socket", "suspend=y");

        List<String> arguments = CLIReflection.readField(builder, "arguments", List.class);
        assertEquals("-agentlib:jdwp=transport=dt_socket,suspend=y", arguments.get(0));
        assertEquals("--existing", arguments.get(1));
    }

    @Test
    void enablePreviewFeaturesRequiresJdk12() {
        var jdk11Builder = JavaExecutableCLIBuilder.module(TestJdks.create(11), "module");
        assertThrows(UnsupportedOperationException.class, jdk11Builder::enablePreviewFeatures);

        var jdk12Builder = JavaExecutableCLIBuilder.module(TestJdks.create(12), "module");
        jdk12Builder.enablePreviewFeatures();
        List<String> arguments = CLIReflection.readField(jdk12Builder, "arguments", List.class);
        assertTrue(arguments.contains("--enable-preview"));
    }

    @Test
    void enableNativeAccessRequiresJdk16() {
        var jdk15Builder = JavaExecutableCLIBuilder.module(TestJdks.create(15), "module");
        assertThrows(UnsupportedOperationException.class, () -> jdk15Builder.enableNativeAccess("mod"));

        var jdk16Builder = JavaExecutableCLIBuilder.module(TestJdks.create(16), "module");
        jdk16Builder.enableNativeAccess("com.example");
        List<String> arguments = CLIReflection.readField(jdk16Builder, "arguments", List.class);
        assertTrue(arguments.contains("--enable-native-access=com.example"));
    }

    @Test
    void illegalNativeAccessValidatesVersionAndMode() {
        var jdk23Builder = JavaExecutableCLIBuilder.module(TestJdks.create(23), "module");
        assertThrows(UnsupportedOperationException.class,
            () -> jdk23Builder.illegalNativeAccess(JavaExecutableCLIBuilder.AccessMode.ALLOW));

        var jdk24Builder = JavaExecutableCLIBuilder.module(TestJdks.create(24), "module");
        assertThrows(UnsupportedOperationException.class,
            () -> jdk24Builder.illegalNativeAccess(JavaExecutableCLIBuilder.AccessMode.DEBUG));

        jdk24Builder.illegalNativeAccess(JavaExecutableCLIBuilder.AccessMode.WARN);
        List<String> arguments = CLIReflection.readField(jdk24Builder, "arguments", List.class);
        assertTrue(arguments.contains("--illegal-native-access=warn"));
    }

    @Test
    void finalizationRequiresJdk18() {
        var jdk17Builder = JavaExecutableCLIBuilder.module(TestJdks.create(17), "module");
        assertThrows(UnsupportedOperationException.class,
            () -> jdk17Builder.finalization(JavaExecutableCLIBuilder.EnabledDisabled.ENABLED));

        var jdk18Builder = JavaExecutableCLIBuilder.module(TestJdks.create(18), "module");
        jdk18Builder.finalization(JavaExecutableCLIBuilder.EnabledDisabled.DISABLED);
        List<String> arguments = CLIReflection.readField(jdk18Builder, "arguments", List.class);
        assertTrue(arguments.contains("--finalization=disabled"));
    }
}
