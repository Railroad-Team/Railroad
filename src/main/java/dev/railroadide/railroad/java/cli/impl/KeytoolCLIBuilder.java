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
 * Builder that constructs {@code keytool} invocations used to create and manage keystores,
 * certificates, and related trust material.
 *
 * <p>Each helper method adds the appropriate flag to the argument list and validates
 * required data before execution.</p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html">keytool command documentation</a>
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

    /**
     * Creates a builder that executes {@code keytool} from the provided {@link JDK}.
     *
     * @param jdk the JDK to use when locating the executable; must not be null
     * @return a new {@code KeytoolCLIBuilder} configured for the given JDK
     */
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
     *
     * @param command the command to run; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder command(KeytoolCommand command) {
        Objects.requireNonNull(command, "Command cannot be null");
        if (this.selectedCommand != null)
            throw new IllegalStateException("Only one keytool command can be specified per invocation.");

        this.arguments.add(command.getFlag());
        this.selectedCommand = command;
        return this;
    }

    /**
     * Selects {@code -certreq} to generate a certificate signing request.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder certreq() {
        return command(KeytoolCommand.CERTREQ);
    }

    /**
     * Selects {@code -changealias} to change a certificate alias in a keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder changeAlias() {
        return command(KeytoolCommand.CHANGE_ALIAS);
    }

    /**
     * Selects {@code -delete} to remove an entry from a keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder deleteEntry() {
        return command(KeytoolCommand.DELETE);
    }

    /**
     * Selects {@code -exportcert} to export a certificate from the keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder exportCertificate() {
        return command(KeytoolCommand.EXPORT_CERT);
    }

    /**
     * Selects {@code -gencert} to generate a certificate using an existing certificate authority.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder generateCertificate() {
        return command(KeytoolCommand.GEN_CERT);
    }

    /**
     * Selects {@code -genkeypair} to generate a new key pair and self-signed certificate.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder generateKeyPair() {
        return command(KeytoolCommand.GEN_KEYPAIR);
    }

    /**
     * Selects {@code -genseckey} to generate a secret key entry in the keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder generateSecretKey() {
        return command(KeytoolCommand.GEN_SECKEY);
    }

    /**
     * Selects {@code -importcert} to import a certificate into the keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder importCertificate() {
        return command(KeytoolCommand.IMPORT_CERT);
    }

    /**
     * Selects {@code -importpass} to import a password entry from another keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder importPassword() {
        return command(KeytoolCommand.IMPORT_PASS);
    }

    /**
     * Selects {@code -importkeystore} to import entries from a different keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder importKeystore() {
        return command(KeytoolCommand.IMPORT_KEYSTORE);
    }

    /**
     * Selects {@code -keypasswd} to change the password of a key pair.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder keyPasswordCommand() {
        return command(KeytoolCommand.KEY_PASSWD);
    }

    /**
     * Selects {@code -list} to list the entries contained in the keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder listEntries() {
        return command(KeytoolCommand.LIST);
    }

    /**
     * Selects {@code -printcert} to display the contents of a certificate.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder printCertificate() {
        return command(KeytoolCommand.PRINT_CERT);
    }

    /**
     * Selects {@code -printcertreq} to display a certificate request.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder printCertificateRequest() {
        return command(KeytoolCommand.PRINT_CERT_REQ);
    }

    /**
     * Selects {@code -printcrl} to display a certificate revocation list.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder printCRL() {
        return command(KeytoolCommand.PRINT_CRL);
    }

    /**
     * Selects {@code -storepasswd} to change a keystore password.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder storePasswordCommand() {
        return command(KeytoolCommand.STORE_PASSWD);
    }

    /**
     * Selects {@code -showinfo} to display keystore metadata and provider information.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder showInfo() {
        return command(KeytoolCommand.SHOW_INFO);
    }

    /**
     * Selects {@code -version} to print the tool's version.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder version() {
        return command(KeytoolCommand.VERSION);
    }

    /**
     * Adds {@code -help} to print usage for the selected command.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder help() {
        this.arguments.add("-help");
        return this;
    }

    /**
     * Adds {@code -rfc} to format certificate output in RFC style.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder rfcOutput() {
        this.arguments.add("-rfc");
        return this;
    }

    /**
     * Adds {@code -v} to enable verbose logging.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder verbose() {
        this.arguments.add("-v");
        return this;
    }

    /**
     * Adds {@code -protected} to run operations in protected mode.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder protectedMode() {
        this.arguments.add("-protected");
        return this;
    }

    /**
     * Adds {@code -trustcacerts} to trust certificates from the default trust store.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder trustCaCerts() {
        this.arguments.add("-trustcacerts");
        return this;
    }

    /**
     * Adds {@code -cacerts} to operate on the default cacerts keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder useDefaultCacerts() {
        this.arguments.add("-cacerts");
        return this;
    }

    /**
     * Adds {@code -certs} to include the certificate chain in exports.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder includeCertChain() {
        this.arguments.add("-certs");
        return this;
    }

    /**
     * Adds {@code -noprompt} to skip confirmation prompts.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder noPrompt() {
        this.arguments.add("-noprompt");
        return this;
    }

    /**
     * Adds {@code -stdout} to route certificate output to {@code stdout}.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder outputToStdout() {
        this.arguments.add("-stdout");
        return this;
    }

    /**
     * Adds {@code -tls} with the provided protocols to request TLS information.
     *
     * @param protocols the TLS protocols to enable; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder tlsInfo(String protocols) {
        Objects.requireNonNull(protocols, "Protocols cannot be null");
        this.arguments.add("-tls " + protocols);
        return this;
    }

    /**
     * Adds {@code -sslserver} to contact a specific SSL server.
     *
     * @param server the SSL server; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder sslServer(String server) {
        Objects.requireNonNull(server, "Server cannot be null");
        this.arguments.add("-sslserver " + server);
        return this;
    }

    /**
     * Adds {@code -alias} to specify the alias of the entry to work with.
     *
     * @param alias the alias; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder alias(String alias) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        this.arguments.add("-alias " + alias);
        return this;
    }

    /**
     * Adds {@code -destalias} for the destination alias when copying or moving entries.
     *
     * @param alias the destination alias; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder destAlias(String alias) {
        Objects.requireNonNull(alias, "Destination alias cannot be null");
        this.arguments.add("-destalias " + alias);
        return this;
    }

    /**
     * Adds {@code -srcalias} for the source alias when copying or converting entries.
     *
     * @param alias the source alias; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder srcAlias(String alias) {
        Objects.requireNonNull(alias, "Source alias cannot be null");
        this.arguments.add("-srcalias " + alias);
        return this;
    }

    /**
     * Adds {@code -dname} to define the distinguished name for certificate creation.
     *
     * @param distinguishedName the distinguished name; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder dname(String distinguishedName) {
        Objects.requireNonNull(distinguishedName, "Distinguished name cannot be null");
        this.arguments.add("-dname " + distinguishedName);
        return this;
    }

    /**
     * Adds {@code -keyalg} to specify the key algorithm.
     *
     * @param algorithm the key algorithm; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder keyAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Key algorithm cannot be null");
        this.arguments.add("-keyalg " + algorithm);
        return this;
    }

    /**
     * Adds {@code -keysize} to request a specific key size.
     *
     * @param size the key size; must be positive
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder keySize(int size) {
        if (size <= 0)
            throw new IllegalArgumentException("Key size must be positive");
        this.arguments.add("-keysize " + size);
        return this;
    }

    /**
     * Adds {@code -sigalg} to specify the signature algorithm.
     *
     * @param algorithm the signature algorithm; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder signatureAlgorithm(String algorithm) {
        Objects.requireNonNull(algorithm, "Signature algorithm cannot be null");
        this.arguments.add("-sigalg " + algorithm);
        return this;
    }

    /**
     * Adds {@code -validity} to define the validity duration of a certificate.
     *
     * @param days the number of days; must be positive
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder validityDays(int days) {
        if (days <= 0)
            throw new IllegalArgumentException("Validity days must be positive");
        this.arguments.add("-validity " + days);
        return this;
    }

    /**
     * Adds {@code -startdate} to specify when the certificate becomes valid.
     *
     * @param date the start date; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder startDate(String date) {
        Objects.requireNonNull(date, "Start date cannot be null");
        this.arguments.add("-startdate " + date);
        return this;
    }

    /**
     * Adds {@code -groupname} to specify a provider group name.
     *
     * @param group the group name; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder groupName(String group) {
        Objects.requireNonNull(group, "Group name cannot be null");
        this.arguments.add("-groupname " + group);
        return this;
    }

    /**
     * Adds {@code -keypass} to supply the password for a key entry.
     *
     * @param password the key password; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder keyPass(String password) {
        Objects.requireNonNull(password, "Key password cannot be null");
        this.arguments.add("-keypass " + password);
        return this;
    }

    /**
     * Adds {@code -new} to specify a new password.
     *
     * @param password the new password; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder newPassword(String password) {
        Objects.requireNonNull(password, "New password cannot be null");
        this.arguments.add("-new " + password);
        return this;
    }

    /**
     * Adds {@code -signer} to specify the alias of the signing certificate.
     *
     * @param signerAlias the signer alias; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder signer(String signerAlias) {
        Objects.requireNonNull(signerAlias, "Signer alias cannot be null");
        this.arguments.add("-signer " + signerAlias);
        return this;
    }

    /**
     * Adds {@code -signerkeypass} to supply the password for the signer.
     *
     * @param password the signer's key password; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder signerKeyPass(String password) {
        Objects.requireNonNull(password, "Signer key password cannot be null");
        this.arguments.add("-signerkeypass " + password);
        return this;
    }

    /**
     * Adds {@code -keystore} to specify the keystore path.
     *
     * @param keystore the keystore path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder keystore(String keystore) {
        Objects.requireNonNull(keystore, "Keystore cannot be null");
        this.arguments.add("-keystore " + keystore);
        return this;
    }

    /**
     * Adds {@code -keystore} using a {@link Path}.
     *
     * @param keystorePath the keystore path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder keystore(Path keystorePath) {
        Objects.requireNonNull(keystorePath, "Keystore path cannot be null");
        return keystore(keystorePath.toString());
    }

    /**
     * Adds {@code -storepass} to set the keystore password.
     *
     * @param password the keystore password; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder storePass(String password) {
        Objects.requireNonNull(password, "Store password cannot be null");
        this.arguments.add("-storepass " + password);
        return this;
    }

    /**
     * Adds {@code -storetype} to define the keystore type.
     *
     * @param type the keystore type; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder storeType(String type) {
        Objects.requireNonNull(type, "Store type cannot be null");
        this.arguments.add("-storetype " + type);
        return this;
    }

    /**
     * Adds {@code -destkeystore} to specify the destination keystore path.
     *
     * @param path the destination keystore; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder destKeystore(String path) {
        Objects.requireNonNull(path, "Destination keystore cannot be null");
        this.arguments.add("-destkeystore " + path);
        return this;
    }

    /**
     * Adds {@code -destkeystore} using a {@link Path}.
     *
     * @param path the destination keystore path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder destKeystore(Path path) {
        Objects.requireNonNull(path, "Destination keystore path cannot be null");
        return destKeystore(path.toString());
    }

    /**
     * Adds {@code -deststorepass} for the destination keystore password.
     *
     * @param password the destination store password; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder destStorePass(String password) {
        Objects.requireNonNull(password, "Destination store password cannot be null");
        this.arguments.add("-deststorepass " + password);
        return this;
    }

    /**
     * Adds {@code -deststoretype} to override the destination keystore type.
     *
     * @param type the destination store type; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder destStoreType(String type) {
        Objects.requireNonNull(type, "Destination store type cannot be null");
        this.arguments.add("-deststoretype " + type);
        return this;
    }

    /**
     * Adds {@code -destkeypass} to supply the password for the destination key entry.
     *
     * @param password the destination key password; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder destKeyPass(String password) {
        Objects.requireNonNull(password, "Destination key password cannot be null");
        this.arguments.add("-destkeypass " + password);
        return this;
    }

    /**
     * Adds {@code -destprovidername} for third-party providers when writing to the destination.
     *
     * @param providerName the provider name; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder destProviderName(String providerName) {
        Objects.requireNonNull(providerName, "Destination provider name cannot be null");
        this.arguments.add("-destprovidername " + providerName);
        return this;
    }

    /**
     * Adds {@code -destprotected} to request protected mode on the destination keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder destProtected() {
        this.arguments.add("-destprotected");
        return this;
    }

    /**
     * Adds {@code -srckeystore} for the source keystore path.
     *
     * @param path the source keystore; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder srcKeystore(String path) {
        Objects.requireNonNull(path, "Source keystore cannot be null");
        this.arguments.add("-srckeystore " + path);
        return this;
    }

    /**
     * Adds {@code -srckeystore} using a {@link Path}.
     *
     * @param path the source keystore path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder srcKeystore(Path path) {
        Objects.requireNonNull(path, "Source keystore path cannot be null");
        return srcKeystore(path.toString());
    }

    /**
     * Adds {@code -srcstorepass} to supply the source keystore password.
     *
     * @param password the source store password; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder srcStorePass(String password) {
        Objects.requireNonNull(password, "Source store password cannot be null");
        this.arguments.add("-srcstorepass " + password);
        return this;
    }

    /**
     * Adds {@code -srcstoretype} to override the source keystore type.
     *
     * @param type the source store type; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder srcStoreType(String type) {
        Objects.requireNonNull(type, "Source store type cannot be null");
        this.arguments.add("-srcstoretype " + type);
        return this;
    }

    /**
     * Adds {@code -srcprovidername} for custom source providers.
     *
     * @param providerName the source provider name; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder srcProviderName(String providerName) {
        Objects.requireNonNull(providerName, "Source provider name cannot be null");
        this.arguments.add("-srcprovidername " + providerName);
        return this;
    }

    /**
     * Adds {@code -srckeypass} to provide the source key password.
     *
     * @param password the source key password; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder srcKeyPass(String password) {
        Objects.requireNonNull(password, "Source key password cannot be null");
        this.arguments.add("-srckeypass " + password);
        return this;
    }

    /**
     * Adds {@code -srcprotected} to request protected mode on the source keystore.
     *
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder srcProtected() {
        this.arguments.add("-srcprotected");
        return this;
    }

    /**
     * Adds {@code -providername} to specify the provider to load.
     *
     * @param name the provider name; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder providerName(String name) {
        Objects.requireNonNull(name, "Provider name cannot be null");
        this.arguments.add("-providername " + name);
        return this;
    }

    /**
     * Adds {@code -providerclass} to specify a provider implementation class.
     *
     * @param className the provider class name; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder providerClass(String className) {
        Objects.requireNonNull(className, "Provider class cannot be null");
        this.arguments.add("-providerclass " + className);
        return this;
    }

    /**
     * Adds {@code -providerarg} to pass an argument to the provider.
     *
     * @param arg the provider argument; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder providerArg(String arg) {
        Objects.requireNonNull(arg, "Provider argument cannot be null");
        this.arguments.add("-providerarg " + arg);
        return this;
    }

    /**
     * Adds {@code -providerpath} with one or more paths where providers are located.
     *
     * @param paths the provider paths; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder providerPath(String... paths) {
        Objects.requireNonNull(paths, "Provider paths cannot be null");
        String joined = String.join(File.pathSeparator, Arrays.asList(paths));
        this.arguments.add("-providerpath " + joined);
        return this;
    }

    /**
     * Adds {@code -addprovider} to install a provider into the keystore.
     *
     * @param providerName the provider name; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder addProvider(String providerName) {
        Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.arguments.add("-addprovider " + providerName);
        return this;
    }

    /**
     * Adds {@code -infile} to specify the input file.
     *
     * @param file the input file; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder inFile(String file) {
        Objects.requireNonNull(file, "Input file cannot be null");
        this.arguments.add("-infile " + file);
        return this;
    }

    /**
     * Adds {@code -infile} using a {@link Path}.
     *
     * @param file the input file path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder inFile(Path file) {
        Objects.requireNonNull(file, "Input file path cannot be null");
        return inFile(file.toString());
    }

    /**
     * Adds {@code -outfile} to specify the output file.
     *
     * @param file the output file; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder outFile(String file) {
        Objects.requireNonNull(file, "Output file cannot be null");
        this.arguments.add("-outfile " + file);
        return this;
    }

    /**
     * Adds {@code -outfile} using a {@link Path}.
     *
     * @param file the output file path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder outFile(Path file) {
        Objects.requireNonNull(file, "Output file path cannot be null");
        return outFile(file.toString());
    }

    /**
     * Adds {@code -file} to supply a general file argument.
     *
     * @param file the file path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder file(String file) {
        Objects.requireNonNull(file, "File cannot be null");
        this.arguments.add("-file " + file);
        return this;
    }

    /**
     * Adds {@code -file} using a {@link Path}.
     *
     * @param file the file path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder file(Path file) {
        Objects.requireNonNull(file, "File path cannot be null");
        return file(file.toString());
    }

    /**
     * Adds {@code -jarfile} to provide the signing JAR file.
     *
     * @param file the jar file path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder jarFile(String file) {
        Objects.requireNonNull(file, "JAR file cannot be null");
        this.arguments.add("-jarfile " + file);
        return this;
    }

    /**
     * Adds {@code -jarfile} using a {@link Path}.
     *
     * @param file the jar file path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder jarFile(Path file) {
        Objects.requireNonNull(file, "JAR file path cannot be null");
        return jarFile(file.toString());
    }

    /**
     * Adds {@code -conf} to specify the configuration file.
     *
     * @param conf the configuration file; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder confFile(String conf) {
        Objects.requireNonNull(conf, "Configuration file cannot be null");
        this.arguments.add("-conf " + conf);
        return this;
    }

    /**
     * Adds {@code -conf} using a {@link Path}.
     *
     * @param conf the configuration path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder confFile(Path conf) {
        Objects.requireNonNull(conf, "Configuration path cannot be null");
        return confFile(conf.toString());
    }

    /**
     * Adds {@code -ext} to provide an extension definition.
     *
     * @param extension the extension definition; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder extension(String extension) {
        Objects.requireNonNull(extension, "Extension cannot be null");
        this.arguments.add("-ext " + extension);
        return this;
    }

    /**
     * Adds multiple {@code -ext} definitions.
     *
     * @param extensions the extensions to add; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder extensions(String... extensions) {
        Objects.requireNonNull(extensions, "Extensions cannot be null");
        for (String extension : extensions) {
            extension(extension);
        }
        return this;
    }

    /**
     * Adds an argument file reference via {@code @file}.
     *
     * @param argFile the argument file path; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
    public KeytoolCLIBuilder includeArgumentFile(Path argFile) {
        Objects.requireNonNull(argFile, "Argument file cannot be null");
        this.arguments.add("@" + argFile);
        return this;
    }

    /**
     * Adds a JVM option prefixed with {@code -J}.
     *
     * @param option the JVM option; must not be null
     * @return the current {@code KeytoolCLIBuilder}
     */
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

    /**
     * Represents the primary {@code keytool} commands that can be executed.
     */
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
