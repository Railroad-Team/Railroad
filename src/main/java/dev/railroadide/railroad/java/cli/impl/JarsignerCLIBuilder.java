package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;
import lombok.Getter;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A fluent builder for constructing and executing {@code jarsigner} commands.
 * <p>
 * This builder provides methods to configure various options for signing and verifying JAR files,
 * including keystore details, passwords, algorithms, and verbose output.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jarsigner.html">jarsigner command documentation</a>
 */
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

    /**
     * Creates a new {@code JarsignerCLIBuilder} instance.
     *
     * @param jdk The JDK to use for executing the {@code jarsigner} command.
     * @return A new builder instance.
     */
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

    /**
     * Configures the builder to sign a JAR file with a specified alias.
     *
     * @param jarFile The path to the JAR file to sign.
     * @param alias   The alias of the signer in the keystore.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder sign(Path jarFile, String alias) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.operationMode = OperationMode.SIGN;
        this.jarFile = jarFile.toString();
        this.signingAlias = alias;
        this.verifyAliases.clear();
        return this;
    }

    /**
     * Configures the builder to sign a JAR file with a specified alias.
     *
     * @param jarFile The path to the JAR file to sign.
     * @param alias   The alias of the signer in the keystore.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder sign(String jarFile, String alias) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.operationMode = OperationMode.SIGN;
        this.jarFile = jarFile;
        this.signingAlias = alias;
        this.verifyAliases.clear();
        return this;
    }

    /**
     * Configures the builder to verify a JAR file.
     *
     * @param jarFile The path to the JAR file to verify.
     * @param aliases Optional aliases to verify against.
     * @return This builder instance.
     */
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

    /**
     * Configures the builder to verify a JAR file.
     *
     * @param jarFile The path to the JAR file to verify.
     * @param aliases Optional aliases to verify against.
     * @return This builder instance.
     */
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

    /**
     * Configures the builder to display version information for {@code jarsigner}.
     *
     * @return This builder instance.
     */
    public JarsignerCLIBuilder version() {
        this.operationMode = OperationMode.VERSION;
        this.jarFile = null;
        this.signingAlias = null;
        this.verifyAliases.clear();
        return this;
    }

    /**
     * Sets the JAR file to be signed or verified.
     *
     * @param jarFile The path to the JAR file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder jarFile(Path jarFile) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        this.jarFile = jarFile.toString();
        return this;
    }

    /**
     * Sets the JAR file to be signed or verified.
     *
     * @param jarFile The path to the JAR file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder jarFile(String jarFile) {
        Objects.requireNonNull(jarFile, "JAR file cannot be null");
        this.jarFile = jarFile;
        return this;
    }

    /**
     * Sets the alias of the signer in the keystore.
     *
     * @param alias The alias of the signer.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder signingAlias(String alias) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.signingAlias = alias;
        return this;
    }

    /**
     * Adds an alias to verify against when in verify mode.
     *
     * @param alias The alias to add.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder addVerifyAlias(String alias) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.verifyAliases.add(alias);
        return this;
    }

    /**
     * Adds multiple aliases to verify against when in verify mode.
     *
     * @param aliases The aliases to add.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder addVerifyAliases(String... aliases) {
        Objects.requireNonNull(aliases, "Aliases cannot be null");
        for (String alias : aliases) {
            addVerifyAlias(alias);
        }

        return this;
    }

    /**
     * Specifies the keystore to use. Corresponds to the {@code -keystore} option.
     *
     * @param keystorePath The path to the keystore file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder keystore(Path keystorePath) {
        Objects.requireNonNull(keystorePath, "Keystore path cannot be null");
        return keystore(keystorePath.toString());
    }

    /**
     * Specifies the keystore to use. Corresponds to the {@code -keystore} option.
     *
     * @param location The location of the keystore.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder keystore(String location) {
        Objects.requireNonNull(location, "Keystore location cannot be null");
        this.arguments.add("-keystore " + location);
        return this;
    }

    /**
     * Sets the password for the keystore. Corresponds to the {@code -storepass} option.
     *
     * @param password The keystore password.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder storePassword(String password) {
        return addPasswordArgument("-storepass", PasswordSource.DIRECT, password);
    }

    /**
     * Sets the keystore password from an environment variable. Corresponds to the {@code -storepass:env} option.
     *
     * @param envVariable The name of the environment variable.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder storePasswordFromEnv(String envVariable) {
        return addPasswordArgument("-storepass", PasswordSource.ENVIRONMENT, envVariable);
    }

    /**
     * Sets the keystore password from a file. Corresponds to the {@code -storepass:file} option.
     *
     * @param file The path to the password file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder storePasswordFromFile(Path file) {
        Objects.requireNonNull(file, "Password file cannot be null");
        return addPasswordArgument("-storepass", PasswordSource.FILE, file.toString());
    }

    /**
     * Sets the keystore password from a file. Corresponds to the {@code -storepass:file} option.
     *
     * @param file The path to the password file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder storePasswordFromFile(String file) {
        return addPasswordArgument("-storepass", PasswordSource.FILE, file);
    }

    /**
     * Sets the keystore type. Corresponds to the {@code -storetype} option.
     *
     * @param storeType The keystore type.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder storeType(String storeType) {
        Objects.requireNonNull(storeType, "Store type cannot be null");
        this.arguments.add("-storetype " + storeType);
        return this;
    }

    /**
     * Sets the password for the private key. Corresponds to the {@code -keypass} option.
     *
     * @param password The private key password.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder keyPassword(String password) {
        return addPasswordArgument("-keypass", PasswordSource.DIRECT, password);
    }

    /**
     * Sets the private key password from an environment variable. Corresponds to the {@code -keypass:env} option.
     *
     * @param envVariable The name of the environment variable.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder keyPasswordFromEnv(String envVariable) {
        return addPasswordArgument("-keypass", PasswordSource.ENVIRONMENT, envVariable);
    }

    /**
     * Sets the private key password from a file. Corresponds to the {@code -keypass:file} option.
     *
     * @param file The path to the password file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder keyPasswordFromFile(Path file) {
        Objects.requireNonNull(file, "Password file cannot be null");
        return addPasswordArgument("-keypass", PasswordSource.FILE, file.toString());
    }

    /**
     * Sets the private key password from a file. Corresponds to the {@code -keypass:file} option.
     *
     * @param file The path to the password file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder keyPasswordFromFile(String file) {
        return addPasswordArgument("-keypass", PasswordSource.FILE, file);
    }

    /**
     * Specifies an alternative certificate chain file. Corresponds to the {@code -certchain} option.
     *
     * @param certChainFile The path to the certificate chain file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder certificateChain(Path certChainFile) {
        Objects.requireNonNull(certChainFile, "Certificate chain file cannot be null");
        this.arguments.add("-certchain " + certChainFile);
        return this;
    }

    /**
     * Specifies an alternative certificate chain file. Corresponds to the {@code -certchain} option.
     *
     * @param certChainFile The path to the certificate chain file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder certificateChain(String certChainFile) {
        Objects.requireNonNull(certChainFile, "Certificate chain file cannot be null");
        this.arguments.add("-certchain " + certChainFile);
        return this;
    }

    /**
     * Specifies the base name for the signature file. Corresponds to the {@code -sigfile} option.
     *
     * @param baseName The base name for the signature file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder signatureFile(String baseName) {
        Objects.requireNonNull(baseName, "Signature file base name cannot be null");
        this.arguments.add("-sigfile " + baseName);
        return this;
    }

    /**
     * Specifies the name of the signed JAR file. Corresponds to the {@code -signedjar} option.
     *
     * @param signedJarPath The path to the signed JAR file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder signedJar(Path signedJarPath) {
        Objects.requireNonNull(signedJarPath, "Signed JAR path cannot be null");
        this.arguments.add("-signedjar " + signedJarPath);
        return this;
    }

    /**
     * Specifies the name of the signed JAR file. Corresponds to the {@code -signedjar} option.
     *
     * @param signedJarPath The path to the signed JAR file.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder signedJar(String signedJarPath) {
        Objects.requireNonNull(signedJarPath, "Signed JAR path cannot be null");
        this.arguments.add("-signedjar " + signedJarPath);
        return this;
    }

    /**
     * Specifies the digest algorithm to use. Corresponds to the {@code -digestalg} option.
     *
     * @param algorithm The digest algorithm.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder digestAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Digest algorithm cannot be null");
        this.arguments.add("-digestalg " + algorithm);
        return this;
    }

    /**
     * Specifies the signature algorithm to use. Corresponds to the {@code -sigalg} option.
     *
     * @param algorithm The signature algorithm.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder signatureAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Signature algorithm cannot be null");
        this.arguments.add("-sigalg " + algorithm);
        return this;
    }

    /**
     * Enables verbose output. Corresponds to the {@code -verbose} option.
     *
     * @return This builder instance.
     */
    public JarsignerCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    /**
     * Enables verbose output with a specified detail level. Corresponds to the {@code -verbose:detail} option.
     *
     * @param detail The level of verbose detail.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder verbose(VerboseDetail detail) {
        Objects.requireNonNull(detail, "Verbose detail cannot be null");
        if (detail == VerboseDetail.ALL) {
            this.arguments.add("-verbose");
        } else {
            this.arguments.add("-verbose:" + detail.getToken());
        }

        return this;
    }

    /**
     * Includes certificate details in the output. Corresponds to the {@code -certs} option.
     *
     * @return This builder instance.
     */
    public JarsignerCLIBuilder includeCertificateDetails() {
        this.arguments.add("-certs");
        return this;
    }

    /**
     * Enables revocation checking. Corresponds to the {@code -revCheck} option.
     *
     * @return This builder instance.
     */
    public JarsignerCLIBuilder enableRevocationCheck() {
        this.arguments.add("-revCheck");
        return this;
    }

    /**
     * Specifies the URL of the Timestamping Authority (TSA). Corresponds to the {@code -tsa} option.
     *
     * @param url The URL of the TSA.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder timestampAuthority(String url) {
        Objects.requireNonNull(url, "TSA URL cannot be null");
        this.arguments.add("-tsa " + url);
        return this;
    }

    /**
     * Specifies the alias of the Timestamping Authority (TSA) certificate. Corresponds to the {@code -tsacert} option.
     *
     * @param alias The alias of the TSA certificate.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder timestampAuthorityCertificate(String alias) {
        Objects.requireNonNull(alias, "TSA certificate alias cannot be null");
        this.arguments.add("-tsacert " + alias);
        return this;
    }

    /**
     * Specifies the timestamp policy ID. Corresponds to the {@code -tsapolicyid} option.
     *
     * @param policyId The timestamp policy ID.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder timestampPolicyId(String policyId) {
        Objects.requireNonNull(policyId, "Policy ID cannot be null");
        this.arguments.add("-tsapolicyid " + policyId);
        return this;
    }

    /**
     * Specifies the digest algorithm for the timestamp. Corresponds to the {@code -tsadigestalg} option.
     *
     * @param algorithm The timestamp digest algorithm.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder timestampDigestAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Timestamp digest algorithm cannot be null");
        this.arguments.add("-tsadigestalg " + algorithm);
        return this;
    }

    /**
     * Includes the signature file in the signature block. Corresponds to the {@code -internalsf} option.
     *
     * @return This builder instance.
     */
    public JarsignerCLIBuilder includeSignatureFileInBlock() {
        this.arguments.add("-internalsf");
        return this;
    }

    /**
     * Only signs or verifies sections of the JAR file. Corresponds to the {@code -sectionsonly} option.
     *
     * @return This builder instance.
     */
    public JarsignerCLIBuilder sectionsOnly() {
        this.arguments.add("-sectionsonly");
        return this;
    }

    /**
     * Specifies whether protected authentication is required. Corresponds to the {@code -protected} option.
     *
     * @param required {@code true} if protected authentication is required, {@code false} otherwise.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder protectedAuthentication(boolean required) {
        this.arguments.add("-protected " + required);
        return this;
    }

    /**
     * Specifies the name of the cryptographic service provider. Corresponds to the {@code -providerName} option.
     *
     * @param providerName The name of the provider.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder providerName(String providerName) {
        Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.arguments.add("-providerName " + providerName);
        return this;
    }

    /**
     * Adds a cryptographic service provider by name. Corresponds to the {@code -addprovider} option.
     *
     * @param providerName The name of the provider to add.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder addProvider(String providerName) {
        Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.arguments.add("-addprovider " + providerName);
        return this;
    }

    /**
     * Adds a cryptographic service provider by name and an argument. Corresponds to the {@code -addprovider} and {@code -providerArg} options.
     *
     * @param providerName The name of the provider to add.
     * @param providerArg  An argument for the provider.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder addProvider(String providerName, String providerArg) {
        Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.arguments.add("-addprovider " + providerName);
        Objects.requireNonNull(providerArg, "Provider argument cannot be null");
        this.arguments.add("-providerArg " + providerArg);
        return this;
    }

    /**
     * Specifies the class name of the cryptographic service provider. Corresponds to the {@code -providerClass} option.
     *
     * @param className The class name of the provider.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder providerClass(String className) {
        Objects.requireNonNull(className, "Provider class cannot be null");
        this.arguments.add("-providerClass " + className);
        return this;
    }

    /**
     * Specifies the class name of the cryptographic service provider and an argument. Corresponds to the {@code -providerClass} and {@code -providerArg} options.
     *
     * @param className   The class name of the provider.
     * @param providerArg An argument for the provider.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder providerClass(String className, String providerArg) {
        Objects.requireNonNull(className, "Provider class cannot be null");
        this.arguments.add("-providerClass " + className);
        Objects.requireNonNull(providerArg, "Provider argument cannot be null");
        this.arguments.add("-providerArg " + providerArg);
        return this;
    }

    /**
     * Specifies the classpath for the provider. Corresponds to the {@code -providerPath} option.
     *
     * @param classpath The classpath for the provider.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder providerPath(String classpath) {
        Objects.requireNonNull(classpath, "Provider path cannot be null");
        this.arguments.add("-providerPath " + classpath);
        return this;
    }

    /**
     * Passes an option to the Java Virtual Machine. Corresponds to the {@code -J} option.
     *
     * @param option The option to pass to the JVM.
     * @return This builder instance.
     */
    public JarsignerCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    /**
     * Enables strict mode for verification. Corresponds to the {@code -strict} option.
     *
     * @return This builder instance.
     */
    public JarsignerCLIBuilder strictMode() {
        this.arguments.add("-strict");
        return this;
    }

    /**
     * Specifies a configuration file. Corresponds to the {@code -conf} option.
     *
     * @param url The URL of the configuration file.
     * @return This builder instance.
     */
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

    /**
     * Adds a password argument to the command.
     *
     * @param option The command-line option for the password (e.g., "-storepass", "-keypass").
     * @param source The source of the password (direct, environment variable, or file).
     * @param value  The password value, environment variable name, or file path.
     * @return This builder instance.
     */
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

    /**
     * Represents the level of detail for verbose output.
     */
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

    /**
     * Represents the source of a password argument.
     */
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

    /**
     * Represents the operation mode for the {@code jarsigner} command.
     */
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
