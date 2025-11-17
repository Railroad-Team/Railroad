package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.impl.*;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Provides a fluent API for accessing and executing command-line tools within a specific {@link JDK}.
 * This class acts as a factory for creating specialized {@link CLIBuilder} instances for various JDK executables
 * like {@code java}, {@code jar}, {@code javadoc}, etc.
 *
 * @param jdk The {@link JDK} instance this CLI toolset is bound to.
 */
public record JDKCLI(JDK jdk) {
    public JDKCLI(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a builder for launching a Java application from a compiled class file.
     *
     * @param mainClassPath The path to the main {@code .class} file.
     * @return A new {@link JavaExecutableCLIBuilder} instance.
     */
    public JavaExecutableCLIBuilder launchMainClass(Path mainClassPath) {
        return JavaExecutableCLIBuilder.classFile(jdk, mainClassPath);
    }

    /**
     * Creates a builder for launching a Java application from an executable JAR file.
     *
     * @param jarFilePath The path to the {@code .jar} file.
     * @return A new {@link JavaExecutableCLIBuilder} instance.
     */
    public JavaExecutableCLIBuilder launchJar(Path jarFilePath) {
        return JavaExecutableCLIBuilder.jarFile(jdk, jarFilePath);
    }

    /**
     * Creates a builder for launching a Java application from a module.
     *
     * @param moduleName The name of the module to launch.
     * @return A new {@link JavaExecutableCLIBuilder} instance.
     */
    public JavaExecutableCLIBuilder launchModule(String moduleName) {
        return JavaExecutableCLIBuilder.module(jdk, moduleName);
    }

    /**
     * Creates a builder for launching a single-file Java source-code program.
     *
     * @param sourceFilePath The path to the {@code .java} source file.
     * @return A new {@link JavaExecutableCLIBuilder} instance.
     */
    public JavaExecutableCLIBuilder launchSourceFile(Path sourceFilePath) {
        return JavaExecutableCLIBuilder.sourceFile(jdk, sourceFilePath);
    }

    /**
     * Returns a builder that exercises the {@code jar} tool to create, update, or inspect archive files.
     *
     * @return a new {@link JarCLIBuilder}
     */
    public JarCLIBuilder jar() {
        return JarCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for the {@code jarsigner} tool to sign or verify Java archives.
     *
     * @return a new {@link JarsignerCLIBuilder}
     */
    public JarsignerCLIBuilder jarsigner() {
        return JarsignerCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder that runs {@code javadoc} to generate API documentation from source files.
     *
     * @return a new {@link JavadocCLIBuilder}
     */
    public JavadocCLIBuilder javadoc() {
        return JavadocCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code javap} to disassemble class files and inspect their signatures.
     *
     * @return a new {@link JavapCLIBuilder}
     */
    public JavapCLIBuilder javap() {
        return JavapCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jcmd} to issue diagnostic commands to a running JVM.
     *
     * @return a new {@link JcmdCLIBuilder}
     */
    public JcmdCLIBuilder jcmd() {
        return JcmdCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jdb} to attach a debugger to a JVM session.
     *
     * @return a new {@link JdbCLIBuilder}
     */
    public JdbCLIBuilder jdb() {
        return JdbCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder that runs {@code jdeprscan} to scan for deprecated API usage.
     *
     * @return a new {@link JdeprscanCLIBuilder}
     */
    public JdeprscanCLIBuilder jdeprscan() {
        return JdeprscanCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jdeps} to analyze module and package dependencies.
     *
     * @return a new {@link JdepsCLIBuilder}
     */
    public JdepsCLIBuilder jdeps() {
        return JdepsCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder that interacts with {@code jfr} to manage Java Flight Recorder sessions.
     *
     * @return a new {@link JfrCLIBuilder}
     */
    public JfrCLIBuilder jfr() {
        return JfrCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jinfo} to print JVM configuration and system properties.
     *
     * @return a new {@link JinfoCLIBuilder}
     */
    public JinfoCLIBuilder jinfo() {
        return JinfoCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder that drives {@code jlink} to create custom runtime images.
     *
     * @return a new {@link JlinkCLIBuilder}
     */
    public JlinkCLIBuilder jlink() {
        return JlinkCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jmap} to inspect heap and memory maps of a JVM.
     *
     * @return a new {@link JmapCLIBuilder}
     */
    public JmapCLIBuilder jmap() {
        return JmapCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jmod} to create, describe, or verify modules.
     *
     * @return a new {@link JmodCLIBuilder}
     */
    public JmodCLIBuilder jmod() {
        return JmodCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jpackage} to package self-contained applications.
     *
     * @return a new {@link JpackageCLIBuilder}
     */
    public JpackageCLIBuilder jpackage() {
        return JpackageCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jps} to list the Java processes running on the machine.
     *
     * @return a new {@link JpsCLIBuilder}
     */
    public JpsCLIBuilder jps() {
        return JpsCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder that launches {@code jshell} for interactive REPL sessions.
     *
     * @return a new {@link JshellCLIBuilder}
     */
    public JshellCLIBuilder jshell() {
        return JshellCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jstack} to capture thread stack traces from a JVM.
     *
     * @return a new {@link JstackCLIBuilder}
     */
    public JstackCLIBuilder jstack() {
        return JstackCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code jstat} to query runtime statistics of a JVM.
     *
     * @return a new {@link JstatCLIBuilder}
     */
    public JstatCLIBuilder jstat() {
        return JstatCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder that starts {@code jstatd} for remote JVM monitoring.
     *
     * @return a new {@link JstatdCLIBuilder}
     */
    public JstatdCLIBuilder jstatd() {
        return JstatdCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code keytool} to manage keystores and certificates.
     *
     * @return a new {@link KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder keytool() {
        return KeytoolCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code rmic} to generate RMI stubs and skeletons.
     *
     * @return a new {@link RmicCLIBuilder}
     */
    public RmicCLIBuilder rmic() {
        return RmicCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code rmid} to launch the RMI activation daemon.
     *
     * @return a new {@link RmidCLIBuilder}
     */
    public RmidCLIBuilder rmid() {
        return RmidCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder that starts {@code rmiregistry} on a specified port.
     *
     * @return a new {@link RmiregistryCLIBuilder}
     */
    public RmiregistryCLIBuilder rmiregistry() {
        return RmiregistryCLIBuilder.create(jdk);
    }

    /**
     * Returns a builder for {@code serialver} to obtain {@code serialVersionUID} values for classes.
     *
     * @return a new {@link SerialverCLIBuilder}
     */
    public SerialverCLIBuilder serialver() {
        return SerialverCLIBuilder.create(jdk);
    }
}
