package dev.railroadide.railroad;

import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion.VersionType;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TestVersionData {
    private static final List<String> DEFAULT_VERSION_ORDER = List.of(
        "1.14.4",
        "1.16.5",
        "1.17.1",
        "1.18.2",
        "1.19.4",
        "1.20",
        "1.20.1",
        "1.20.4",
        "1.21.1",
        "1.21.5"
    );

    private TestVersionData() {
    }

    static MinecraftVersion ensureVersion(String id) {
        ensureMinecraftVersions(DEFAULT_VERSION_ORDER);
        if (!DEFAULT_VERSION_ORDER.contains(id)) {
            ensureMinecraftVersions(List.of(id));
        }

        return MinecraftVersion.fromId(id).orElseThrow(() -> new IllegalStateException("Missing test Minecraft version " + id));
    }

    static MinecraftVersion ensureVersionWithPistonMeta(String id) {
        MinecraftVersion version = ensureVersion(id);
        Path metaPath = ConfigHandler.getConfigDirectory()
            .resolve("piston-meta")
            .resolve(version.id() + ".json");
        try {
            Files.createDirectories(metaPath.getParent());
            if (Files.notExists(metaPath)) {
                Files.writeString(metaPath, pistonMetaStub(version.id()));
            }
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to prepare piston meta stub for " + version.id(), ioException);
        }
        return version;
    }

    @SuppressWarnings("unchecked")
    private static void ensureMinecraftVersions(List<String> ids) {
        try {
            Field versionsField = MinecraftVersion.class.getDeclaredField("MINECRAFT_VERSIONS");
            versionsField.setAccessible(true);
            List<MinecraftVersion> versions = (List<MinecraftVersion>) versionsField.get(null);

            for (String id : ids) {
                boolean exists = versions.stream().anyMatch(v -> Objects.equals(v.id(), id));
                if (!exists) {
                    LocalDateTime now = LocalDateTime.now();
                    versions.add(new MinecraftVersion(id, VersionType.RELEASE, "https://example.com/" + id, now, now));
                }
            }

            List<MinecraftVersion> ordered = new ArrayList<>(versions);
            ordered.sort((a, b) -> indexOf(a.id()) - indexOf(b.id()));
            versions.clear();
            versions.addAll(ordered);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new IllegalStateException("Failed to seed Minecraft versions for tests", exception);
        }
    }

    private static int indexOf(String id) {
        int index = DEFAULT_VERSION_ORDER.indexOf(id);
        return index >= 0 ? index : DEFAULT_VERSION_ORDER.size();
    }

    private static String pistonMetaStub(String id) {
        return """
            {
              "arguments": {
                "game": ["--demo", "true"],
                "jvm": ["-Xmx1G"]
              },
              "assetIndex": {
                "id": "test",
                "sha1": "0123456789abcdef0123456789abcdef01234567",
                "size": 1,
                "totalSize": 1,
                "url": "https://example.com/asset-index"
              },
              "assets": "test",
              "complianceLevel": 0,
              "downloads": {
                "client": {
                  "sha1": "0123456789abcdef0123456789abcdef01234567",
                  "size": 1,
                  "url": "https://example.com/client.jar"
                },
                "client_mappings": {
                  "sha1": "0123456789abcdef0123456789abcdef01234567",
                  "size": 1,
                  "url": "https://example.com/client.txt"
                },
                "server": {
                  "sha1": "0123456789abcdef0123456789abcdef01234567",
                  "size": 1,
                  "url": "https://example.com/server.jar"
                },
                "server_mappings": {
                  "sha1": "0123456789abcdef0123456789abcdef01234567",
                  "size": 1,
                  "url": "https://example.com/server.txt"
                }
              },
              "id": "%s",
              "javaVersion": {
                "component": "java-runtime-alpha",
                "majorVersion": 17
              },
              "libraries": [
                {
                  "name": "example:lib:1.0",
                  "downloads": {
                    "artifact": {
                      "sha1": "0123456789abcdef0123456789abcdef01234567",
                      "size": 1,
                      "url": "https://example.com/lib.jar"
                    }
                  }
                }
              ],
              "logging": {
                "client": {
                  "argument": "--log",
                  "file": {
                    "id": "client",
                    "sha1": "0123456789abcdef0123456789abcdef01234567",
                    "size": 1,
                    "url": "https://example.com/log4j.jar"
                  },
                  "type": "log4j2"
                }
              },
              "mainClass": "net.minecraft.client.main.Main",
              "minimumLauncherVersion": 1,
              "releaseTime": "2024-01-01T00:00:00",
              "time": "2024-01-01T00:00:00",
              "type": "release"
            }
            """.formatted(id);
    }
}
