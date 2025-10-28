package dev.railroadide.railroad.localization;

import dev.railroadide.core.localization.Language;
import dev.railroadide.railroad.Railroad;

import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanguageRegistryLoader {
    private static final String FOLDER = "assets/railroad/lang";
    private static final Pattern FILE_PATTERN = Pattern.compile("^([a-z]{2})_([a-z]{2})\\.lang$");

    private LanguageRegistryLoader() {
    }

    public static void load() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources(FOLDER);

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                discoverFromFileSystem(url);
            }
        } catch (Exception e) {
            Railroad.LOGGER.debug("Language load failed", e);
        }
    }


    private static void discoverFromFileSystem(URL url) throws Exception {
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(url.toURI()), "*.lang")) {
                for (Path path : stream)
                    tryRegisterFromFileName(path.getFileName().toString());
            }
        } else if ("jar".equals(protocol)) {
            String spec = url.toString();
            int bang = spec.indexOf("!/");
            URI jarUri = URI.create(spec.substring(0, bang));
            String inJarPath = spec.substring(bang + 2);

            try (FileSystem fileSystem = FileSystems.newFileSystem(jarUri, Map.of())) {
                Path dir = fileSystem.getPath(inJarPath);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.lang")) {
                    for (Path path : stream)
                        tryRegisterFromFileName(path.getFileName().toString());
                }
            }
        } else {
            Railroad.LOGGER.warn("Unsupported URL scheme for languages: {}", url);
        }
    }

    private static void tryRegisterFromFileName(String fileName) {
        Matcher matcher = FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return;
        }

        String lang = matcher.group(1);
        String country = matcher.group(2);

        String fullCode = (lang + "_" + country).toUpperCase(Locale.ROOT);
        if (Language.REGISTRY.contains(fullCode)) {
            return;
        }

        Locale locale = Locale.forLanguageTag(lang + "-" + country.toUpperCase(Locale.ROOT));
        String displayName = locale.getDisplayName(locale);

        Language.builder(displayName)
            .languageCode(lang)
            .countryCode(country.toUpperCase(Locale.ROOT))
            .build();

        Railroad.LOGGER.debug("Registered language: {} ({})", displayName, fullCode);
    }
}
