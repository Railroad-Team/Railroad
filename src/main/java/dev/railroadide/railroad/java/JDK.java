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

@ToString
@EqualsAndHashCode
public final class JDK {
    private final Path path;
    private final String name;
    private final JavaVersion version;
    private final Brand brand;
    private final JDKCLI cli;

    public JDK(Path path, String name, JavaVersion version, Brand brand) {
        this.path = path;
        this.name = name;
        this.version = version;
        this.brand = brand;
        this.cli = new JDKCLI(this);
    }

    public JDK(Path path, String name, JavaVersion version) {
        this.path = path;
        this.name = name;
        this.version = version;
        this.brand = Brand.from(this);
        this.cli = new JDKCLI(this);
    }

    public Path path() {
        return path;
    }

    public String name() {
        return name;
    }

    public JavaVersion version() {
        return version;
    }

    public Brand brand() {
        return brand;
    }

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

        Brand(String key, Ikon icon, String... aliases) {
            this.key = key;
            this.icon = icon;
            this.aliases = aliases;
        }

        Brand(String key, String imagePath, String... aliases) {
            this.key = key;
            this.imagePath = imagePath;
            this.aliases = aliases;
        }

        Brand(String key, Ikon icon) {
            this(key, icon, (String[]) null);
        }

        Brand(String key, String imagePath) {
            this(key, imagePath, (String[]) null);
        }

        public boolean isIkon() {
            return icon != null;
        }

        public boolean isImage() {
            return imagePath != null;
        }

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
