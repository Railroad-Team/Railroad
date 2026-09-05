package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.LanguageInspectionProvider;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

/**
 * Global registries for Java inspection extension points.
 */
public final class JavaInspectionRegistries {
    static {
        loadInspections(ClassLoader.getSystemClassLoader(), "dev.railroadide.railroad.ide.diagnostics.inspections");
    }

    // TODO: Plugins need to be able to register their own inspections, so we need to load inspections from plugin class
    // loaders as well.
    private static void loadInspections(ClassLoader classLoader, String packageName) {
        var reflections = new Reflections(
            new ConfigurationBuilder()
                .addClassLoaders(classLoader)
                .forPackage(packageName)
                .setScanners(Scanners.TypesAnnotated));

        List<JavaInspectionRuleProvider> registeredInspections = reflections
            .get(Scanners.TypesAnnotated.with(RegisteredInspection.class).asClass()).stream()
            .filter(clazz -> {
                if (!JavaInspectionRuleProvider.class.isAssignableFrom(clazz)) {
                    Railroad.LOGGER.error(
                        "Class {} is annotated with @RegisteredInspection but does not implement JavaInspectionRuleProvider",
                        clazz.getName());
                    return false;
                }

                return true;
            })
            .filter(clazz -> {
                try {
                    clazz.getConstructor();
                    return true;
                } catch (NoSuchMethodException _) {
                    Railroad.LOGGER.error(
                        "Class {} is annotated with @RegisteredInspection but does not have a no-arg constructor",
                        clazz.getName());
                    return false;
                }
            })
            .map(clazz -> {
                try {
                    return (JavaInspectionRuleProvider) clazz.getConstructor().newInstance();
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Failed to instantiate JavaInspectionRuleProvider class {}", clazz.getName(),
                        exception);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();

        for (JavaInspectionRuleProvider provider : registeredInspections) {
            if (containsRuleProvider(provider.id())) {
                JavaInspectionRuleProvider existing = getRuleProvider(provider.id());
                String existingClassName = existing != null ? existing.getClass().getName() : "null";
                Railroad.LOGGER.error("Duplicate JavaInspectionRuleProvider with id {}: {} and {}", provider.id(),
                    provider.getClass().getName(), existingClassName);
                continue;
            }

            registerRuleProvider(provider.id(), provider);
        }
    }

    private JavaInspectionRegistries() {
    }

    /**
     * Returns registered Java providers whose identifiers start with {@code railroad}.
     *
     * @return built-in Java inspection providers
     */
    public static List<JavaInspectionRuleProvider> coreRuleProviders() {
        return ruleProviders().stream()
            .filter(provider -> provider.id().startsWith("railroad"))
            .toList();
    }

    /**
     * Registers a Java inspection provider in the language inspection registry.
     *
     * @param id stable rule or provider identifier
     * @param provider provider supplying the inspection rules
     * @return registered provider
     */
    public static JavaInspectionRuleProvider registerRuleProvider(String id, JavaInspectionRuleProvider provider) {
        Objects.requireNonNull(provider, "provider");
        LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.register(id, provider);
        return provider;
    }

    /**
     * Removes the provider registered under the supplied identifier.
     *
     * @param id stable rule or provider identifier
     * @return removed Java provider, or null if absent
     */
    public static JavaInspectionRuleProvider unregisterRuleProvider(String id) {
        return asJavaProvider(LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.unregister(id));
    }

    /**
     * Looks up a Java inspection provider by identifier.
     *
     * @param id stable rule or provider identifier
     * @return registered Java provider, or null if absent
     */
    public static JavaInspectionRuleProvider getRuleProvider(String id) {
        return asJavaProvider(LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.get(id));
    }

    /**
     * Checks whether the identifier refers to a provider for Java inspections.
     *
     * @param id stable rule or provider identifier
     * @return whether a matching Java provider is registered
     */
    public static boolean containsRuleProvider(String id) {
        LanguageInspectionProvider provider = LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY
            .get(id);
        return provider instanceof JavaInspectionRuleProvider javaProvider
            && JavaLanguageSupport.LANGUAGE_ID.equals(javaProvider.languageId());
    }

    /**
     * Returns the currently registered Java inspection providers.
     *
     * @return list of registered Java providers
     */
    public static List<JavaInspectionRuleProvider> ruleProviders() {
        return ruleProviderEntries().values().stream().toList();
    }

    /**
     * Collects the registered Java providers by identifier.
     *
     * @return map of provider identifiers to Java providers
     */
    public static Map<String, JavaInspectionRuleProvider> ruleProviderEntries() {
        return LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY.entries().entrySet().stream()
            .filter(entry -> entry.getValue() instanceof JavaInspectionRuleProvider javaProvider
                && JavaLanguageSupport.LANGUAGE_ID.equals(javaProvider.languageId()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> (JavaInspectionRuleProvider) entry.getValue(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private static JavaInspectionRuleProvider asJavaProvider(LanguageInspectionProvider provider) {
        if (provider == null)
            return null;
        if (provider instanceof JavaInspectionRuleProvider javaProvider)
            return javaProvider;

        throw new IllegalStateException("Inspection provider '" + provider.id() + "' is not a Java provider.");
    }
}
