package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.mappings.ParchmentVersionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParchmentVersionServiceTest {
    @Test
    void testLatestParchmentVersion() {
        MinecraftVersion minecraft = TestVersionData.ensureVersion("1.20.4");

        Optional<String> latest = ParchmentVersionService.INSTANCE.latestFor(minecraft, true);

        assertTrue(latest.isPresent());
        assertTrue(latest.orElseThrow().startsWith(minecraft.id()));
    }

    @Test
    void testListParchmentVersions() {
        MinecraftVersion minecraft = TestVersionData.ensureVersion("1.20.4");

        List<String> versions = ParchmentVersionService.INSTANCE.listVersionsFor(minecraft, true);

        assertFalse(versions.isEmpty());
        assertTrue(versions.stream().allMatch(version -> version.startsWith(minecraft.id())));
    }
}
