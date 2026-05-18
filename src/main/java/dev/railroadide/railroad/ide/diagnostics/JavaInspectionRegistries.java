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
        loadInspections(ClassLoader.getSystemClassLoader(), "dev.railroadide.railroad.ide.diagnostics.inspections");
    }

    // TODO: Plugins need to be able to register their own inspections, so we need to load inspections from plugin class loaders as well.
    private static void loadInspections(ClassLoader classLoader, String packageName) {
        var reflections = new Reflections(
            new ConfigurationBuilder()
                .addClassLoaders(classLoader)
                .forPackage(packageName)
                .setScanners(Scanners.TypesAnnotated)
        );

        List<JavaInspectionRuleProvider> registeredInspections = reflections.get(Scanners.TypesAnnotated.with(RegisteredInspection.class).asClass()).stream()
            .filter(clazz -> {
                if (!JavaInspectionRuleProvider.class.isAssignableFrom(clazz)) {
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
                String existingClassName = existing != null ? existing.getClass().getName() : "null";
                Railroad.LOGGER.error("Duplicate JavaInspectionRuleProvider with id {}: {} and {}", provider.id(), provider.getClass().getName(), existingClassName);
                continue;
            }

            JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(provider.id(), provider);
        }
    }

    private JavaInspectionRegistries() {
    }

    public static List<JavaInspectionRuleProvider> coreRuleProviders() {
        return JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.values().stream()
            .filter(provider -> provider.id().startsWith("railroad"))
            .toList();
    }
}
