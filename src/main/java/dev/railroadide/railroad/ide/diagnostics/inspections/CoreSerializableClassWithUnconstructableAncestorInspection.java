package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.classparser.stub.ConstructorStub;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.lang.reflect.Modifier;
import java.util.*;

@RegisteredInspection(id = CoreSerializableClassWithUnconstructableAncestorInspection.ID)
public class CoreSerializableClassWithUnconstructableAncestorInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-serializable-class-with-unconstructable-ancestor";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR.id(),
                JavaSemanticRules.SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR.defaultSeverity(),
                JavaSemanticRules.SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR.messageTemplate(),
                Set.of("core", "serialization"),
                CoreSerializableClassWithUnconstructableAncestorInspection::reportSerializableClassWithUnconstructableAncestor
            )
        );
    }

    private static void reportSerializableClassWithUnconstructableAncestor(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        Map<String, SyntaxNode> localTypeDeclarations = localTypeDeclarations(context);
        for (SyntaxNode classNode : context.nodesOfKind(JavaSyntaxKinds.CLASS_DECLARATION.id())) {
            Symbol classSymbol = context.declaredSymbol(classNode).orElse(null);
            if (classSymbol == null)
                continue;

            String qualifiedClassName = classSymbol.qualifiedName().orElse(null);
            if (qualifiedClassName == null || !context.isSubtype(qualifiedClassName, "java.io.Serializable"))
                continue;

            String ancestor = firstNonSerializableAncestor(context, qualifiedClassName);
            if (ancestor == null)
                continue;

            if (hasAccessibleNoArgConstructor(context, ancestor, localTypeDeclarations))
                continue;

            reporter.report(classNode, classSymbol.simpleName(), context.simpleTypeName(ancestor));
        }
    }

    private static boolean hasAccessibleNoArgConstructor(JavaRuleContext context, String ancestorQualifiedName, Map<String, SyntaxNode> localTypeDeclarations) {
        SyntaxNode localDeclaration = localTypeDeclarations.get(ancestorQualifiedName);
        if (localDeclaration != null)
            return hasAccessibleLocalNoArgConstructor(context, localDeclaration, ancestorQualifiedName);

        ClassStub stub = context.jdkClassStubsByQualifiedName().get(ancestorQualifiedName);
        if (stub == null)
            // TODO: We need to handle 3rd party libs here
            return true; // unknown type

        if (stub.constructors().isEmpty())
            return true;

        for (ConstructorStub constructor : stub.constructors()) {
            if (constructor.parameters().isEmpty() && isAccessibleFromHere(context, ancestorQualifiedName, constructor.modifiers()))
                return true;
        }

        return false;
    }

    private static boolean hasAccessibleLocalNoArgConstructor(JavaRuleContext context, SyntaxNode typeNode, String ownerQualifiedName) {
        List<SyntaxNode> constructors = new ArrayList<>();
        context.traverseDescendants(typeNode, node -> {
            if (!Objects.equals(JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id(), node.kind().id()))
                return;

            String enclosing = context.enclosingTypeSymbol(node).flatMap(Symbol::qualifiedName).orElse(null);
            if (Objects.equals(enclosing, ownerQualifiedName))
                constructors.add(node);
        });

        if (constructors.isEmpty())
            return true; // implicit default constructor

        for (SyntaxNode constructor : constructors) {
            SyntaxNode parameterList = context.directChild(constructor, JavaSyntaxKinds.PARAMETER_LIST.id());
            int parameterCount = 0;
            if (parameterList != null) {
                for (SyntaxNode child : parameterList.children()) {
                    if (Objects.equals(JavaSyntaxKinds.PARAMETER.id(), child.kind().id()))
                        parameterCount++;
                }
            }

            if (parameterCount == 0 && isAccessibleFromHere(context, ownerQualifiedName, modifierBits(context, constructor)))
                return true;
        }

        return false;
    }

    private static int modifierBits(JavaRuleContext context, SyntaxNode constructor) {
        int modifiers = 0;
        if (context.hasDirectModifierToken(constructor, JavaTokenType.PUBLIC_KEYWORD))
            modifiers |= Modifier.PUBLIC;

        if (context.hasDirectModifierToken(constructor, JavaTokenType.PROTECTED_KEYWORD))
            modifiers |= Modifier.PROTECTED;

        if (context.hasDirectModifierToken(constructor, JavaTokenType.PRIVATE_KEYWORD))
            modifiers |= Modifier.PRIVATE;

        return modifiers;
    }

    private static boolean isAccessibleFromHere(JavaRuleContext context, String ownerQualifiedName, int modifiers) {
        if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
            return true;

        if (Modifier.isPrivate(modifiers))
            return false;

        return Objects.equals(context.currentPackageName(), context.packagePrefix(ownerQualifiedName));
    }

    private static String firstNonSerializableAncestor(JavaRuleContext context, String qualifiedName) {
        String current = qualifiedName;
        Set<String> visited = new HashSet<>();

        while (visited.add(current)) {
            String superClass = directSuperClassName(context, current);
            if (superClass == null)
                return null;

            if (!context.isSubtype(superClass, "java.io.Serializable"))
                return superClass;

            current = superClass;
        }

        return null;
    }

    private static String directSuperClassName(JavaRuleContext context, String qualifiedName) {
        for (String superType : context.directSuperTypeNames(qualifiedName)) {
            if (!context.isInterfaceType(superType))
                return superType;
        }

        return "java.lang.Object";
    }


    private static Map<String, SyntaxNode> localTypeDeclarations(JavaRuleContext context) {
        Map<String, SyntaxNode> result = new LinkedHashMap<>();
        context.traverse(node -> context.declaredSymbol(node).ifPresent(symbol -> {
            String qualifiedName = symbol.qualifiedName().orElse(null);
            if (qualifiedName != null && context.isTypeSymbol(symbol.kind()))
                result.putIfAbsent(qualifiedName, node);
        }));

        return Map.copyOf(result);
    }
}
