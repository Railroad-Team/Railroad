package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.ide.language.LanguageCapabilities;
import dev.railroadide.railroad.ide.language.LanguageRuntime;
import dev.railroadide.railroad.ide.sst.provider.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.Set;

/** Test registrations exercise the same contribution/session path as plugins. */
public final class IndexTestProviders {
    private static final Map<ProjectLanguageIndexService, Fixture> FIXTURES = new WeakHashMap<>();

    private IndexTestProviders() {
    }

    public static synchronized ProjectLanguageIndexService service() {
        var runtime = new LanguageRuntime();
        var service = new ProjectLanguageIndexService(runtime);
        FIXTURES.put(service, new Fixture(runtime, new HashMap<>()));
        return service;
    }

    public static synchronized void indexer(ProjectLanguageIndexService service, ProjectLanguageIndexer<?, ?> indexer) {
        Registration registration = registration(service, indexer.languageId());
        registration.indexer = indexer;
        publish(service, registration);
    }

    public static synchronized void persistence(
        ProjectLanguageIndexService service,
        ProjectLanguageIndexPersistence<?> persistence
    ) {
        Registration registration = registration(service, persistence.languageId());
        registration.persistence = persistence;
        publish(service, registration);
    }

    public static synchronized LanguageRuntime runtime(ProjectLanguageIndexService service) {
        return FIXTURES.get(service).runtime;
    }

    public static synchronized void context(
        ProjectLanguageIndexService service,
        LanguageIndexContextContributor contributor
    ) {
        Registration registration = registration(service, contributor.languageId());
        registration.context = contributor;
        publish(service, registration);
    }

    private static void publish(ProjectLanguageIndexService service, Registration registration) {
        LanguageRuntime runtime = runtime(service);
        runtime.unregister(registration.id());
        runtime.register("test", registration);
    }

    private static Registration registration(ProjectLanguageIndexService service, String language) {
        Fixture fixture = FIXTURES.get(service);
        if (fixture == null)
            throw new IllegalArgumentException("Use IndexTestProviders.service()");
        return fixture.registrations.computeIfAbsent(language,
            id -> new Registration(new LanguageId(id.contains(":") ? id : "railroad:" + id)));
    }

    private record Fixture(LanguageRuntime runtime, Map<String, Registration> registrations) {
    }

    private static final class Registration implements LanguageContribution {
        private final LanguageId id;
        private ProjectLanguageIndexer<?, ?> indexer;
        private ProjectLanguageIndexPersistence<?> persistence;
        private LanguageIndexContextContributor context;

        private Registration(LanguageId id) {
            this.id = id;
        }

        @Override
        public LanguageId id() {
            return id;
        }

        @Override
        public String displayName() {
            return id.value();
        }

        @Override
        public LanguageMatcher matcher() {
            return input -> indexer != null && indexer.supports(input.path()) ? 1 : 0;
        }

        @Override
        public Set<CapabilityKey<?>> supportedCapabilities() {
            var keys = new HashSet<CapabilityKey<?>>();
            if (indexer != null) {
                keys.add(LanguageCapabilities.INDEXER);
            }
            if (persistence != null) {
                keys.add(LanguageCapabilities.PERSISTENCE);
            }
            if (context != null) {
                keys.add(LanguageCapabilities.INDEX_CONTEXT);
            }
            return Set.copyOf(keys);
        }

        @Override
        public LanguageSession openSession(LanguageSessionContext context) {
            var capabilities = CapabilityRegistry.builder();
            if (indexer != null) {
                capabilities.add(LanguageCapabilities.INDEXER, indexer);
            }
            if (persistence != null) {
                capabilities.add(LanguageCapabilities.PERSISTENCE, persistence);
            }
            if (this.context != null) {
                capabilities.add(LanguageCapabilities.INDEX_CONTEXT, this.context);
            }
            return new LanguageSession(capabilities.build());
        }
    }
}
