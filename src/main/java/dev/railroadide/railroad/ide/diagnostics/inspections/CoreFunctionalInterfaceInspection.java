package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CoreFunctionalInterfaceInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-functional-interface";

    private static final List<JavaInspectionRule> RULES = List.of(new SimpleJavaInspectionRule(JavaSemanticRules.INTERFACE_SHOULD_BE_FUNCTIONAL.id(), JavaSemanticRules.INTERFACE_SHOULD_BE_FUNCTIONAL.defaultSeverity(), JavaSemanticRules.INTERFACE_SHOULD_BE_FUNCTIONAL.messageTemplate(), Set.of("core", "interface"), CoreFunctionalInterfaceInspection::reportInterfaceShouldBeFunctional));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static void reportInterfaceShouldBeFunctional(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode node : context.nodesOfKind(JavaSyntaxKinds.INTERFACE_DECLARATION.id())) {
            Symbol declaredName = context.declaredSymbol(node).orElse(null);
            if (declaredName == null) continue;

            String qualifiedName = declaredName.qualifiedName().orElse(null);
            if (qualifiedName == null) continue;

            if (hasAnnotation(context, node, "java.lang.FunctionalInterface")) continue;

            int abstractMethodCount = 0;
            Set<String> seenMethods = new HashSet<>();
            for (JavaRuleContext.MethodDescriptor descriptor : context.declaredMethodDescriptors(qualifiedName)) {
                String signatureKey = descriptor.signatureKey();
                seenMethods.add(signatureKey);
                if (!descriptor.isAbstract() || isObjectMethod(descriptor)) continue;
                abstractMethodCount++;
                if (abstractMethodCount > 1) break;
            }

            for (JavaRuleContext.MethodDescriptor descriptor : context.inheritedMethodDescriptors(qualifiedName)) {
                String signatureKey = descriptor.signatureKey();
                if (!descriptor.isAbstract() || isObjectMethod(descriptor) || seenMethods.contains(signatureKey))
                    continue;
                seenMethods.add(signatureKey);
                abstractMethodCount++;
                if (abstractMethodCount > 1) break;
            }

            if (abstractMethodCount == 1) reporter.report(node, declaredName.simpleName());
        }
    }

    private static boolean hasAnnotation(JavaRuleContext context, SyntaxNode node, String qualifiedAnnotationName) {
        String simpleName = qualifiedAnnotationName.contains(".")
                ? qualifiedAnnotationName.substring(qualifiedAnnotationName.lastIndexOf('.') + 1)
                : qualifiedAnnotationName;

        for (SyntaxNode child : node.children()) {
            if (!JavaSyntaxKinds.ANNOTATION.id().equals(child.kind().id())) continue;

            SyntaxNode qualifiedNameNode = context.directChild(child, JavaSyntaxKinds.QUALIFIED_NAME.id());
            if (qualifiedNameNode == null) continue;

            String name = context.canonicalQualifiedName(qualifiedNameNode);
            if (qualifiedAnnotationName.equals(name) || simpleName.equals(name)) return true;
        }
        return false;
    }

    private static boolean isObjectMethod(JavaRuleContext.MethodDescriptor descriptor) {
        List<Type> parameters = descriptor.parameterTypes();

        return switch (descriptor.name()) {
            case "toString" -> parameters.isEmpty() && "java.lang.String".equals(descriptor.returnType().displayName());
            case "hashCode" -> parameters.isEmpty() && "int".equals(descriptor.returnType().displayName());
            case "equals" ->
                parameters.size() == 1 && "boolean".equals(descriptor.returnType().displayName()) && "java.lang.Object".equals(parameters.getFirst().displayName());
            default -> false;
        };
    }
}
