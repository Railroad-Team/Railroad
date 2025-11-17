package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKUtils;
import dev.railroadide.railroad.utility.JavaVersion;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Utility class for creating and retrieving JDK instances for testing purposes.
 */
final class TestJdks {
    private TestJdks() {
    }

    static JDK create(int release) {
        Path home = Path.of("build", "test-jdk-" + release + "-" + UUID.randomUUID());
        return new JDK(home, "test-jdk-" + release, JavaVersion.fromMajor(release), JDK.Brand.UNKNOWN);
    }

    static JDK currentRuntime() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank())
            throw new IllegalStateException("java.home property is not set");

        JDK jdk = JDKUtils.createJDKFromAnyPath(javaHome);
        if (jdk == null)
            throw new IllegalStateException("Unable to resolve the running JDK at " + javaHome);

        return jdk;
    }
}
