package dev.railroadide.railroad.plugin;

import dev.railroadide.railroad.ide.diagnostics.LanguageInspectionRegistries;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.plugin.defaults.DefaultPluginDescriptor;
import dev.railroadide.railroad.plugin.spi.PluginDescriptor;
import dev.railroadide.railroad.plugin.spi.Plugin;
import dev.railroadide.railroad.plugin.spi.PluginContext;
import dev.railroadide.railroad.plugin.spi.deps.MavenDeps;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.ShutdownHooks;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class PluginManagerTest {

    @Test
    public void loadsRegistersAndUnregistersJavaInspectionRuleProviders() {
        PluginDescriptor descriptor = testDescriptor("plugin-lifecycle-" + UUID.randomUUID());
        JavaInspectionRuleProvider provider = PluginManager.loadJavaInspectionRuleProviders(
            TestJavaInspectionRuleProvider.class.getClassLoader()).stream()
            .filter(candidate -> TestJavaInspectionRuleProvider.PROVIDER_ID.equals(candidate.id()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Test service provider was not discovered"));

        String registrationId = PluginManager.javaInspectionRuleProviderRegistrationId(descriptor, provider);
        var loadResult = new PluginLoadResult(Path.of("test-plugin.jar"), descriptor);

        try {
            List<String> registeredIds = PluginManager.registerJavaInspectionRuleProviders(descriptor,
                List.of(provider));
            loadResult.setJavaInspectionRuleProviderRegistrationIds(registeredIds);

            assertEquals(List.of(registrationId), registeredIds);
            assertTrue(JavaInspectionRegistries.containsRuleProvider(registrationId));
            assertSame(provider, JavaInspectionRegistries.getRuleProvider(registrationId));
            assertTrue(LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.contains(registrationId));
            assertSame(provider,
                LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.get(registrationId));

            PluginManager.unregisterJavaInspectionRuleProviders(loadResult);

            assertFalse(JavaInspectionRegistries.containsRuleProvider(registrationId));
            assertFalse(LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.contains(registrationId));
            assertTrue(loadResult.javaInspectionRuleProviderRegistrationIds().isEmpty());
        } finally {
            if (JavaInspectionRegistries.containsRuleProvider(registrationId)) {
                JavaInspectionRegistries.unregisterRuleProvider(registrationId);
            }
        }
    }

    @Test
    public void registerJavaInspectionRuleProvidersRollsBackDuplicateIds() {
        PluginDescriptor descriptor = testDescriptor("plugin-duplicate-" + UUID.randomUUID());
        JavaInspectionRuleProvider provider = new TestJavaInspectionRuleProvider();
        String registrationId = PluginManager.javaInspectionRuleProviderRegistrationId(descriptor, provider);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> PluginManager.registerJavaInspectionRuleProviders(descriptor, List.of(provider, provider)));

        assertTrue(exception.getMessage().contains(registrationId));
        assertFalse(JavaInspectionRegistries.containsRuleProvider(registrationId));
    }

    @Test
    public void failedLanguageDisposalStillReleasesPluginRegistrationsAndLoader() throws Exception {
        PluginDescriptor descriptor = testDescriptor("plugin-cleanup-" + UUID.randomUUID());
        var languageId = new LanguageId(descriptor.getId() + ":language");
        var closeCalls = new AtomicInteger();
        var loaderCloseCalls = new AtomicInteger();
        var disposeKey = new CapabilityKey<AutoCloseable>("test:dispose", AutoCloseable.class);
        var contribution = new LanguageContribution() {
            @Override
            public LanguageId id() {
                return languageId;
            }

            @Override
            public String displayName() {
                return "Cleanup test";
            }

            @Override
            public LanguageMatcher matcher() {
                return LanguageMatcher.extension("cleanup");
            }

            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(disposeKey);
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                AutoCloseable service = () -> {
                    closeCalls.incrementAndGet();
                    throw new IOException("Provider disposal failed");
                };
                return new LanguageSession(CapabilityRegistry.builder().add(disposeKey, service).build());
            }
        };
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
            new Class<?>[]{Project.class},
            (_, method, _) -> method.getName().equals("getPath") ? Path.of(".") : null);
        var provider = new TestJavaInspectionRuleProvider();
        var loadResult = new PluginLoadResult(Path.of("test-plugin.jar"), descriptor);
        var loader = new PluginClassLoader(loadResult.pluginPath(), new MavenDeps(List.of(), List.of())) {
            @Override
            public void close() throws IOException {
                loaderCloseCalls.incrementAndGet();
                super.close();
            }
        };
        loadResult.setPlugin(null, loader);
        loadResult.setShutdownRegistration(ShutdownHooks.registerHook(() -> fail("Unloaded plugin hook retained")));
        LanguageSupportRegistry.runtime().register(descriptor.getId(), contribution);
        var session = LanguageSupportRegistry.runtime().session(project, languageId, "");
        loadResult.setJavaInspectionRuleProviderRegistrationIds(
            PluginManager.registerJavaInspectionRuleProviders(descriptor, List.of(provider)));
        String inspectionId = PluginManager.javaInspectionRuleProviderRegistrationId(descriptor, provider);
        try {
            var unload = PluginManager.class.getDeclaredMethod("unloadPluginRuntime", PluginLoadResult.class);
            unload.setAccessible(true);
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> unload.invoke(null, loadResult));
            assertInstanceOf(IOException.class, failure.getCause());
            assertTrue(session.isClosed());
            assertTrue(LanguageSupportRegistry.runtime().get(languageId).isEmpty());
            assertFalse(JavaInspectionRegistries.containsRuleProvider(inspectionId));
            assertTrue(loadResult.javaInspectionRuleProviderRegistrationIds().isEmpty());
            assertNull(loadResult.classLoader());
            assertNull(loadResult.pluginInstance());
            var shutdownRegistration = PluginLoadResult.class.getDeclaredField("shutdownRegistration");
            shutdownRegistration.setAccessible(true);
            assertNull(shutdownRegistration.get(loadResult));
            assertEquals(1, closeCalls.get());
            assertEquals(1, loaderCloseCalls.get());
            assertDoesNotThrow(() -> unload.invoke(null, loadResult));
            assertEquals(1, closeCalls.get());
            assertEquals(1, loaderCloseCalls.get());
        } finally {
            LanguageSupportRegistry.runtime().unregisterOwner(descriptor.getId());
            LanguageSupportRegistry.runtime().closeProject(project);
            PluginManager.unregisterJavaInspectionRuleProviders(loadResult);
            loadResult.clearShutdownRegistration();
            loader.close();
        }
    }

    private static PluginDescriptor testDescriptor(String pluginId) {
        return DefaultPluginDescriptor.builder(pluginId)
            .name("Test Plugin")
            .version("1.0.0")
            .mainClass("dev.railroadide.railroad.plugin.TestPlugin")
            .dependencies(new MavenDeps(List.of(), List.of()))
            .build();
    }

    @Test
    public void incompatibleDisableCallbackStillDisposesThePluginRuntime() throws Exception {
        PluginDescriptor descriptor = testDescriptor("plugin-linkage-" + UUID.randomUUID());
        var languageId = new LanguageId(descriptor.getId() + ":language");
        var loaderCloseCalls = new AtomicInteger();
        var loadResult = new PluginLoadResult(Path.of("test-plugin.jar"), descriptor);
        var loader = new PluginClassLoader(loadResult.pluginPath(), new MavenDeps(List.of(), List.of())) {
            @Override
            public void close() throws IOException {
                loaderCloseCalls.incrementAndGet();
                super.close();
            }
        };
        var contribution = new LanguageContribution() {
            @Override
            public LanguageId id() {
                return languageId;
            }

            @Override
            public String displayName() {
                return "Linkage test";
            }

            @Override
            public LanguageMatcher matcher() {
                return LanguageMatcher.extension("linkage");
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return new LanguageSession(CapabilityRegistry.empty());
            }
        };
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
            new Class<?>[]{Project.class},
            (_, method, _) -> method.getName().equals("getPath") ? Path.of(".") : null);
        Plugin plugin = new Plugin() {
            @Override
            public void onEnable(PluginContext context) {
            }

            @Override
            public void onDisable(PluginContext context) {
                throw new NoClassDefFoundError("removed.PluginApi");
            }
        };
        loadResult.setPlugin(plugin, loader);
        LanguageSupportRegistry.runtime().register(descriptor.getId(), contribution);
        var session = LanguageSupportRegistry.runtime().session(project, languageId, "");
        try {
            var disableRuntime = PluginManager.class.getDeclaredMethod("disablePluginRuntime", PluginLoadResult.class);
            disableRuntime.setAccessible(true);
            var failure = assertInstanceOf(RuntimeException.class, disableRuntime.invoke(null, loadResult));
            assertInstanceOf(NoClassDefFoundError.class, failure.getCause());
            assertTrue(session.isClosed());
            assertTrue(LanguageSupportRegistry.runtime().get(languageId).isEmpty());
            assertNull(loadResult.pluginInstance());
            assertNull(loadResult.classLoader());
            assertEquals(1, loaderCloseCalls.get());
        } finally {
            LanguageSupportRegistry.runtime().unregisterOwner(descriptor.getId());
            LanguageSupportRegistry.runtime().closeProject(project);
            if (loadResult.classLoader() != null) {
                loader.close();
            }
        }
    }
}
