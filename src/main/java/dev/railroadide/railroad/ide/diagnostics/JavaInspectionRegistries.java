package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Global registries for Java inspection extension points.
 */
public final class JavaInspectionRegistries {
    public static final Registry<JavaInspectionRuleProvider> JAVA_INSPECTION_RULE_PROVIDER_REGISTRY =
        RegistryManager.createRegistry("railroad:java_inspection_rule_provider", JavaInspectionRuleProvider.class);

    static {
        var reflections = new Reflections(
            new ConfigurationBuilder()
                .addClassLoaders(ClassLoader.getSystemClassLoader())
                .setScanners(Scanners.TypesAnnotated)
        );

        List<JavaInspectionRuleProvider> registeredInspections = reflections.get(Scanners.TypesAnnotated.with(RegisteredInspection.class).asClass()).stream()
            .filter(clazz -> {
                if (!clazz.isAssignableFrom(JavaInspectionRuleProvider.class)) {
                    Railroad.LOGGER.error("Class {} is annotated with @RegisteredInspection but does not implement JavaInspectionRuleProvider", clazz.getName());
                    return false;
                }

                return true;
            })
            .filter(clazz -> {
                try {
                    clazz.getConstructor();
                    return true;
                } catch (NoSuchMethodException ignored) {
                    Railroad.LOGGER.error("Class {} is annotated with @RegisteredInspection but does not have a no-arg constructor", clazz.getName());
                    return false;
                }
            })
            .map(clazz -> {
                try {
                    return (JavaInspectionRuleProvider) clazz.getConstructor().newInstance();
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Failed to instantiate JavaInspectionRuleProvider class {}", clazz.getName(), exception);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();

        for (JavaInspectionRuleProvider provider : registeredInspections) {
            if (JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.contains(provider.id())) {
                JavaInspectionRuleProvider existing = JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.get(provider.id());
                Railroad.LOGGER.error("Duplicate JavaInspectionRuleProvider with id {}: {} and {}", provider.id(), provider.getClass().getName(), existing.getClass().getName());
                continue;
            }

            JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(provider.id(), provider);
        }
    }

    private JavaInspectionRegistries() {
    }

    public static List<JavaInspectionRuleProvider> coreRuleProviders() {
        return JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.values().stream()
            .filter(provider -> provider.id().startsWith("railroad:core-"))
            .toList();
    }
}
