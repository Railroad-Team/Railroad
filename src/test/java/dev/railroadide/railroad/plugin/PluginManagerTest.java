package dev.railroadide.railroad.plugin;

import dev.railroadide.railroad.ide.diagnostics.LanguageInspectionRegistries;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.plugin.defaults.DefaultPluginDescriptor;
import dev.railroadide.railroad.plugin.spi.PluginDescriptor;
import dev.railroadide.railroad.plugin.spi.deps.MavenDeps;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PluginManagerTest {

    @Test
    void loadsRegistersAndUnregistersJavaInspectionRuleProviders() {
        PluginDescriptor descriptor = testDescriptor("plugin-lifecycle-" + UUID.randomUUID());
        JavaInspectionRuleProvider provider = PluginManager.loadJavaInspectionRuleProviders(
                TestJavaInspectionRuleProvider.class.getClassLoader()
        ).stream()
                .filter(candidate -> TestJavaInspectionRuleProvider.PROVIDER_ID.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Test service provider was not discovered"));

        String registrationId = PluginManager.javaInspectionRuleProviderRegistrationId(descriptor, provider);
        PluginLoadResult loadResult = new PluginLoadResult(Path.of("test-plugin.jar"), descriptor);

        try {
            List<String> registeredIds = PluginManager.registerJavaInspectionRuleProviders(descriptor, List.of(provider));
            loadResult.setJavaInspectionRuleProviderRegistrationIds(registeredIds);

            assertEquals(List.of(registrationId), registeredIds);
            assertTrue(JavaInspectionRegistries.containsRuleProvider(registrationId));
            assertSame(provider, JavaInspectionRegistries.getRuleProvider(registrationId));
            assertTrue(LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.contains(registrationId));
            assertSame(provider, LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.get(registrationId));

            PluginManager.unregisterJavaInspectionRuleProviders(loadResult);

            assertFalse(JavaInspectionRegistries.containsRuleProvider(registrationId));
            assertFalse(LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.contains(registrationId));
            assertTrue(loadResult.javaInspectionRuleProviderRegistrationIds().isEmpty());
        } finally {
            if (JavaInspectionRegistries.containsRuleProvider(registrationId))
                JavaInspectionRegistries.unregisterRuleProvider(registrationId);
        }
    }

    @Test
    void registerJavaInspectionRuleProvidersRollsBackDuplicateIds() {
        PluginDescriptor descriptor = testDescriptor("plugin-duplicate-" + UUID.randomUUID());
        JavaInspectionRuleProvider provider = new TestJavaInspectionRuleProvider();
        String registrationId = PluginManager.javaInspectionRuleProviderRegistrationId(descriptor, provider);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                PluginManager.registerJavaInspectionRuleProviders(descriptor, List.of(provider, provider))
        );

        assertTrue(exception.getMessage().contains(registrationId));
        assertFalse(JavaInspectionRegistries.containsRuleProvider(registrationId));
    }

    private static PluginDescriptor testDescriptor(String pluginId) {
        return DefaultPluginDescriptor.builder(pluginId)
                .name("Test Plugin")
                .version("1.0.0")
                .mainClass("dev.railroadide.railroad.plugin.TestPlugin")
                .dependencies(new MavenDeps(List.of(), List.of()))
                .build();
    }
}
