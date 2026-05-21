package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@RegisteredInspection
public class CoreOverlyStrongTypeCastInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-overly-strong-type-cast";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.OVERLY_STRONG_TYPE_CAST.id(),
                JavaSemanticRules.OVERLY_STRONG_TYPE_CAST.defaultSeverity(),
                JavaSemanticRules.OVERLY_STRONG_TYPE_CAST.messageTemplate(),
                Set.of("core", "type-safety"),
                CoreOverlyStrongTypeCastInspection::reportOverlyStrongTypeCasts
            )
        );
    }

    private static void reportOverlyStrongTypeCasts(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode invocationNode : context.nodesOfKind(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            SyntaxNode castNode = context.unwrapTransparentExpression(context.explicitReceiver(invocationNode));
            if (castNode == null || !JavaSyntaxKinds.CAST_EXPRESSION.id().equals(castNode.kind().id()))
                continue;

            SyntaxNode castTypeNode = context.directChild(castNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (castTypeNode == null)
                continue;

            String castTypeName = context.resolveQualifiedTypeName(castTypeNode);
            if (castTypeName == null || castTypeName.isBlank())
                continue;

            reportOverlyStrongReceiverCastForMethodInvocation(context, reporter, castNode, invocationNode, castTypeName);
        }

        for (SyntaxNode fieldAccessNode : context.nodesOfKind(JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id())) {
            SyntaxNode castNode = context.unwrapTransparentExpression(context.explicitReceiver(fieldAccessNode));
            if (castNode == null || !JavaSyntaxKinds.CAST_EXPRESSION.id().equals(castNode.kind().id()))
                continue;

            SyntaxNode castTypeNode = context.directChild(castNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (castTypeNode == null)
                continue;

            String castTypeName = context.resolveQualifiedTypeName(castTypeNode);
            if (castTypeName == null || castTypeName.isBlank())
                continue;

            reportOverlyStrongReceiverCastForFieldAccess(context, reporter, castNode, fieldAccessNode, castTypeName);
        }
    }

    private static void reportOverlyStrongReceiverCastForMethodInvocation(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode castNode, SyntaxNode invocationNode, String castTypeName) {
        SyntaxNode selectorNode = context.selectorNameNode(invocationNode);
        if (selectorNode == null)
            return;

        String methodName = context.firstIdentifierLikeTokenText(selectorNode);
        if (methodName == null || methodName.isBlank())
            return;

        int argumentCount = 0;
        SyntaxNode argumentList = context.directChild(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
        if (argumentList != null)
            argumentCount = context.directExpressionChildren(argumentList).size();

        String weakerType = findWeakerTypeDeclaringMethod(context, castTypeName, methodName, argumentCount);
        if (weakerType == null)
            return;

        reporter.report(castNode, context.simpleTypeName(castTypeName), context.simpleTypeName(weakerType));
    }

    private static void reportOverlyStrongReceiverCastForFieldAccess(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode castNode, SyntaxNode invocationNode, String castTypeName) {
        SyntaxNode selectorNode = context.selectorNameNode(invocationNode);
        if (selectorNode == null)
            return;

        String fieldName = context.firstIdentifierLikeTokenText(selectorNode);
        if (fieldName == null || fieldName.isBlank())
            return;

        String weakerType = findWeakerTypeDeclaringField(context, castTypeName, fieldName);
        if (weakerType == null)
            return;

        reporter.report(castNode, context.simpleTypeName(castTypeName), context.simpleTypeName(weakerType));
    }

    private static @Nullable String findWeakerTypeDeclaringMethod(JavaRuleContext context, String castTypeName, String methodName, int argumentCount) {
        for (String candidate : superTypesOf(context, castTypeName)) {
            if (candidate.equals(castTypeName))
                continue;

            if (declaresMethod(context, candidate, methodName, argumentCount))
                return candidate;
        }

        return null;
    }

    private static @Nullable String findWeakerTypeDeclaringField(JavaRuleContext context, String castTypeName, String fieldName) {
        for (String candidate : superTypesOf(context, castTypeName)) {
            if (candidate.equals(castTypeName))
                continue;

            if (declaresField(context, candidate, fieldName))
                return candidate;
        }

        return null;
    }

    private static List<String> superTypesOf(JavaRuleContext context, String typeName) {
        List<String> superTypes = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(typeName);
        visited.add(typeName);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            superTypes.add(current);

            for (String superType : context.directSuperTypeNames(current)) {
                if (visited.add(superType)) {
                    queue.addLast(superType);
                }
            }
        }

        return List.copyOf(superTypes);
    }

    private static boolean declaresMethod(JavaRuleContext context, String ownerTypeName, String methodName, int argumentCount) {
        for (JavaRuleContext.MethodDescriptor method : context.declaredMethodDescriptors(ownerTypeName)) {
            // TODO: Check that the types of the parameters are compatible, not just the count
            if (method.name().equals(methodName) && method.parameterTypes().size() == argumentCount)
                return true;
        }

        for (JavaRuleContext.MethodDescriptor method : context.inheritedMethodDescriptors(ownerTypeName)) {
            // TODO: Check that the types of the parameters are compatible, not just the count
            if (method.name().equals(methodName) && method.parameterTypes().size() == argumentCount)
                return true;
        }

        return false;
    }

    private static boolean declaresField(JavaRuleContext context, String ownerTypeName, String fieldName) {
        for (JavaRuleContext.FieldDescriptor field : context.declaredFieldDescriptors(ownerTypeName)) {
            if (field.name().equals(fieldName))
                return true;
        }

        for (JavaRuleContext.FieldDescriptor field : context.inheritedFieldDescriptors(ownerTypeName)) {
            if (field.name().equals(fieldName))
                return true;
        }

        return false;
    }
}
