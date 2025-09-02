package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.fabric.FabricApiMavenVersionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FabricApiVersionTest {
    @Test
    public void testLatestFabricApiVersion() {
        assertEquals("0.97.3+1.20.4", FabricApiMavenVersionService.INSTANCE.latestFor("1.20.4").orElse(""));
        assertEquals("0.128.2+1.21.5", FabricApiMavenVersionService.INSTANCE.latestFor("1.21.5").orElse(""));
    }

    @Test
    public void testListFabricApiVersions() {
        assertEquals(22, FabricApiMavenVersionService.INSTANCE.listVersionsFor("1.20.4").size());
        assertEquals(44, FabricApiMavenVersionService.INSTANCE.listVersionsFor("1.21.5").size());
    }
}
