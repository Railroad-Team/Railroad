package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MinecraftVersionTest {
    @BeforeAll
    static void seedVersions() {
        // ensure test versions are available for all dependent tests
        TestVersionData.ensureVersion("1.20.4");
        TestVersionData.ensureVersion("1.20");
        TestVersionData.ensureVersion("1.21.1");
        TestVersionData.ensureVersion("1.21.5");
    }

    @Test
    void testFromIdReturnsVersion() {
        Optional<MinecraftVersion> version = MinecraftVersion.fromId("1.20.4");
        assertTrue(version.isPresent());
        assertEquals("1.20.4", version.orElseThrow().id());
    }

    @Test
    void testGetVersionsAfter() {
        MinecraftVersion base = MinecraftVersion.fromId("1.20.1").orElseThrow();
        List<MinecraftVersion> after = MinecraftVersion.getVersionsAfter(base);
        assertFalse(after.isEmpty());
        assertEquals("1.20.4", after.getFirst().id());
    }

    @Test
    void testGetMajorVersion() {
        MinecraftVersion version = MinecraftVersion.fromId("1.20.4").orElseThrow();
        Optional<MinecraftVersion> major = MinecraftVersion.getMajorVersion(version);
        assertTrue(major.isPresent());
        assertEquals("1.20", major.orElseThrow().id());
    }
}
