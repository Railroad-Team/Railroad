package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.forge.ForgeVersionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ForgeVersionServiceTest {
    @Test
    void testLatestForgeVersion() {
        MinecraftVersion minecraft = TestVersionData.ensureVersion("1.20.1");

        Optional<String> latest = ForgeVersionService.INSTANCE.latestFor(minecraft);

        assertTrue(latest.isPresent());
        assertTrue(latest.orElseThrow().startsWith(minecraft.id() + "-"));
    }

    @Test
    void testListForgeVersions() {
        MinecraftVersion minecraft = TestVersionData.ensureVersion("1.20.1");

        List<String> versions = ForgeVersionService.INSTANCE.listVersionsFor(minecraft);

        assertFalse(versions.isEmpty());
        assertTrue(versions.stream().allMatch(version -> version.startsWith(minecraft.id())));
    }
}
