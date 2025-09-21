package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.mappings.MCPVersionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MCPVersionServiceTest {
    @Test
    void testLatestMcpVersion() {
        MinecraftVersion minecraft = TestVersionData.ensureVersion("1.20.1");

        Optional<String> latest = MCPVersionService.INSTANCE.latestFor(minecraft, true);

        assertTrue(latest.isPresent());
        assertTrue(latest.orElseThrow().contains(minecraft.id()));
    }

    @Test
    void testListMcpVersions() {
        MinecraftVersion minecraft = TestVersionData.ensureVersion("1.20.1");

        List<String> versions = MCPVersionService.INSTANCE.listVersionsFor(minecraft, true);

        assertFalse(versions.isEmpty());
        assertTrue(versions.stream().allMatch(version -> version.contains(minecraft.id())));
    }
}
