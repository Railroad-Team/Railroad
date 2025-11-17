package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.JDK;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the execution of various CLI tools provided by a JDK, such as keytool and the Java launcher.
 */
class JDKCliToolExecutionTest {

    @Test
    void keytoolVersionCommandRunsOnCurrentJdk() throws IOException {
        JDK jdk = TestJdks.currentRuntime();
        Process process = jdk.cli().keytool()
            .version()
            .setTimeout(5, TimeUnit.SECONDS)
            .run();

        assertEquals(0, process.exitValue(), "keytool -version should exit successfully");

        String stdout = readFully(process.getInputStream());
        String stderr = readFully(process.getErrorStream());
        String output = stdout.isBlank() ? stderr : stdout;

        assertFalse(output.isBlank(), "keytool should report its version");
        assertTrue(output.toLowerCase(Locale.ROOT).contains("keytool"),
            "version output should mention keytool");
    }

    @Test
    void javaLauncherRunsSourceFile() throws IOException {
        JDK jdk = TestJdks.currentRuntime();
        Path tempDir = Files.createTempDirectory("jdk-cli-src");
        Path sourceFile = tempDir.resolve("CliHello.java");
        Files.writeString(sourceFile, """
            public class CliHello {
                public static void main(String[] args) {
                    System.out.print("hello from java");
                }
            }
            """);

        Process process = jdk.cli().launchSourceFile(sourceFile)
            .setTimeout(5, TimeUnit.SECONDS)
            .run();

        assertEquals(0, process.exitValue(), "java should execute the generated source file");
        String stdout = readFully(process.getInputStream()).trim();
        assertEquals("hello from java", stdout);
    }

    private static String readFully(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
