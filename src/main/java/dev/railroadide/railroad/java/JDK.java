package dev.railroadide.railroad.java;

import dev.railroadide.railroad.java.cli.JDKCLI;
import dev.railroadide.railroad.utility.JavaVersion;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * A descriptor for a Java Development Kit (JDK) installation.
 * This class encapsulates information about a JDK, including its installation path, name,
 * Java version, and brand (e.g., Oracle, Adoptium). It also provides utility methods
 * for interacting with the JDK's command-line interface tools.
 */
@ToString
@EqualsAndHashCode
public final class JDK {
    private final Path path;
    private final String name;
    private final JavaVersion version;
    private final Brand brand;
    private final JDKCLI cli;

    /**
     * Constructs a new {@code JDK} instance with the specified path, name, version, and brand.
     *
     * @param path The absolute path to the JDK installation directory.
     * @param name The display name of the JDK.
     * @param version The Java version of this JDK.
     * @param brand The brand of this JDK (e.g., Oracle, Adoptium).
     */
    public JDK(Path path, String name, JavaVersion version, Brand brand) {
        this.path = path;
        this.name = name;
        this.version = version;
        this.brand = brand;
        this.cli = new JDKCLI(this);
    }

    /**
     * Constructs a new {@code JDK} instance with the specified path, name, and version.
     * The brand will be automatically determined based on the JDK's properties.
     *
     * @param path The absolute path to the JDK installation directory.
     * @param name The display name of the JDK.
     * @param version The Java version of this JDK.
     */
    public JDK(Path path, String name, JavaVersion version) {
        this.path = path;
        this.name = name;
        this.version = version;
        this.brand = Brand.from(this);
        this.cli = new JDKCLI(this);
    }

    /**
     * Returns the absolute path to the JDK installation directory.
     * @return The path to the JDK.
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the display name of this JDK.
     * @return The name of the JDK.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the Java version of this JDK.
     * @return The Java version.
     */
    public JavaVersion version() {
        return version;
    }

    /**
     * Returns the brand of this JDK.
     * @return The JDK brand.
     */
    public Brand brand() {
        return brand;
    }

    /**
     * Returns the command-line interface (CLI) utility for this JDK.
     * @return The JDKCLI instance.
     */
    public JDKCLI cli() {
        return cli;
    }

    /**
     * Resolves the absolute path to a tool executable within this JDK installation.
     *
     * @param executableName filename of the CLI tool (e.g. {@code jar} or {@code keytool})
     * @return absolute path to the executable, best-effort even when this JDK descriptor points directly to {@code bin}
     */
    public Path executablePath(String executableName) {
        Objects.requireNonNull(executableName, "Executable name cannot be null");
        Path binDirectory = path.resolve("bin");
        Path candidate = binDirectory.resolve(executableName);
        if (Files.exists(candidate))
            return candidate;

        return path.resolve(executableName);
    }

    /**
     * Represents the brand or vendor of a JDK distribution.
     */
    public enum Brand {
        ORACLE("oracle", "/images/Oracle-icon.svg"),
        ADOPTIUM("temurin", "/images/Adoptium-icon.svg", "adoptopenjdk", "adoptium", "eclipse"),
        AZUL("zulu", "/images/Azul-icon.svg", "azul"),
        CORRETTO("corretto", FontAwesomeBrands.AMAZON, "amazon"),
        BELL_SOFT("liberica", "/images/Liberica-icon.svg", "bellsoft"),
        GRAAL("graalvm", "/images/GraalVM-icon.svg", "graal"),
        SAP("sapmachine", "/images/SAP-icon.svg", "sap"),
        RED_HAT("redhat", FontAwesomeBrands.REDHAT, "red hat", "rhel"),
        MICROSOFT("microsoft", FontAwesomeBrands.MICROSOFT, "ms"),
        IBM("ibm", "/images/IBM-icon.svg", "semeru"),
        UNKNOWN("java", FontAwesomeBrands.JAVA);

        private final String key;
        private final String[] aliases;
        @Getter
        private Ikon icon;
        @Getter
        private String imagePath;

        /**
         * Constructs a Brand with a key, Ikon, and optional aliases.
         * @param key The primary identifier for the brand.
         * @param icon The Ikon associated with the brand.
         * @param aliases Additional names or identifiers for the brand.
         */
        Brand(String key, Ikon icon, String... aliases) {
            this.key = key;
            this.icon = icon;
            this.aliases = aliases;
        }

        /**
         * Constructs a Brand with a key, image path, and optional aliases.
         * @param key The primary identifier for the brand.
         * @param imagePath The path to the image icon associated with the brand.
         * @param aliases Additional names or identifiers for the brand.
         */
        Brand(String key, String imagePath, String... aliases) {
            this.key = key;
            this.imagePath = imagePath;
            this.aliases = aliases;
        }

        /**
         * Constructs a Brand with a key and an Ikon, with no aliases.
         * @param key The primary identifier for the brand.
         * @param icon The Ikon associated with the brand.
         */
        Brand(String key, Ikon icon) {
            this(key, icon, (String[]) null);
        }

        /**
         * Constructs a Brand with a key and an image path, with no aliases.
         * @param key The primary identifier for the brand.
         * @param imagePath The path to the image icon associated with the brand.
         */
        Brand(String key, String imagePath) {
            this(key, imagePath, (String[]) null);
        }

        /**
         * Checks if this brand has an associated Ikon.
         * @return {@code true} if an Ikon is present, {@code false} otherwise.
         */
        public boolean isIkon() {
            return icon != null;
        }

        /**
         * Checks if this brand has an associated image path.
         * @return {@code true} if an image path is present, {@code false} otherwise.
         */
        public boolean isImage() {
            return imagePath != null;
        }

        /**
         * Determines the {@code Brand} of a given JDK based on its properties and name.
         * It first attempts to resolve the brand from the JDK's release properties (IMPLEMENTOR, VENDOR),
         * then falls back to string matching on the JDK's name and path if properties are inconclusive.
         *
         * @param jdk The {@code JDK} instance to determine the brand for.
         * @return The resolved {@code Brand}, or {@code UNKNOWN} if no specific brand can be identified.
         */
        private static Brand from(JDK jdk) {
            String rawName = jdk.name();
            String path = jdk.path().toAbsolutePath().toString().toLowerCase(Locale.ROOT);
            String name = (rawName == null ? "" : rawName).toLowerCase(Locale.ROOT);
            Properties props = JDKUtils.readReleaseProperties(jdk.path());
            String implVendor = props.getProperty("IMPLEMENTOR", "").toLowerCase(Locale.ROOT);
            String vendor = props.getProperty("VENDOR", "").toLowerCase(Locale.ROOT);
            for (Brand brand : values()) {
                if (brand == UNKNOWN)
                    continue;

                if (implVendor.contains(brand.key) || vendor.contains(brand.key))
                    return brand;

                if (brand.aliases != null) {
                    for (String alias : brand.aliases) {
                        if (implVendor.contains(alias) || vendor.contains(alias))
                            return brand;
                    }
                }
            }

            // If release properties are inconclusive, fall back to string matching on name and path
            for (Brand brand : values()) {
                if (brand == UNKNOWN)
                    continue;

                if (name.contains(brand.key) || path.contains(brand.key))
                    return brand;

                if (brand.aliases != null) {
                    for (String alias : brand.aliases) {
                        if (name.contains(alias) || path.contains(alias))
                            return brand;
                    }
                }
            }

            return UNKNOWN;
        }
    }
}
