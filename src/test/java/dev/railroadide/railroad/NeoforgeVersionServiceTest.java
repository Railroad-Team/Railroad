package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.forge.NeoforgeVersionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NeoforgeVersionServiceTest {
    @Test
    void testLatestNeoForgeVersion() {
        MinecraftVersion minecraft = TestVersionData.ensureVersion("1.21.1");

        Optional<String> latest = NeoforgeVersionService.INSTANCE.latestFor(minecraft);

        assertTrue(latest.isPresent());
        assertTrue(latest.orElseThrow().contains("21.1"));
    }

    @Test
    void testListNeoForgeVersions() {
        MinecraftVersion minecraft = TestVersionData.ensureVersion("1.20.4");

        List<String> versions = NeoforgeVersionService.INSTANCE.listVersionsFor(minecraft, true);

        assertFalse(versions.isEmpty());
        assertTrue(versions.stream().allMatch(version -> version.contains("20.4")));
    }
}
