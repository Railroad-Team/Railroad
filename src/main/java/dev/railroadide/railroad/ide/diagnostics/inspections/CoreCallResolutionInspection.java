package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.inspections.JavaOverloadResolver.Outcome;
import dev.railroadide.railroad.ide.diagnostics.inspections.JavaOverloadResolver.Resolution;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext.MethodDescriptor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides built-in Java inspections for {@link JavaSemanticRules#UNRESOLVED_CALL} and
 * {@link JavaSemanticRules#AMBIGUOUS_CALL}.
 */
@RegisteredInspection
public final class CoreCallResolutionInspection implements JavaInspectionRuleProvider {
    /**
     * Stable identifier used to register this inspection provider.
     */
    public static final String ID = "railroad:core-call-resolution";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.UNRESOLVED_CALL.id(),
            JavaSemanticRules.UNRESOLVED_CALL.defaultSeverity(),
            JavaSemanticRules.UNRESOLVED_CALL.messageTemplate(),
            Set.of("core", "calls"),
            CoreCallResolutionInspection::reportUnresolvedCalls),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.AMBIGUOUS_CALL.id(),
            JavaSemanticRules.AMBIGUOUS_CALL.defaultSeverity(),
            JavaSemanticRules.AMBIGUOUS_CALL.messageTemplate(),
            Set.of("core", "calls"),
            CoreCallResolutionInspection::reportAmbiguousCalls));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static void reportUnresolvedCalls(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            String kindId = node.kind().id();
            if ("JAVA_METHOD_INVOCATION_EXPRESSION".equals(kindId)) {
                Outcome overload = JavaOverloadResolver.resolve(context, node).outcome();
                if (overload == Outcome.RESOLVED || overload == Outcome.AMBIGUOUS
                    || overload == Outcome.ERRONEOUS_ARGUMENTS)
                    return;
                if (overload == Outcome.UNDETERMINED && (context.resolvedSymbol(node).isPresent()
                    || context.canResolveMethodInvocation(node) || !context.hasKnownMethodOwner(node)))
                    return;

                SyntaxNode memberNode = context.selectorNameNode(node);
                String callName = memberNode == null
                    ? context.firstIdentifierLikeTokenText(node)
                    : context.canonicalQualifiedName(memberNode);
                if (callName == null || callName.isBlank())
                    return;
                Railroad.LOGGER.debug(
                    "Unresolved method invocation detail for {}: {}",
                    callName,
                    context.describeMethodInvocationResolution(node));
                reporter.report(memberNode == null ? node : memberNode, callName);
                return;
            }

            if (!"JAVA_CLASS_INSTANCE_CREATION_EXPRESSION".equals(kindId))
                return;
            if (context.resolvedSymbol(node).isPresent())
                return;
            if (context.canResolveClassInstanceCreation(node))
                return;

            SyntaxNode typeRef = context.directChild(node, "JAVA_TYPE_REFERENCE");
            String typeName = typeRef == null ? null : context.canonicalTypeText(typeRef);
            if (typeName == null || typeName.isBlank())
                return;
            reporter.report(typeRef, typeName);
        });
    }

    private static void reportAmbiguousCalls(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if (!"JAVA_METHOD_INVOCATION_EXPRESSION".equals(node.kind().id()))
                return;
            Resolution resolution = JavaOverloadResolver.resolve(context, node);
            if (resolution.outcome() != Outcome.AMBIGUOUS)
                return;

            SyntaxNode memberNode = context.selectorNameNode(node);
            List<MethodDescriptor> methods = resolution.methods();
            reporter.report(memberNode == null ? node : memberNode, methods.getFirst().name(),
                describe(methods.getFirst()), describe(methods.get(1)));
        });
    }

    private static String describe(MethodDescriptor method) {
        String owner = method.ownerQualifiedName();
        return owner.substring(owner.lastIndexOf('.') + 1).replace('$', '.') + "." + method.name()
            + method.parameterTypes().stream().map(type -> type.displayName())
                .collect(Collectors.joining(", ", "(", ")"));
    }
}
