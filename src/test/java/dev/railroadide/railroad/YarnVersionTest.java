package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.fabric.YarnVersionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YarnVersionTest {
    @Test
    public void testLatestYarnVersion() {
        assertEquals("1.20.4+build.3", YarnVersionService.INSTANCE.latestFor("1.20.4").orElse(""));
        assertEquals("1.21.5+build.1", YarnVersionService.INSTANCE.latestFor("1.21.5").orElse(""));
    }

    @Test
    public void testListYarnVersions() {
        assertEquals(3, YarnVersionService.INSTANCE.listVersionsFor("1.20.4").size());
        assertEquals(1, YarnVersionService.INSTANCE.listVersionsFor("1.21.5").size());
    }

    @Test
    public void testListAllYarnVersions() {
        assertTrue(YarnVersionService.INSTANCE.listAllVersions().size() >= 200);
    }
}
