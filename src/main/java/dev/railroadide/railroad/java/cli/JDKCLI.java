package dev.railroadide.railroad.java.cli;

import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.impl.*;

import java.nio.file.Path;
import java.util.Objects;

public record JDKCLI(JDK jdk) {
    public JDKCLI(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public JavaExecutableCLIBuilder launchMainClass(Path mainClassPath) {
        return JavaExecutableCLIBuilder.classFile(jdk, mainClassPath);
    }

    public JavaExecutableCLIBuilder launchJar(Path jarFilePath) {
        return JavaExecutableCLIBuilder.jarFile(jdk, jarFilePath);
    }

    public JavaExecutableCLIBuilder launchModule(String moduleName) {
        return JavaExecutableCLIBuilder.module(jdk, moduleName);
    }

    public JavaExecutableCLIBuilder launchSourceFile(Path sourceFilePath) {
        return JavaExecutableCLIBuilder.sourceFile(jdk, sourceFilePath);
    }

    public JarCLIBuilder jar() {
        return JarCLIBuilder.create(jdk);
    }

    public JarsignerCLIBuilder jarsigner() {
        return JarsignerCLIBuilder.create(jdk);
    }

    public JavadocCLIBuilder javadoc() {
        return JavadocCLIBuilder.create(jdk);
    }

    public JavapCLIBuilder javap() {
        return JavapCLIBuilder.create(jdk);
    }

    public JcmdCLIBuilder jcmd() {
        return JcmdCLIBuilder.create(jdk);
    }

    public JdbCLIBuilder jdb() {
        return JdbCLIBuilder.create(jdk);
    }

    public JdeprscanCLIBuilder jdeprscan() {
        return JdeprscanCLIBuilder.create(jdk);
    }

    public JdepsCLIBuilder jdeps() {
        return JdepsCLIBuilder.create(jdk);
    }

    public JfrCLIBuilder jfr() {
        return JfrCLIBuilder.create(jdk);
    }

    public JinfoCLIBuilder jinfo() {
        return JinfoCLIBuilder.create(jdk);
    }

    public JlinkCLIBuilder jlink() {
        return JlinkCLIBuilder.create(jdk);
    }

    public JmapCLIBuilder jmap() {
        return JmapCLIBuilder.create(jdk);
    }

    public JmodCLIBuilder jmod() {
        return JmodCLIBuilder.create(jdk);
    }

    public JpackageCLIBuilder jpackage() {
        return JpackageCLIBuilder.create(jdk);
    }

    public JpsCLIBuilder jps() {
        return JpsCLIBuilder.create(jdk);
    }

    public JshellCLIBuilder jshell() {
        return JshellCLIBuilder.create(jdk);
    }

    public JstackCLIBuilder jstack() {
        return JstackCLIBuilder.create(jdk);
    }

    public JstatCLIBuilder jstat() {
        return JstatCLIBuilder.create(jdk);
    }

    public JstatdCLIBuilder jstatd() {
        return JstatdCLIBuilder.create(jdk);
    }

    public KeytoolCLIBuilder keytool() {
        return KeytoolCLIBuilder.create(jdk);
    }

    public RmicCLIBuilder rmic() {
        return RmicCLIBuilder.create(jdk);
    }

    public RmidCLIBuilder rmid() {
        return RmidCLIBuilder.create(jdk);
    }

    public RmiregistryCLIBuilder rmiregistry() {
        return RmiregistryCLIBuilder.create(jdk);
    }

    public SerialverCLIBuilder serialver() {
        return SerialverCLIBuilder.create(jdk);
    }
}
