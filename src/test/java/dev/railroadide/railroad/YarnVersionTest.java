package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.mappings.YarnVersionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YarnVersionTest {
    @BeforeAll
    static void prepareVersions() {
        TestVersionData.ensureVersion("1.20.4");
        TestVersionData.ensureVersion("1.21.5");
    }

    @Test
    public void testLatestYarnVersion() {
        assertEquals("1.20.4+build.3", YarnVersionService.INSTANCE.latestFor(MinecraftVersion.fromId("1.20.4").orElseThrow()).orElse(""));
        assertEquals("1.21.5+build.1", YarnVersionService.INSTANCE.latestFor(MinecraftVersion.fromId("1.21.5").orElseThrow()).orElse(""));
    }

    @Test
    public void testListYarnVersions() {
        assertEquals(3, YarnVersionService.INSTANCE.listVersionsFor(MinecraftVersion.fromId("1.20.4").orElseThrow()).size());
        assertEquals(1, YarnVersionService.INSTANCE.listVersionsFor(MinecraftVersion.fromId("1.21.5").orElseThrow()).size());
    }

    @Test
    public void testListAllYarnVersions() {
        assertTrue(YarnVersionService.INSTANCE.listAllVersions().size() >= 200);
    }
}
