package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RegisteredInspection
public class CoreFeatureEnvyInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-feature-envy";

    private static final Set<String> CALLABLE_KINDS = Set.of(
        JavaSyntaxKinds.METHOD_DECLARATION.id(),
        JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id(),
        JavaSyntaxKinds.RECORD_COMPACT_CONSTRUCTOR.id()
    );

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.FEATURE_ENVY_MANIPULATE.id(),
            JavaSemanticRules.FEATURE_ENVY_MANIPULATE.defaultSeverity(),
            JavaSemanticRules.FEATURE_ENVY_MANIPULATE.messageTemplate(),
            Set.of("core", "feature-envy"),
            CoreFeatureEnvyInspection::reportFeatureEnvyManipulate
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.FEATURE_ENVY_TIGHTLY_COUPLED.id(),
            JavaSemanticRules.FEATURE_ENVY_TIGHTLY_COUPLED.defaultSeverity(),
            JavaSemanticRules.FEATURE_ENVY_TIGHTLY_COUPLED.messageTemplate(),
            Set.of("core", "feature-envy"),
            CoreFeatureEnvyInspection::reportFeatureEnvyTightlyCoupled
        )
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static void reportFeatureEnvyManipulate(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode node : context.nodesOfKinds(CALLABLE_KINDS)) {
            Symbol enclosingType = context.enclosingTypeSymbol(node).orElse(null);
            if (enclosingType == null)
                continue;

            String hostQualifiedName = enclosingType.qualifiedName().orElse(null);
            if (hostQualifiedName == null)
                continue;

            Map<String, Integer> externalAccessCounts = new HashMap<>();
            var internalAccessCount = new AtomicInteger();

            context.traverseDescendants(node, descendant -> {
                if (context.isSelectorNameExpression(descendant))
                    return;

                Symbol symbol = context.resolvedSymbol(descendant).orElse(null);
                if (symbol == null || (symbol.kind() != SymbolKind.FIELD && symbol.kind() != SymbolKind.METHOD))
                    return;

                if (Modifier.isStatic(context.symbolModifiers(symbol)))
                    return;

                String ownerName = context.ownerQualifiedName(symbol).orElse(null);
                if (ownerName == null || isLibraryType(context, ownerName))
                    return;

                if (ownerName.equals(hostQualifiedName)) {
                    internalAccessCount.getAndIncrement();
                } else {
                    externalAccessCounts.merge(ownerName, 1, Integer::sum);
                }
            });

            String mostEnviedType = externalAccessCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

            if (mostEnviedType != null) {
                int maxExternal = externalAccessCounts.get(mostEnviedType);
                // Report if we touch that specific class more than our own data, with a minimum hit threshold
                if (maxExternal > internalAccessCount.get() && maxExternal >= 3) {
                    String enviedClassName = context.simpleTypeName(mostEnviedType);
                    String methodName = context.declaredSymbol(node).map(Symbol::simpleName).orElse("unknown");
                    reporter.report(node, methodName, enviedClassName, enviedClassName);
                }
            }
        }
    }

    private static void reportFeatureEnvyTightlyCoupled(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode node : context.nodesOfKinds(CALLABLE_KINDS)) {
            Symbol enclosingType = context.enclosingTypeSymbol(node).orElse(null);
            if (enclosingType == null)
                continue;

            String hostQualifiedName = enclosingType.qualifiedName().orElse(null);
            if (hostQualifiedName == null)
                continue;

            Map<String, Set<String>> externalMembersAccessed = new HashMap<>();
            context.traverseDescendants(node, descendant -> {
                if (context.isSelectorNameExpression(descendant))
                    return;

                Symbol symbol = context.resolvedSymbol(descendant).orElse(null);
                if (symbol == null || (symbol.kind() != SymbolKind.FIELD && symbol.kind() != SymbolKind.METHOD))
                    return;

                if (Modifier.isStatic(context.symbolModifiers(symbol)))
                    return;

                String ownerName = context.ownerQualifiedName(symbol).orElse(null);
                if (ownerName == null || ownerName.equals(hostQualifiedName) || isLibraryType(context, ownerName))
                    return;

                externalMembersAccessed.computeIfAbsent(ownerName, $ -> new HashSet<>())
                    .add(symbol.simpleName());
            });

            for (Map.Entry<String, Set<String>> entry : externalMembersAccessed.entrySet()) {
                if (entry.getValue().size() >= 3) {
                    String enviedClassName = context.simpleTypeName(entry.getKey());
                    String methodName = context.declaredSymbol(node).map(Symbol::simpleName).orElse("unknown");
                    reporter.report(node, methodName, enviedClassName, enviedClassName);
                    break;
                }
            }
        }
    }

    private static boolean isLibraryType(JavaRuleContext context, String qualifiedName) {
        return qualifiedName.startsWith("java.")
            || qualifiedName.startsWith("javax.")
            || context.jdkQualifiedTypeNames().contains(qualifiedName);
    }
}
