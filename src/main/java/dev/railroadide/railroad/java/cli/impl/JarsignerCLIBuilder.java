package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;
import lombok.Getter;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JarsignerCLIBuilder implements CLIBuilder<Process, JarsignerCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jarsigner.exe" : "jarsigner";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> verifyAliases = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private OperationMode operationMode = OperationMode.SIGN;
    private String jarFile;
    private String signingAlias;

    private JarsignerCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JarsignerCLIBuilder create(JDK jdk) {
        return new JarsignerCLIBuilder(jdk);
    }

    @Override
    public JarsignerCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JarsignerCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JarsignerCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JarsignerCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JarsignerCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JarsignerCLIBuilder sign(Path jarFile, String alias) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.operationMode = OperationMode.SIGN;
        this.jarFile = jarFile.toString();
        this.signingAlias = alias;
        this.verifyAliases.clear();
        return this;
    }

    public JarsignerCLIBuilder sign(String jarFile, String alias) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.operationMode = OperationMode.SIGN;
        this.jarFile = jarFile;
        this.signingAlias = alias;
        this.verifyAliases.clear();
        return this;
    }

    public JarsignerCLIBuilder verify(Path jarFile, String... aliases) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        this.operationMode = OperationMode.VERIFY;
        this.jarFile = jarFile.toString();
        this.signingAlias = null;
        this.verifyAliases.clear();
        if (aliases != null) {
            for (String alias : aliases) {
                addVerifyAlias(alias);
            }
        }

        return this;
    }

    public JarsignerCLIBuilder verify(String jarFile, String... aliases) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        this.operationMode = OperationMode.VERIFY;
        this.jarFile = jarFile;
        this.signingAlias = null;
        this.verifyAliases.clear();
        if (aliases != null) {
            for (String alias : aliases) {
                addVerifyAlias(alias);
            }
        }

        return this;
    }

    public JarsignerCLIBuilder version() {
        this.operationMode = OperationMode.VERSION;
        this.jarFile = null;
        this.signingAlias = null;
        this.verifyAliases.clear();
        return this;
    }

    public JarsignerCLIBuilder jarFile(Path jarFile) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        this.jarFile = jarFile.toString();
        return this;
    }

    public JarsignerCLIBuilder jarFile(String jarFile) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        this.jarFile = jarFile;
        return this;
    }

    public JarsignerCLIBuilder signingAlias(String alias) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.signingAlias = alias;
        return this;
    }

    public JarsignerCLIBuilder addVerifyAlias(String alias) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.verifyAliases.add(alias);
        return this;
    }

    public JarsignerCLIBuilder addVerifyAliases(String... aliases) {
        Objects.requireNonNull(aliases, "Aliases cannot be null");
        for (String alias : aliases) {
            addVerifyAlias(alias);
        }

        return this;
    }

    public JarsignerCLIBuilder keystore(Path keystorePath) {
        Objects.requireNonNull(keystorePath, "Keystore path cannot be null");
        return keystore(keystorePath.toString());
    }

    public JarsignerCLIBuilder keystore(String location) {
        Objects.requireNonNull(location, "Keystore location cannot be null");
        this.arguments.add("-keystore " + location);
        return this;
    }

    public JarsignerCLIBuilder storePassword(String password) {
        return addPasswordArgument("-storepass", PasswordSource.DIRECT, password);
    }

    public JarsignerCLIBuilder storePasswordFromEnv(String envVariable) {
        return addPasswordArgument("-storepass", PasswordSource.ENVIRONMENT, envVariable);
    }

    public JarsignerCLIBuilder storePasswordFromFile(Path file) {
        Objects.requireNonNull(file, "Password file cannot be null");
        return addPasswordArgument("-storepass", PasswordSource.FILE, file.toString());
    }

    public JarsignerCLIBuilder storePasswordFromFile(String file) {
        return addPasswordArgument("-storepass", PasswordSource.FILE, file);
    }

    public JarsignerCLIBuilder storeType(String storeType) {
        Objects.requireNonNull(storeType, "Store type cannot be null");
        this.arguments.add("-storetype " + storeType);
        return this;
    }

    public JarsignerCLIBuilder keyPassword(String password) {
        return addPasswordArgument("-keypass", PasswordSource.DIRECT, password);
    }

    public JarsignerCLIBuilder keyPasswordFromEnv(String envVariable) {
        return addPasswordArgument("-keypass", PasswordSource.ENVIRONMENT, envVariable);
    }

    public JarsignerCLIBuilder keyPasswordFromFile(Path file) {
        Objects.requireNonNull(file, "Password file cannot be null");
        return addPasswordArgument("-keypass", PasswordSource.FILE, file.toString());
    }

    public JarsignerCLIBuilder keyPasswordFromFile(String file) {
        return addPasswordArgument("-keypass", PasswordSource.FILE, file);
    }

    public JarsignerCLIBuilder certificateChain(Path certChainFile) {
        Objects.requireNonNull(certChainFile, "Certificate chain file cannot be null");
        this.arguments.add("-certchain " + certChainFile);
        return this;
    }

    public JarsignerCLIBuilder certificateChain(String certChainFile) {
        Objects.requireNonNull(certChainFile, "Certificate chain file cannot be null");
        this.arguments.add("-certchain " + certChainFile);
        return this;
    }

    public JarsignerCLIBuilder signatureFile(String baseName) {
        Objects.requireNonNull(baseName, "Signature file base name cannot be null");
        this.arguments.add("-sigfile " + baseName);
        return this;
    }

    public JarsignerCLIBuilder signedJar(Path signedJarPath) {
        Objects.requireNonNull(signedJarPath, "Signed JAR path cannot be null");
        this.arguments.add("-signedjar " + signedJarPath);
        return this;
    }

    public JarsignerCLIBuilder signedJar(String signedJarPath) {
        Objects.requireNonNull(signedJarPath, "Signed JAR path cannot be null");
        this.arguments.add("-signedjar " + signedJarPath);
        return this;
    }

    public JarsignerCLIBuilder digestAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Digest algorithm cannot be null");
        this.arguments.add("-digestalg " + algorithm);
        return this;
    }

    public JarsignerCLIBuilder signatureAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Signature algorithm cannot be null");
        this.arguments.add("-sigalg " + algorithm);
        return this;
    }

    public JarsignerCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    public JarsignerCLIBuilder verbose(VerboseDetail detail) {
        Objects.requireNonNull(detail, "Verbose detail cannot be null");
        if (detail == VerboseDetail.ALL) {
            this.arguments.add("-verbose");
        } else {
            this.arguments.add("-verbose:" + detail.getToken());
        }

        return this;
    }

    public JarsignerCLIBuilder includeCertificateDetails() {
        this.arguments.add("-certs");
        return this;
    }

    public JarsignerCLIBuilder enableRevocationCheck() {
        this.arguments.add("-revCheck");
        return this;
    }

    public JarsignerCLIBuilder timestampAuthority(String url) {
        Objects.requireNonNull(url, "TSA URL cannot be null");
        this.arguments.add("-tsa " + url);
        return this;
    }

    public JarsignerCLIBuilder timestampAuthorityCertificate(String alias) {
        Objects.requireNonNull(alias, "TSA certificate alias cannot be null");
        this.arguments.add("-tsacert " + alias);
        return this;
    }

    public JarsignerCLIBuilder timestampPolicyId(String policyId) {
        Objects.requireNonNull(policyId, "Policy ID cannot be null");
        this.arguments.add("-tsapolicyid " + policyId);
        return this;
    }

    public JarsignerCLIBuilder timestampDigestAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Timestamp digest algorithm cannot be null");
        this.arguments.add("-tsadigestalg " + algorithm);
        return this;
    }

    public JarsignerCLIBuilder includeSignatureFileInBlock() {
        this.arguments.add("-internalsf");
        return this;
    }

    public JarsignerCLIBuilder sectionsOnly() {
        this.arguments.add("-sectionsonly");
        return this;
    }

    public JarsignerCLIBuilder protectedAuthentication(boolean required) {
        this.arguments.add("-protected " + required);
        return this;
    }

    public JarsignerCLIBuilder providerName(String providerName) {
        Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.arguments.add("-providerName " + providerName);
        return this;
    }

    public JarsignerCLIBuilder addProvider(String providerName) {
        Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.arguments.add("-addprovider " + providerName);
        return this;
    }

    public JarsignerCLIBuilder addProvider(String providerName, String providerArg) {
        Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.arguments.add("-addprovider " + providerName);
        Objects.requireNonNull(providerArg, "Provider argument cannot be null");
        this.arguments.add("-providerArg " + providerArg);
        return this;
    }

    public JarsignerCLIBuilder providerClass(String className) {
        Objects.requireNonNull(className, "Provider class cannot be null");
        this.arguments.add("-providerClass " + className);
        return this;
    }

    public JarsignerCLIBuilder providerClass(String className, String providerArg) {
        Objects.requireNonNull(className, "Provider class cannot be null");
        this.arguments.add("-providerClass " + className);
        Objects.requireNonNull(providerArg, "Provider argument cannot be null");
        this.arguments.add("-providerArg " + providerArg);
        return this;
    }

    public JarsignerCLIBuilder providerPath(String classpath) {
        Objects.requireNonNull(classpath, "Provider path cannot be null");
        this.arguments.add("-providerPath " + classpath);
        return this;
    }

    public JarsignerCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    public JarsignerCLIBuilder strictMode() {
        this.arguments.add("-strict");
        return this;
    }

    public JarsignerCLIBuilder configurationFile(String url) {
        Objects.requireNonNull(url, "Configuration URL cannot be null");
        this.arguments.add("-conf " + url);
        return this;
    }

    @Override
    public Process run() {
        if (operationMode == OperationMode.SIGN) {
            if (jarFile == null)
                throw new IllegalStateException("A JAR file must be specified when signing.");
            if (signingAlias == null)
                throw new IllegalStateException("An alias must be provided when signing.");
        } else if (operationMode == OperationMode.VERIFY) {
            if (jarFile == null)
                throw new IllegalStateException("A JAR file must be specified when verifying.");
        }

        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        if (operationMode.getFlag() != null) {
            command.add(operationMode.getFlag());
        }

        command.addAll(arguments);
        if (operationMode != OperationMode.VERSION && jarFile != null) {
            command.add(jarFile);
            if (operationMode == OperationMode.SIGN) {
                command.add(signingAlias);
            } else if (operationMode == OperationMode.VERIFY && !verifyAliases.isEmpty()) {
                command.addAll(verifyAliases);
            }
        }

        var processBuilder = new ProcessBuilder();
        processBuilder.command(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        if (useSystemEnvVars) {
            Map<String, String> env = processBuilder.environment();
            env.putAll(environmentVariables);
        } else {
            processBuilder.environment().clear();
            processBuilder.environment().putAll(environmentVariables);
        }

        try {
            Process process = processBuilder.start();
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jarsigner");
            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jarsigner process", exception);
        }
    }

    private JarsignerCLIBuilder addPasswordArgument(String option, PasswordSource source, String value) {
        Objects.requireNonNull(option, "Option cannot be null");
        Objects.requireNonNull(source, "Password source cannot be null");
        Objects.requireNonNull(value, "Password value cannot be null");
        StringBuilder builder = new StringBuilder(option);
        if (!source.getSuffix().isEmpty()) {
            builder.append(source.getSuffix());
        }

        builder.append(" ").append(value);
        this.arguments.add(builder.toString());
        return this;
    }

    @Getter
    public enum VerboseDetail {
        ALL("all"),
        GROUPED("grouped"),
        SUMMARY("summary");

        private final String token;

        VerboseDetail(String token) {
            this.token = token;
        }
    }

    @Getter
    private enum PasswordSource {
        DIRECT(""),
        ENVIRONMENT(":env"),
        FILE(":file");

        private final String suffix;

        PasswordSource(String suffix) {
            this.suffix = suffix;
        }
    }

    @Getter
    private enum OperationMode {
        SIGN(null),
        VERIFY("-verify"),
        VERSION("-version");

        private final String flag;

        OperationMode(String flag) {
            this.flag = flag;
        }
    }
}
