package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;
import lombok.Getter;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Builder for invoking the {@code keytool} command.
 */
public class KeytoolCLIBuilder implements CLIBuilder<Process, KeytoolCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "keytool.exe" : "keytool";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private Path workingDirectory;
    private final Map<String, String> environmentVariables = new HashMap<>();
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private KeytoolCommand selectedCommand;

    private KeytoolCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static KeytoolCLIBuilder create(JDK jdk) {
        return new KeytoolCLIBuilder(jdk);
    }

    @Override
    public KeytoolCLIBuilder addArgument(String arg) {
        this.arguments.add(Objects.requireNonNull(arg, "Argument cannot be null"));
        return this;
    }

    @Override
    public KeytoolCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = Objects.requireNonNull(path, "Working directory cannot be null");
        return this;
    }

    @Override
    public KeytoolCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment key cannot be null");
        Objects.requireNonNull(value, "Environment value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public KeytoolCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public KeytoolCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        this.timeoutDuration = duration;
        this.timeoutUnit = Objects.requireNonNull(unit, "TimeUnit cannot be null");
        return this;
    }

    /**
     * Sets the keytool command to run. Only a single command may be selected.
     */
    public KeytoolCLIBuilder command(KeytoolCommand command) {
        Objects.requireNonNull(command, "Command cannot be null");
        if (this.selectedCommand != null)
            throw new IllegalStateException("Only one keytool command can be specified per invocation.");

        this.arguments.add(command.getFlag());
        this.selectedCommand = command;
        return this;
    }

    public KeytoolCLIBuilder certreq() {
        return command(KeytoolCommand.CERTREQ);
    }

    public KeytoolCLIBuilder changeAlias() {
        return command(KeytoolCommand.CHANGE_ALIAS);
    }

    public KeytoolCLIBuilder deleteEntry() {
        return command(KeytoolCommand.DELETE);
    }

    public KeytoolCLIBuilder exportCertificate() {
        return command(KeytoolCommand.EXPORT_CERT);
    }

    public KeytoolCLIBuilder generateCertificate() {
        return command(KeytoolCommand.GEN_CERT);
    }

    public KeytoolCLIBuilder generateKeyPair() {
        return command(KeytoolCommand.GEN_KEYPAIR);
    }

    public KeytoolCLIBuilder generateSecretKey() {
        return command(KeytoolCommand.GEN_SECKEY);
    }

    public KeytoolCLIBuilder importCertificate() {
        return command(KeytoolCommand.IMPORT_CERT);
    }

    public KeytoolCLIBuilder importPassword() {
        return command(KeytoolCommand.IMPORT_PASS);
    }

    public KeytoolCLIBuilder importKeystore() {
        return command(KeytoolCommand.IMPORT_KEYSTORE);
    }

    public KeytoolCLIBuilder keyPasswordCommand() {
        return command(KeytoolCommand.KEY_PASSWD);
    }

    public KeytoolCLIBuilder listEntries() {
        return command(KeytoolCommand.LIST);
    }

    public KeytoolCLIBuilder printCertificate() {
        return command(KeytoolCommand.PRINT_CERT);
    }

    public KeytoolCLIBuilder printCertificateRequest() {
        return command(KeytoolCommand.PRINT_CERT_REQ);
    }

    public KeytoolCLIBuilder printCRL() {
        return command(KeytoolCommand.PRINT_CRL);
    }

    public KeytoolCLIBuilder storePasswordCommand() {
        return command(KeytoolCommand.STORE_PASSWD);
    }

    public KeytoolCLIBuilder showInfo() {
        return command(KeytoolCommand.SHOW_INFO);
    }

    public KeytoolCLIBuilder version() {
        return command(KeytoolCommand.VERSION);
    }

    public KeytoolCLIBuilder help() {
        this.arguments.add("-help");
        return this;
    }

    public KeytoolCLIBuilder rfcOutput() {
        this.arguments.add("-rfc");
        return this;
    }

    public KeytoolCLIBuilder verbose() {
        this.arguments.add("-v");
        return this;
    }

    public KeytoolCLIBuilder protectedMode() {
        this.arguments.add("-protected");
        return this;
    }

    public KeytoolCLIBuilder trustCaCerts() {
        this.arguments.add("-trustcacerts");
        return this;
    }

    public KeytoolCLIBuilder useDefaultCacerts() {
        this.arguments.add("-cacerts");
        return this;
    }

    public KeytoolCLIBuilder includeCertChain() {
        this.arguments.add("-certs");
        return this;
    }

    public KeytoolCLIBuilder noPrompt() {
        this.arguments.add("-noprompt");
        return this;
    }

    public KeytoolCLIBuilder outputToStdout() {
        this.arguments.add("-stdout");
        return this;
    }

    public KeytoolCLIBuilder tlsInfo(String protocols) {
        Objects.requireNonNull(protocols, "Protocols cannot be null");
        this.arguments.add("-tls " + protocols);
        return this;
    }

    public KeytoolCLIBuilder sslServer(String server) {
        Objects.requireNonNull(server, "Server cannot be null");
        this.arguments.add("-sslserver " + server);
        return this;
    }

    public KeytoolCLIBuilder alias(String alias) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.arguments.add("-alias " + alias);
        return this;
    }

    public KeytoolCLIBuilder destAlias(String alias) {
        Objects.requireNonNull(alias, "Destination alias cannot be null");
        this.arguments.add("-destalias " + alias);
        return this;
    }

    public KeytoolCLIBuilder srcAlias(String alias) {
        Objects.requireNonNull(alias, "Source alias cannot be null");
        this.arguments.add("-srcalias " + alias);
        return this;
    }

    public KeytoolCLIBuilder dname(String distinguishedName) {
        Objects.requireNonNull(distinguishedName, "Distinguished name cannot be null");
        this.arguments.add("-dname " + distinguishedName);
        return this;
    }

    public KeytoolCLIBuilder keyAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Key algorithm cannot be null");
        this.arguments.add("-keyalg " + algorithm);
        return this;
    }

    public KeytoolCLIBuilder keySize(int size) {
        if (size <= 0)
            throw new IllegalArgumentException("Key size must be positive");
        this.arguments.add("-keysize " + size);
        return this;
    }

    public KeytoolCLIBuilder signatureAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Signature algorithm cannot be null");
        this.arguments.add("-sigalg " + algorithm);
        return this;
    }

    public KeytoolCLIBuilder validityDays(int days) {
        if (days <= 0)
            throw new IllegalArgumentException("Validity days must be positive");
        this.arguments.add("-validity " + days);
        return this;
    }

    public KeytoolCLIBuilder startDate(String date) {
        Objects.requireNonNull(date, "Start date cannot be null");
        this.arguments.add("-startdate " + date);
        return this;
    }

    public KeytoolCLIBuilder groupName(String group) {
        Objects.requireNonNull(group, "Group name cannot be null");
        this.arguments.add("-groupname " + group);
        return this;
    }

    public KeytoolCLIBuilder keyPass(String password) {
        Objects.requireNonNull(password, "Key password cannot be null");
        this.arguments.add("-keypass " + password);
        return this;
    }

    public KeytoolCLIBuilder newPassword(String password) {
        Objects.requireNonNull(password, "New password cannot be null");
        this.arguments.add("-new " + password);
        return this;
    }

    public KeytoolCLIBuilder signer(String signerAlias) {
        Objects.requireNonNull(signerAlias, "Signer alias cannot be null");
        this.arguments.add("-signer " + signerAlias);
        return this;
    }

    public KeytoolCLIBuilder signerKeyPass(String password) {
        Objects.requireNonNull(password, "Signer key password cannot be null");
        this.arguments.add("-signerkeypass " + password);
        return this;
    }

    public KeytoolCLIBuilder keystore(String keystore) {
        Objects.requireNonNull(keystore, "Keystore cannot be null");
        this.arguments.add("-keystore " + keystore);
        return this;
    }

    public KeytoolCLIBuilder keystore(Path keystorePath) {
        Objects.requireNonNull(keystorePath, "Keystore path cannot be null");
        return keystore(keystorePath.toString());
    }

    public KeytoolCLIBuilder storePass(String password) {
        Objects.requireNonNull(password, "Store password cannot be null");
        this.arguments.add("-storepass " + password);
        return this;
    }

    public KeytoolCLIBuilder storeType(String type) {
        Objects.requireNonNull(type, "Store type cannot be null");
        this.arguments.add("-storetype " + type);
        return this;
    }

    public KeytoolCLIBuilder destKeystore(String path) {
        Objects.requireNonNull(path, "Destination keystore cannot be null");
        this.arguments.add("-destkeystore " + path);
        return this;
    }

    public KeytoolCLIBuilder destKeystore(Path path) {
        Objects.requireNonNull(path, "Destination keystore path cannot be null");
        return destKeystore(path.toString());
    }

    public KeytoolCLIBuilder destStorePass(String password) {
        Objects.requireNonNull(password, "Destination store password cannot be null");
        this.arguments.add("-deststorepass " + password);
        return this;
    }

    public KeytoolCLIBuilder destStoreType(String type) {
        Objects.requireNonNull(type, "Destination store type cannot be null");
        this.arguments.add("-deststoretype " + type);
        return this;
    }

    public KeytoolCLIBuilder destKeyPass(String password) {
        Objects.requireNonNull(password, "Destination key password cannot be null");
        this.arguments.add("-destkeypass " + password);
        return this;
    }

    public KeytoolCLIBuilder destProviderName(String providerName) {
        Objects.requireNonNull(providerName, "Destination provider name cannot be null");
        this.arguments.add("-destprovidername " + providerName);
        return this;
    }

    public KeytoolCLIBuilder destProtected() {
        this.arguments.add("-destprotected");
        return this;
    }

    public KeytoolCLIBuilder srcKeystore(String path) {
        Objects.requireNonNull(path, "Source keystore cannot be null");
        this.arguments.add("-srckeystore " + path);
        return this;
    }

    public KeytoolCLIBuilder srcKeystore(Path path) {
        Objects.requireNonNull(path, "Source keystore path cannot be null");
        return srcKeystore(path.toString());
    }

    public KeytoolCLIBuilder srcStorePass(String password) {
        Objects.requireNonNull(password, "Source store password cannot be null");
        this.arguments.add("-srcstorepass " + password);
        return this;
    }

    public KeytoolCLIBuilder srcStoreType(String type) {
        Objects.requireNonNull(type, "Source store type cannot be null");
        this.arguments.add("-srcstoretype " + type);
        return this;
    }

    public KeytoolCLIBuilder srcProviderName(String providerName) {
        Objects.requireNonNull(providerName, "Source provider name cannot be null");
        this.arguments.add("-srcprovidername " + providerName);
        return this;
    }

    public KeytoolCLIBuilder srcKeyPass(String password) {
        Objects.requireNonNull(password, "Source key password cannot be null");
        this.arguments.add("-srckeypass " + password);
        return this;
    }

    public KeytoolCLIBuilder srcProtected() {
        this.arguments.add("-srcprotected");
        return this;
    }

    public KeytoolCLIBuilder providerName(String name) {
        Objects.requireNonNull(name, "Provider name cannot be null");
        this.arguments.add("-providername " + name);
        return this;
    }

    public KeytoolCLIBuilder providerClass(String className) {
        Objects.requireNonNull(className, "Provider class cannot be null");
        this.arguments.add("-providerclass " + className);
        return this;
    }

    public KeytoolCLIBuilder providerArg(String arg) {
        Objects.requireNonNull(arg, "Provider argument cannot be null");
        this.arguments.add("-providerarg " + arg);
        return this;
    }

    public KeytoolCLIBuilder providerPath(String... paths) {
        Objects.requireNonNull(paths, "Provider paths cannot be null");
        String joined = String.join(File.pathSeparator, Arrays.asList(paths));
        this.arguments.add("-providerpath " + joined);
        return this;
    }

    public KeytoolCLIBuilder addProvider(String providerName) {
        Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.arguments.add("-addprovider " + providerName);
        return this;
    }

    public KeytoolCLIBuilder inFile(String file) {
        Objects.requireNonNull(file, "Input file cannot be null");
        this.arguments.add("-infile " + file);
        return this;
    }

    public KeytoolCLIBuilder inFile(Path file) {
        Objects.requireNonNull(file, "Input file path cannot be null");
        return inFile(file.toString());
    }

    public KeytoolCLIBuilder outFile(String file) {
        Objects.requireNonNull(file, "Output file cannot be null");
        this.arguments.add("-outfile " + file);
        return this;
    }

    public KeytoolCLIBuilder outFile(Path file) {
        Objects.requireNonNull(file, "Output file path cannot be null");
        return outFile(file.toString());
    }

    public KeytoolCLIBuilder file(String file) {
        Objects.requireNonNull(file, "File cannot be null");
        this.arguments.add("-file " + file);
        return this;
    }

    public KeytoolCLIBuilder file(Path file) {
        Objects.requireNonNull(file, "File path cannot be null");
        return file(file.toString());
    }

    public KeytoolCLIBuilder jarFile(String file) {
        Objects.requireNonNull(file, "JAR file cannot be null");
        this.arguments.add("-jarfile " + file);
        return this;
    }

    public KeytoolCLIBuilder jarFile(Path file) {
        Objects.requireNonNull(file, "JAR file path cannot be null");
        return jarFile(file.toString());
    }

    public KeytoolCLIBuilder confFile(String conf) {
        Objects.requireNonNull(conf, "Configuration file cannot be null");
        this.arguments.add("-conf " + conf);
        return this;
    }

    public KeytoolCLIBuilder confFile(Path conf) {
        Objects.requireNonNull(conf, "Configuration path cannot be null");
        return confFile(conf.toString());
    }

    public KeytoolCLIBuilder extension(String extension) {
        Objects.requireNonNull(extension, "Extension cannot be null");
        this.arguments.add("-ext " + extension);
        return this;
    }

    public KeytoolCLIBuilder extensions(String... extensions) {
        Objects.requireNonNull(extensions, "Extensions cannot be null");
        for (String extension : extensions) {
            extension(extension);
        }
        return this;
    }

    public KeytoolCLIBuilder includeArgumentFile(Path argFile) {
        Objects.requireNonNull(argFile, "Argument file cannot be null");
        this.arguments.add("@" + argFile);
        return this;
    }

    public KeytoolCLIBuilder jvmOption(String option) {
        Objects.requireNonNull(option, "JVM option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);

        ProcessBuilder processBuilder = new ProcessBuilder().command(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        if (useSystemEnvVars) {
            processBuilder.environment().putAll(environmentVariables);
        } else {
            processBuilder.environment().clear();
            processBuilder.environment().putAll(environmentVariables);
        }

        try {
            Process process = processBuilder.start();
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "keytool");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start keytool process", exception);
        }
    }

    @Getter
    public enum KeytoolCommand {
        CERTREQ("-certreq"),
        CHANGE_ALIAS("-changealias"),
        DELETE("-delete"),
        EXPORT_CERT("-exportcert"),
        GEN_CERT("-gencert"),
        GEN_KEYPAIR("-genkeypair"),
        GEN_SECKEY("-genseckey"),
        IMPORT_CERT("-importcert"),
        IMPORT_PASS("-importpass"),
        IMPORT_KEYSTORE("-importkeystore"),
        KEY_PASSWD("-keypasswd"),
        LIST("-list"),
        PRINT_CERT("-printcert"),
        PRINT_CERT_REQ("-printcertreq"),
        PRINT_CRL("-printcrl"),
        STORE_PASSWD("-storepasswd"),
        SHOW_INFO("-showinfo"),
        VERSION("-version");

        private final String flag;

        KeytoolCommand(String flag) {
            this.flag = flag;
        }
    }
}
