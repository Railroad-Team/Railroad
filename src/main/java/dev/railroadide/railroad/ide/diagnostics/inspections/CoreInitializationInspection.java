package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.lang.reflect.Modifier;
import java.util.*;

@RegisteredInspection
public final class CoreInitializationInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-initialization-inspection";

    private static final Set<String> CONSTRUCTOR_KINDS = Set.of(
        JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id(),
        JavaSyntaxKinds.RECORD_COMPACT_CONSTRUCTOR.id()
    );

    private static final Set<String> TYPE_DECLARATION_KINDS = Set.of(
        JavaSyntaxKinds.CLASS_DECLARATION.id(),
        JavaSyntaxKinds.INTERFACE_DECLARATION.id(),
        JavaSyntaxKinds.ENUM_DECLARATION.id(),
        JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION.id(),
        JavaSyntaxKinds.RECORD_DECLARATION.id()
    );

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.OVERRIDABLE_METHOD_DURING_CONSTRUCTION.id(),
            JavaSemanticRules.OVERRIDABLE_METHOD_DURING_CONSTRUCTION.defaultSeverity(),
            JavaSemanticRules.OVERRIDABLE_METHOD_DURING_CONSTRUCTION.messageTemplate(),
            Set.of("core", "initialization"),
            CoreInitializationInspection::reportOverridableMethodDuringConstruction
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.OVERRIDDEN_METHOD_DURING_CONSTRUCTION.id(),
            JavaSemanticRules.OVERRIDDEN_METHOD_DURING_CONSTRUCTION.defaultSeverity(),
            JavaSemanticRules.OVERRIDDEN_METHOD_DURING_CONSTRUCTION.messageTemplate(),
            Set.of("core", "initialization"),
            CoreInitializationInspection::reportOverriddenMethodDuringConstruction
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

    private static void reportOverridableMethodDuringConstruction(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode methodInvocationNode : context.nodesOfKind(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            findThisConstructorCall(context, methodInvocationNode)
                .ifPresent(callSite -> reporter.report(callSite.invocationNode(), callSite.methodSymbol().simpleName()));
        }
    }

    private static void reportOverriddenMethodDuringConstruction(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        Map<String, List<String>> subtypeIndex = buildSubtypeIndex(context);
        for (SyntaxNode methodInvocationNode : context.nodesOfKind(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            Optional<CallSite> maybeCall = findThisConstructorCall(context, methodInvocationNode);
            if (maybeCall.isEmpty())
                continue;

            CallSite callSite = maybeCall.get();
            Set<String> descendants = collectTransitiveSubtypes(subtypeIndex, callSite.currentType());
            if (descendants.isEmpty())
                continue;

            boolean reported = false;
            for (String subtype : descendants) {
                for (JavaRuleContext.MethodDescriptor descriptor : context.declaredMethodDescriptors(subtype)) {
                    if (!descriptor.signatureKey().equals(callSite.signatureKey()))
                        continue;

                    if (descriptor.symbol() == null)
                        continue;

                    int modifiers = descriptor.modifiers();
                    if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers))
                        continue;

                    reporter.report(callSite.invocationNode(), callSite.methodSymbol().simpleName());
                    reported = true;
                    break;
                }

                if (reported)
                    break;
            }
        }
    }

    private static Optional<CallSite> findThisConstructorCall(JavaRuleContext context, SyntaxNode invocationNode) {
        SyntaxNode enclosingCallable = context.nearestEnclosingCallableOrLambda(invocationNode);
        if (enclosingCallable == null || !CONSTRUCTOR_KINDS.contains(enclosingCallable.kind().id()))
            return Optional.empty();

        SyntaxNode receiver = context.explicitReceiver(invocationNode);
        if (receiver != null) {
            String receiverKind = receiver.kind().id();
            if (JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(receiverKind))
                return Optional.empty();

            if (!JavaSyntaxKinds.THIS_EXPRESSION.id().equals(receiverKind))
                return Optional.empty();
        }

        Symbol methodSymbol = context.resolvedSymbol(invocationNode).orElse(null);
        if (methodSymbol == null)
            return Optional.empty();

        int modifiers = context.symbolModifiers(methodSymbol);
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers))
            return Optional.empty();

        String ownerQualifiedName = context.ownerQualifiedName(methodSymbol).orElse(null);
        if (ownerQualifiedName == null || context.isFinalType(ownerQualifiedName))
            return Optional.empty();

        Optional<Symbol> enclosingType = context.enclosingTypeSymbol(enclosingCallable);
        String currentType = enclosingType.flatMap(Symbol::qualifiedName).orElse(null);
        if (currentType == null || currentType.isBlank())
            return Optional.empty();

        JavaRuleContext.MethodDescriptor descriptor = findMethodDescriptor(context, ownerQualifiedName, methodSymbol);
        if (descriptor == null)
            return Optional.empty();

        return Optional.of(new CallSite(currentType, invocationNode, methodSymbol, ownerQualifiedName, descriptor.signatureKey()));
    }

    private static JavaRuleContext.MethodDescriptor findMethodDescriptor(JavaRuleContext context, String ownerQualifiedName, Symbol methodSymbol) {
        for (JavaRuleContext.MethodDescriptor descriptor : context.declaredMethodDescriptors(ownerQualifiedName)) {
            if (methodSymbol.equals(descriptor.symbol()))
                return descriptor;
        }

        return null;
    }

    private static Map<String, List<String>> buildSubtypeIndex(JavaRuleContext context) {
        Map<String, List<String>> directSubtypes = new LinkedHashMap<>();
        for (SyntaxNode typeNode : context.nodesOfKinds(TYPE_DECLARATION_KINDS)) {
            Symbol symbol = context.declaredSymbol(typeNode).orElse(null);
            if (symbol == null)
                continue;

            String qualifiedName = symbol.qualifiedName().orElse(null);
            if (qualifiedName == null)
                continue;

            List<String> supers = collectDirectSuperTypes(context, typeNode);
            for (String superType : supers) {
                directSubtypes.computeIfAbsent(superType, ignored -> new ArrayList<>()).add(qualifiedName);
            }
        }

        return directSubtypes;
    }

    private static List<String> collectDirectSuperTypes(JavaRuleContext context, SyntaxNode declaration) {
        List<String> supers = new ArrayList<>();
        addSuperTypeReferences(context, context.directChild(declaration, JavaSyntaxKinds.EXTENDS_CLAUSE.id()), supers);
        addSuperTypeReferences(context, context.directChild(declaration, JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id()), supers);
        return supers;
    }

    private static void addSuperTypeReferences(JavaRuleContext context, SyntaxNode clause, List<String> out) {
        if (clause == null)
            return;

        for (SyntaxNode child : clause.children()) {
            if (!JavaSyntaxKinds.TYPE_REFERENCE.id().equals(child.kind().id()))
                continue;

            String qualified = context.resolveQualifiedTypeName(child);
            if (qualified != null && !qualified.isBlank()) {
                out.add(qualified);
            }
        }
    }

    private static Set<String> collectTransitiveSubtypes(Map<String, List<String>> subtypeIndex, String base) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(base);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            List<String> direct = subtypeIndex.get(current);
            if (direct == null)
                continue;

            for (String candidate : direct) {
                if (result.add(candidate)) {
                    queue.addLast(candidate);
                }
            }
        }

        result.remove(base);
        return result;
    }

    private record CallSite(
        String currentType,
        SyntaxNode invocationNode,
        Symbol methodSymbol,
        String ownerQualifiedName,
        String signatureKey
    ) {
    }
}
