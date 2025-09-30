package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.mappings.MojmapVersionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MojmapVersionServiceTest {
    @Test
    void testLatestMojmapVersion() {
        MinecraftVersion minecraft = TestVersionData.ensureVersionWithPistonMeta("1.20.4");

        Optional<String> latest = MojmapVersionService.INSTANCE.latestFor(minecraft);

        assertTrue(latest.isPresent());
        assertEquals(minecraft.id(), latest.orElseThrow());
    }

    @Test
    void testListMojmapVersions() {
        List<String> versions = MojmapVersionService.INSTANCE.listAllVersions();

        assertFalse(versions.isEmpty());
        assertTrue(versions.contains("1.20.4"));
    }
}
