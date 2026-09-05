package dev.railroadide.railroad.ide.diagnostics.rules.java;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

import java.util.Objects;

/**
 * Canonical Java semantic rule definition.
 *
 * @param id stable rule or provider identifier
 * @param defaultSeverity severity used without an override
 * @param messageTemplate format string used to build diagnostic messages
 */
public record JavaSemanticRule(
    String id,
    SemanticDiagnostic.Severity defaultSeverity,
    String messageTemplate
) {
    /**
     * Creates validated rule metadata with a nonblank identifier and message template.
     *
     * @param id stable rule or provider identifier
     * @param defaultSeverity severity used without an override
     * @param messageTemplate format string used to build diagnostic messages
     */
    public JavaSemanticRule {
        id = Objects.requireNonNull(id, "id");
        defaultSeverity = Objects.requireNonNull(defaultSeverity, "defaultSeverity");
        messageTemplate = Objects.requireNonNull(messageTemplate, "messageTemplate");
        if (id.isBlank())
            throw new IllegalArgumentException("id cannot be blank");
        if (messageTemplate.isBlank())
            throw new IllegalArgumentException("messageTemplate cannot be blank");
    }

    /**
     * Formats this rule's diagnostic message using the supplied arguments.
     *
     * @param args arguments substituted into the message template
     * @return formatted diagnostic message
     */
    public String format(Object... args) {
        return String.format(messageTemplate, args);
    }
}
