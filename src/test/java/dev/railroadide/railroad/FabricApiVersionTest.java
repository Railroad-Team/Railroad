package dev.railroadide.railroad;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.fabric.FabricApiVersionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FabricApiVersionTest {
    @BeforeAll
    static void prepareVersions() {
        TestVersionData.ensureVersion("1.20.4");
        TestVersionData.ensureVersion("1.21.5");
    }

    @Test
    public void testLatestFabricApiVersion() {
        assertEquals("0.97.3+1.20.4", FabricApiVersionService.INSTANCE.latestFor(MinecraftVersion.fromId("1.20.4").orElseThrow()).orElse(""));
        assertEquals("0.128.2+1.21.5", FabricApiVersionService.INSTANCE.latestFor(MinecraftVersion.fromId("1.21.5").orElseThrow()).orElse(""));
    }

    @Test
    public void testListFabricApiVersions() {
        assertEquals(22, FabricApiVersionService.INSTANCE.listVersionsFor(MinecraftVersion.fromId("1.20.4").orElseThrow()).size());
        assertEquals(44, FabricApiVersionService.INSTANCE.listVersionsFor(MinecraftVersion.fromId("1.21.5").orElseThrow()).size());
    }
}
