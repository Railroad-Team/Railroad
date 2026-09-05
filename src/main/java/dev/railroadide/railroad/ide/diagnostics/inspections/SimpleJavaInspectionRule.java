package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.Objects;
import java.util.Set;

/**
 * An inspection rule backed by immutable metadata and an evaluation callback.
 */
public final class SimpleJavaInspectionRule implements JavaInspectionRule {
    /**
     * Evaluates a rule and reports findings through its reporter.
     */
    @FunctionalInterface
    public interface Evaluator {
        /**
         * Evaluates the inspection against the supplied source context.
         *
         * @param context source and semantic context to inspect
         * @param reporter destination for reported diagnostics
         */
        void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter);
    }

    private final String id;
    private final SemanticDiagnostic.Severity severity;
    private final String template;
    private final Set<String> tags;
    private final Evaluator evaluator;

    /**
     * Creates an inspection rule, taking an immutable copy of its tags.
     *
     * @param id stable rule or provider identifier
     * @param severity default diagnostic severity
     * @param template format string for diagnostic messages
     * @param tags categories used to group and enable the rule
     * @param evaluator callback that evaluates this rule
     */
    public SimpleJavaInspectionRule(
        String id,
        SemanticDiagnostic.Severity severity,
        String template,
        Set<String> tags,
        Evaluator evaluator
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.template = Objects.requireNonNull(template, "template");
        this.tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public SemanticDiagnostic.Severity defaultSeverity() {
        return severity;
    }

    @Override
    public String messageTemplate() {
        return template;
    }

    @Override
    public Set<String> tags() {
        return tags;
    }

    @Override
    public void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        evaluator.evaluate(context, reporter);
    }
}
