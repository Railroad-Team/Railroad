package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.document.api.ContentKind;
import java.util.Set;

/**
 * Plugin-owned language registration. Descriptor methods must return stable immutable values for
 * the registration lifetime. Implementations must be safe for concurrent discovery and session creation.
 * Session services must expose public interface contracts and support concurrent request invocation.
 */
public interface LanguageContribution {
    /**
     * Identifies this language across its dialects and sessions.
     *
     * @return the stable namespaced language identity
     */
    LanguageId id();

    /**
     * Names this language for display in the host.
     *
     * @return the nonblank display name
     */
    String displayName();

    /**
     * Supplies recognition rules for file names and bounded content prefixes.
     *
     * @return the stable language matcher
     */
    LanguageMatcher matcher();

    /**
     * Declares the document representation used by this language.
     *
     * @return the content kind; text by default
     */
    default ContentKind contentKind() {
        return ContentKind.TEXT;
    }

    /**
     * Declares optional contracts before a project session is created.
     *
     * @return the immutable capability keys that every opened session must expose
     */
    default Set<CapabilityKey<?>> supportedCapabilities() {
        return Set.of();
    }

    /**
     * Sets precedence among matching language contributions before match specificity is compared.
     *
     * @return the priority, with larger values preferred; zero by default
     */
    default int priority() {
        return 0;
    }

    /**
     * Opens one project/dialect session; the runtime owns and disposes the returned session.
     *
     * @param context captured project location, dialect, and host services
     * @return a new session exposing exactly the declared capabilities
     */
    LanguageSession openSession(LanguageSessionContext context);
}
