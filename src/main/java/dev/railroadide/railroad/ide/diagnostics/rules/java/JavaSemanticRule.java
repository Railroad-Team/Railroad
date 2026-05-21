package dev.railroadide.railroad.ide.diagnostics.rules.java;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

import java.util.Objects;

/**
 * Canonical Java semantic rule definition.
 */
public record JavaSemanticRule(
        String id,
        SemanticDiagnostic.Severity defaultSeverity,
        String messageTemplate
) {
    public JavaSemanticRule {
        id = Objects.requireNonNull(id, "id");
        defaultSeverity = Objects.requireNonNull(defaultSeverity, "defaultSeverity");
        messageTemplate = Objects.requireNonNull(messageTemplate, "messageTemplate");
        if (id.isBlank())
            throw new IllegalArgumentException("id cannot be blank");
        if (messageTemplate.isBlank())
            throw new IllegalArgumentException("messageTemplate cannot be blank");
    }

    public String format(Object... args) {
        return String.format(messageTemplate, args);
    }
}
