package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.JDK;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the {@link JDKCLI} class, ensuring that CLI builders correctly reference the associated JDK.
 */
class JDKCLITest {
    private final JDK jdk = TestJdks.create(21);

    @Test
    void launchBuildersReferenceProvidedJdk() {
        JDKCLI cli = new JDKCLI(jdk);
        assertBuilderHasJdk(cli.launchMainClass(Path.of("Main.class")), "launchMainClass");
        assertBuilderHasJdk(cli.launchJar(Path.of("app.jar")), "launchJar");
        assertBuilderHasJdk(cli.launchModule("module.name"), "launchModule");
        assertBuilderHasJdk(cli.launchSourceFile(Path.of("Example.java")), "launchSourceFile");
    }

    @Test
    void toolBuildersReferenceProvidedJdk() {
        JDKCLI cli = new JDKCLI(jdk);
        Map<String, Object> builders = new LinkedHashMap<>();
        builders.put("jar", cli.jar());
        builders.put("jarsigner", cli.jarsigner());
        builders.put("javadoc", cli.javadoc());
        builders.put("javap", cli.javap());
        builders.put("jcmd", cli.jcmd());
        builders.put("jdb", cli.jdb());
        builders.put("jdeprscan", cli.jdeprscan());
        builders.put("jdeps", cli.jdeps());
        builders.put("jfr", cli.jfr());
        builders.put("jinfo", cli.jinfo());
        builders.put("jlink", cli.jlink());
        builders.put("jmap", cli.jmap());
        builders.put("jmod", cli.jmod());
        builders.put("jpackage", cli.jpackage());
        builders.put("jps", cli.jps());
        builders.put("jshell", cli.jshell());
        builders.put("jstack", cli.jstack());
        builders.put("jstat", cli.jstat());
        builders.put("jstatd", cli.jstatd());
        builders.put("keytool", cli.keytool());
        builders.put("rmic", cli.rmic());
        builders.put("rmid", cli.rmid());
        builders.put("rmiregistry", cli.rmiregistry());
        builders.put("serialver", cli.serialver());

        builders.forEach((name, builder) -> assertBuilderHasJdk(builder, name));
    }

    private void assertBuilderHasJdk(Object builder, String name) {
        JDK stored = CLIReflection.readField(builder, "jdk", JDK.class);
        assertSame(jdk, stored, () -> name + " should retain the original JDK reference");
    }
}
