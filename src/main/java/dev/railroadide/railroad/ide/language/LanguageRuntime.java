package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.slf4j.LoggerFactory;

/** Owns language registrations and per-project/dialect sessions, including plugin unload. */
public final class LanguageRuntime implements AutoCloseable {
    private final Map<LanguageId, Registration> registrations = new HashMap<>();
    private final Map<Project, Map<SessionKey, LanguageSession>> sessions = new IdentityHashMap<>();
    private boolean closed;
    private long registrationRevision;

    /**
     * Changes whenever the set of provider registrations changes.
     *
     * @return the current registration revision
     */
    public synchronized long registrationRevision() {
        return registrationRevision;
    }

    /**
     * Registers a language contribution under an owner for later cleanup.
     *
     * @param owner nonblank identity of the plugin or host owning the contribution
     * @param contribution language descriptor and session factory
     * @throws IllegalStateException if this runtime is closed
     * @throws IllegalArgumentException if the descriptor is invalid or its language ID is already registered
     */
    public void register(String owner, LanguageContribution contribution) {
        registerOwned(owner, contribution);
    }

    private synchronized Registration registerOwned(String owner, LanguageContribution contribution) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(contribution, "contribution");
        if (closed)
            throw new IllegalStateException("Language runtime is closed");
        if (owner.isBlank())
            throw new IllegalArgumentException("Language owner cannot be blank");
        Objects.requireNonNull(contribution.id(), "contribution.id");
        Objects.requireNonNull(contribution.matcher(), "contribution.matcher");
        Objects.requireNonNull(contribution.displayName(), "contribution.displayName");
        Objects.requireNonNull(contribution.contentKind(), "contribution.contentKind");
        Set<CapabilityKey<?>> capabilities = Set.copyOf(contribution.supportedCapabilities());
        Set<String> capabilityIds = new HashSet<>();
        for (CapabilityKey<?> key : capabilities) {
            if (!key.type().isInterface() || !capabilityIds.add(key.id()))
                throw new IllegalArgumentException("Invalid or conflicting capability contract: " + key.id());
        }
        if (contribution.displayName().isBlank())
            throw new IllegalArgumentException("Language display name cannot be blank");
        var registration = new Registration(owner, contribution, contribution.id(), contribution.matcher(),
            contribution.priority(), capabilities);
        if (registrations.putIfAbsent(registration.id, registration) != null)
            throw new IllegalArgumentException("Language already registered: " + registration.id);
        registrationRevision++;
        return registration;
    }

    /**
     * Discovers only this loader's services. Failed batches roll back their own registrations.
     *
     * @param owner identity that owns the discovered contributions
     * @param loader class loader whose own language services are loaded
     */
    public void discover(String owner, ClassLoader loader) {
        List<Registration> added = new ArrayList<>();
        try {
            Iterable<ServiceLoader.Provider<LanguageContribution>> providers = () -> ServiceLoader
                .load(LanguageContribution.class, loader).stream().iterator();
            for (ServiceLoader.Provider<LanguageContribution> provider : providers) {
                if (provider.type().getClassLoader() == loader) {
                    added.add(registerOwned(owner, provider.get()));
                }
            }
        } catch (RuntimeException | LinkageError | ServiceConfigurationError failure) {
            var loadFailure = new IllegalStateException("Unable to load language contributions for plugin " + owner,
                failure);
            for (Registration registration : added) {
                try {
                    removeRegistration(registration);
                } catch (RuntimeException cleanup) {
                    loadFailure.addSuppressed(cleanup);
                }
            }
            throw loadFailure;
        }
    }

    /**
     * Looks up a registered language by its namespaced identity.
     *
     * @param id language to look up
     * @return the contribution, or empty if unregistered
     */
    public synchronized Optional<LanguageContribution> get(LanguageId id) {
        Registration registration = registrations.get(id);
        return registration == null ? Optional.empty() : Optional.of(registration.contribution);
    }

    /**
     * Takes a snapshot of all registered language contributions.
     *
     * @return an immutable list ordered by language ID
     */
    public synchronized List<LanguageContribution> all() {
        return registrations.values().stream().sorted(Comparator.comparing(Registration::id))
            .map(Registration::contribution).toList();
    }

    /**
     * Order is priority, match specificity, language ID, then owner; loading order never matters.
     *
     * @param input file path and bounded content prefix to match
     * @return the highest-ranked matching contribution, or empty if none matches
     */
    public Optional<LanguageContribution> find(LanguageMatchInput input) {
        List<Registration> candidates;
        synchronized (this) {
            candidates = List.copyOf(registrations.values());
        }
        List<Match> matches = new ArrayList<>();
        for (Registration registration : candidates) {
            try {
                int specificity = registration.matcher.match(input);
                if (specificity > 0) {
                    matches.add(new Match(registration, specificity));
                }
            } catch (RuntimeException exception) {
                LoggerFactory.getLogger(LanguageRuntime.class).warn("Language matcher failed: {}",
                    registration.contribution.id(), exception);
            }
        }
        return matches.stream().sorted(Comparator
            .comparingInt((Match match) -> match.registration.priority).reversed()
            .thenComparing(Comparator.comparingInt(Match::specificity).reversed())
            .thenComparing((Match match) -> match.registration.id)
            .thenComparing((Match match) -> match.registration.owner))
            .map(match -> match.registration.contribution).findFirst();
    }

    /**
     * Matches a file using its name and a bounded content prefix. Unreadable content falls back to filename matching.
     *
     * @param path file to recognize
     * @return the highest-ranked matching contribution, or empty if none matches
     */
    public Optional<LanguageContribution> find(Path path) {
        byte[] prefix = new byte[0];
        if (Files.isRegularFile(path)) {
            try (var stream = Files.newInputStream(path)) {
                prefix = stream.readNBytes(LanguageMatchInput.MAX_PREFIX_BYTES);
            } catch (IOException exception) {
                // Filename matching remains available when content cannot be read.
            }
        }
        return find(new LanguageMatchInput(path, prefix));
    }

    /**
     * Returns an active session for the project, language, and dialect, creating one when needed. The runtime owns the
     * returned session.
     *
     * @param project project owning the language services
     * @param id registered language identity
     * @param dialect dialect identifier, or an empty string for the default dialect
     * @return the shared active language session
     * @throws IllegalArgumentException if the language is not registered
     * @throws java.util.concurrent.CancellationException if the runtime is closed or ownership ends during creation
     * @throws IllegalStateException if the created session does not expose the declared capabilities
     */
    public LanguageSession session(Project project, LanguageId id, String dialect) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(dialect, "dialect");
        Registration registration;
        Map<SessionKey, LanguageSession> projectSessions;
        var key = new SessionKey(id, dialect);
        synchronized (this) {
            if (closed)
                throw new CancellationException("Language runtime is closed");
            registration = registrations.get(id);
            if (registration == null)
                throw new IllegalArgumentException("Language is not registered: " + id);
            projectSessions = sessions.computeIfAbsent(project, _ -> new HashMap<>());
            LanguageSession existing = projectSessions.get(key);
            if (existing != null && !existing.isClosed())
                return existing;
        }
        var context = new LanguageSessionContext(project.getPath(), dialect,
            CapabilityRegistry.builder().add(LanguageCapabilities.PROJECT, project).build());
        LanguageSession created = Objects.requireNonNull(registration.contribution.openSession(context),
            "Language contribution returned a null session: " + id);
        if (!created.supportedCapabilities().equals(registration.capabilities)) {
            try {
                created.close();
            } finally {
                throw new IllegalStateException("Language session capabilities differ from their declaration: " + id);
            }
        }
        LanguageSession result = null;
        synchronized (this) {
            if (!closed && registrations.get(id) == registration && sessions.get(project) == projectSessions) {
                result = projectSessions.get(key);
                if (result == null || result.isClosed()) {
                    projectSessions.put(key, created);
                    return created;
                }
            }
        }
        created.close();
        if (result != null)
            return result;
        throw new CancellationException("Language or project was unloaded while opening " + id);
    }

    /**
     * Ends the project's current sessions, including pending creations. A subsequent explicit session
     * lookup reopens ownership; hosts must stop new requests when their project closes. Previously
     * returned capability handles remain permanently cancelled.
     *
     * @param project project whose current sessions should be closed
     */
    public void closeProject(Project project) {
        Map<SessionKey, LanguageSession> removed;
        synchronized (this) {
            removed = sessions.remove(project);
        }
        closeSessions(removed == null ? List.of() : removed.values());
    }

    /**
     * Removes all contributions belonging to an owner and closes their project sessions.
     *
     * @param owner owner identity used during registration
     */
    public void unregisterOwner(String owner) {
        List<Registration> removed;
        synchronized (this) {
            removed = registrations.values().stream().filter(registration -> registration.owner.equals(owner)).toList();
        }
        RuntimeException failure = null;
        for (Registration registration : removed) {
            try {
                removeRegistration(registration);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null)
            throw failure;
    }

    /**
     * Removes a language and closes its project sessions; an unregistered ID has no effect.
     *
     * @param id language to unregister
     */
    public void unregister(LanguageId id) {
        Registration registration;
        synchronized (this) {
            registration = registrations.get(id);
        }
        if (registration != null) {
            removeRegistration(registration);
        }
    }

    private void removeRegistration(Registration registration) {
        List<LanguageSession> removed = new ArrayList<>();
        synchronized (this) {
            if (!registrations.remove(registration.id, registration))
                return;
            registrationRevision++;
            for (Map<SessionKey, LanguageSession> projectSessions : sessions.values()) {
                projectSessions.entrySet().removeIf(entry -> {
                    if (!entry.getKey().id.equals(registration.id))
                        return false;
                    removed.add(entry.getValue());
                    return true;
                });
            }
        }
        closeSessions(removed);
    }

    /**
     * Removes all registrations and closes all project sessions. An open runtime can subsequently accept new
     * registrations.
     */
    public void clear() {
        List<LanguageSession> removed;
        synchronized (this) {
            removed = sessions.values().stream().flatMap(map -> map.values().stream()).toList();
            sessions.clear();
            if (!registrations.isEmpty()) {
                registrationRevision++;
            }
            registrations.clear();
        }
        closeSessions(removed);
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        clear();
    }

    private static void closeSessions(Collection<LanguageSession> sessions) {
        RuntimeException failure = null;
        for (LanguageSession session : sessions) {
            try {
                session.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null)
            throw failure;
    }

    private record Registration(
        String owner,
        LanguageContribution contribution,
        LanguageId id,
        LanguageMatcher matcher,
        int priority,
        Set<CapabilityKey<?>> capabilities
    ) {
    }

    private record SessionKey(LanguageId id, String dialect) {
    }

    private record Match(Registration registration, int specificity) {
    }
}
