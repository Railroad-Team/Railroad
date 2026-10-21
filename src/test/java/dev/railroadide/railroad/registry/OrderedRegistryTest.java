package dev.railroadide.railroad.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrderedRegistryTest {

    @Test
    public void preservesRegistrationOrder() {
        Registry<String> registry = RegistryManager.createOrderedRegistry(
            "test:ordered-registry-" + System.nanoTime(),
            String.class);

        registry.register("b", "second");
        registry.register("a", "first");
        registry.register("c", "third");

        assertEquals(List.of("b", "a", "c"), registry.keys());
        assertEquals(List.of("second", "first", "third"), registry.values());
    }
}
