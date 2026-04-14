package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CoreThisReferenceEscapedObjectConstructionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-this-reference-escaped-object-construction";

    private static final Set<String> KNOWN_COLLECTION_MODIFYING_METHODS = Set.of(
        "add", "addAll", "put", "putAll", "offer", "offerFirst", "offerLast", "push", "set", "insert", "append",
        "remove", "removeAll", "clear", "poll", "pollFirst", "pollLast", "pop",
        "addIfAbsent", "compute", "computeIfAbsent", "computeIfPresent", "merge", "replace", "replaceAll",
        "retainAll", "removeIf"
    );

    private static final Set<String> KNOWN_PUBLISHING_METHOD_NAMES = Set.of(
        "register", "subscribe", "attach", "listen", "execute", "enqueue", "submit", "start", "run", "schedule",
        "invokeLater", "post", "send", "dispatch", "store", "save", "persist", "write"
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION.id(),
                JavaSemanticRules.THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION.defaultSeverity(),
                JavaSemanticRules.THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION.messageTemplate(),
                Set.of("core", "bug-risk"),
                CoreThisReferenceEscapedObjectConstructionInspection::reportThisReferenceEscapedObjectConstruction
            )
        );
    }

    private static void reportThisReferenceEscapedObjectConstruction(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode constructorNode : context.nodesOfKinds(JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id(), JavaSyntaxKinds.RECORD_COMPACT_CONSTRUCTOR.id())) {
            SyntaxNode body = context.directChild(constructorNode, JavaSyntaxKinds.BLOCK.id());
            if (body == null)
                continue;

            context.traverseDescendants(body, node -> {
                if (JavaSyntaxKinds.THIS_EXPRESSION.id().equals(node.kind().id())) {
                    String escapeSite = escapeSiteDescription(context, node);
                    if (escapeSite != null) {
                        reporter.report(node, escapeSite);
                    }

                    return;
                }

                if (Objects.equals(node.kind().id(), JavaSyntaxKinds.LAMBDA_EXPRESSION.id())
                    && lambdaContainsThis(context, node)) {
                    String escapeSite = lambdaEscapeSiteDescription(context, node);
                    if (escapeSite != null) {
                        reporter.report(node, escapeSite);
                    }
                }
            });
        }
    }

    private static String escapeSiteDescription(JavaRuleContext context, SyntaxNode thisNode) {
        SyntaxNode parentNode = thisNode.parent().orElse(null);
        if (parentNode == null)
            return null;

        String kindId = parentNode.kind().id();
        if (Objects.equals(kindId, JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id())) {
            if (isLhs(context, thisNode, parentNode))
                return null;

            return "assignment";
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.ARGUMENT_LIST.id()))
            return publishingEscapeSiteDescription(context, parentNode);

        if (Objects.equals(kindId, JavaSyntaxKinds.LAMBDA_EXPRESSION.id()))
            return lambdaEscapeSiteDescription(context, parentNode);

        return null;
    }

    private static boolean isLhs(JavaRuleContext context, SyntaxNode node, SyntaxNode assignmentNode) {
        SyntaxNode lhs = context.firstDirectExpressionChild(assignmentNode);
        return lhs == null || Objects.equals(lhs, node);
    }

    private static String publishingEscapeSiteDescription(JavaRuleContext context, SyntaxNode parentNode) {
        SyntaxNode callNode = parentNode.parent().orElse(null);
        if (callNode == null)
            return null;

        String kindId = callNode.kind().id();
        if (!Objects.equals(kindId, JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())
            && !Objects.equals(kindId, JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id()))
            return null;

        Symbol targetSymbol = context.resolvedSymbol(callNode).orElse(null);
        SyntaxNode selectorNode = context.selectorNameNode(callNode);
        String name = targetSymbol == null
            ? context.firstIdentifierLikeTokenText(selectorNode == null ? callNode : selectorNode)
            : targetSymbol.simpleName();
        String owner = targetSymbol == null ? null : context.ownerQualifiedName(targetSymbol).orElse(null);
        String receiverType = receiverTypeName(context, callNode);
        SyntaxNode typeNode = context.directChild(callNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
        String typeName = typeNode == null ? null : context.resolveQualifiedTypeName(typeNode);

        if (Objects.equals(kindId, JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id())
            && ("java.lang.Thread".equals(owner) || "java.lang.Thread".equals(typeName) || "Thread".equals(name)))
            return "thread";

        if (name != null && isKnownPublishingMethod(context, owner, name))
            return name;

        String collectionOwner = receiverType != null ? receiverType : owner == null ? typeName : owner;
        if (name != null && KNOWN_COLLECTION_MODIFYING_METHODS.contains(name) && isCollectionLike(context, collectionOwner))
            return name;

        return null;
    }

    private static String lambdaEscapeSiteDescription(JavaRuleContext context, SyntaxNode lambdaNode) {
        SyntaxNode parentNode = lambdaNode.parent().orElse(null);
        if (parentNode == null)
            return null;

        String kindId = parentNode.kind().id();
        if (Objects.equals(kindId, JavaSyntaxKinds.ARGUMENT_LIST.id())) {
            String site = publishingEscapeSiteDescription(context, parentNode);
            return site == null ? null : "lambda passed to '%s'".formatted(site);
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id())) {
            if (isLhs(context, lambdaNode, parentNode))
                return null;
            return "assignment";
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.RETURN_STATEMENT.id()))
            return "return";

        return null;
    }

    private static String receiverTypeName(JavaRuleContext context, SyntaxNode callNode) {
        SyntaxNode receiver = context.explicitReceiver(callNode);
        if (receiver == null)
            return null;

        return context.inferredType(receiver)
            .map(type -> context.resolveQualifiedTypeName(type.displayName()))
            .orElse(null);
    }

    private static boolean lambdaContainsThis(JavaRuleContext context, SyntaxNode lambdaNode) {
        SyntaxNode body = context.directChild(lambdaNode, JavaSyntaxKinds.LAMBDA_BODY.id());
        if (body == null)
            return false;

        return doesSubtreeContainThisOutsideOfNestedScopes(body);
    }

    private static boolean doesSubtreeContainThisOutsideOfNestedScopes(SyntaxNode node) {
        String kindId = node.kind().id();
        if (Objects.equals(kindId, JavaSyntaxKinds.THIS_EXPRESSION.id()))
            return true;

        if (Objects.equals(kindId, JavaSyntaxKinds.LAMBDA_EXPRESSION.id())
            || Objects.equals(kindId, JavaSyntaxKinds.ANONYMOUS_CLASS_BODY.id()))
            return false; // 'this' references inside nested lambdas or anonymous classes do not escape the constructor, as they capture the correct 'this' context.

        for (SyntaxNode child : node.children()) {
            if (doesSubtreeContainThisOutsideOfNestedScopes(child))
                return true;
        }

        return false;
    }

    private static boolean isKnownPublishingMethod(JavaRuleContext context, String owner, String name) {
        if (isCollectionLike(context, owner) && KNOWN_COLLECTION_MODIFYING_METHODS.contains(name))
            return true;

        return KNOWN_PUBLISHING_METHOD_NAMES.contains(name);
    }

    private static boolean isCollectionLike(JavaRuleContext context, String qualifiedTypeName) {
        if (qualifiedTypeName == null || qualifiedTypeName.isBlank())
            return false;

        return context.isSubtype(qualifiedTypeName, "java.util.Collection")
            || context.isSubtype(qualifiedTypeName, "java.util.Map")
            || qualifiedTypeName.contains("List")
            || qualifiedTypeName.contains("Set")
            || qualifiedTypeName.contains("Queue")
            || qualifiedTypeName.contains("Deque")
            || qualifiedTypeName.contains("Stack")
            || qualifiedTypeName.contains("Map")
            || qualifiedTypeName.contains("Multimap")
            || qualifiedTypeName.contains("Table")
            || qualifiedTypeName.contains("Graph")
            || qualifiedTypeName.contains("Cache")
            || qualifiedTypeName.contains("Buffer")
            || qualifiedTypeName.contains("Collection");
    }
}
