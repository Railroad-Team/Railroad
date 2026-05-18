package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.lang.reflect.Modifier;
import java.util.*;

@RegisteredInspection
public final class CoreInheritanceInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-inheritance";

    private static final String JAVA_CLASS_DECLARATION = "JAVA_CLASS_DECLARATION";
    private static final String JAVA_INTERFACE_DECLARATION = "JAVA_INTERFACE_DECLARATION";
    private static final String JAVA_ENUM_DECLARATION = "JAVA_ENUM_DECLARATION";
    private static final String JAVA_ANNOTATION_TYPE_DECLARATION = "JAVA_ANNOTATION_TYPE_DECLARATION";
    private static final String JAVA_RECORD_DECLARATION = "JAVA_RECORD_DECLARATION";
    private static final String JAVA_TYPE_REFERENCE = "JAVA_TYPE_REFERENCE";
    private static final String JAVA_EXTENDS_CLAUSE = "JAVA_EXTENDS_CLAUSE";
    private static final String JAVA_IMPLEMENTS_CLAUSE = "JAVA_IMPLEMENTS_CLAUSE";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INVALID_INHERITANCE.id(),
            JavaSemanticRules.INVALID_INHERITANCE.defaultSeverity(),
            JavaSemanticRules.INVALID_INHERITANCE.messageTemplate(),
            Set.of("core", "inheritance"),
            CoreInheritanceInspection::reportInvalidInheritance
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.MISSING_IMPLEMENTATION.id(),
            JavaSemanticRules.MISSING_IMPLEMENTATION.defaultSeverity(),
            JavaSemanticRules.MISSING_IMPLEMENTATION.messageTemplate(),
            Set.of("core", "inheritance", "implementation"),
            CoreInheritanceInspection::reportMissingImplementation
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INVALID_OVERRIDE.id(),
            JavaSemanticRules.INVALID_OVERRIDE.defaultSeverity(),
            JavaSemanticRules.INVALID_OVERRIDE.messageTemplate(),
            Set.of("core", "inheritance", "override"),
            CoreInheritanceInspection::reportInvalidOverrides
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD.id(),
            JavaSemanticRules.INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD.defaultSeverity(),
            JavaSemanticRules.INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD.messageTemplate(),
            Set.of("core", "interface", "method-clash"),
            CoreInheritanceInspection::reportInterfaceMethodClashesWithObject
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE.id(),
            JavaSemanticRules.PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE.defaultSeverity(),
            JavaSemanticRules.PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE.messageTemplate(),
            Set.of("core", "api"),
            CoreInheritanceInspection::reportPublicMethodNotExposedByInterface
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

    private static void reportInvalidInheritance(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode declarationNode : localTypeDeclarationNodes(context)) {
            Symbol declared = context.declaredSymbol(declarationNode).orElse(null);
            if (declared == null)
                continue;

            SyntaxNode extendsClause = context.directChild(declarationNode, JAVA_EXTENDS_CLAUSE);
            if (extendsClause != null) {
                for (SyntaxNode typeRef : directTypeReferences(extendsClause)) {
                    String inheritedType = context.resolveQualifiedTypeName(typeRef);
                    if (inheritedType == null || inheritedType.isBlank())
                        continue;

                    if (JAVA_CLASS_DECLARATION.equals(declarationNode.kind().id())
                        && context.isInterfaceType(inheritedType)) {
                        reporter.report(typeRef, "class cannot extend interface '%s'".formatted(context.simpleTypeName(inheritedType)));
                    } else if ((JAVA_INTERFACE_DECLARATION.equals(declarationNode.kind().id())
                        || JAVA_ANNOTATION_TYPE_DECLARATION.equals(declarationNode.kind().id()))
                        && !context.isInterfaceType(inheritedType)) {
                        reporter.report(typeRef, "interface cannot extend non-interface '%s'".formatted(context.simpleTypeName(inheritedType)));
                    } else if (context.isFinalType(inheritedType)) {
                        reporter.report(typeRef, "cannot extend final type '%s'".formatted(context.simpleTypeName(inheritedType)));
                    }
                }
            }

            SyntaxNode implementsClause = context.directChild(declarationNode, JAVA_IMPLEMENTS_CLAUSE);
            if (implementsClause == null)
                continue;

            for (SyntaxNode typeRef : directTypeReferences(implementsClause)) {
                String implementedType = context.resolveQualifiedTypeName(typeRef);
                if (implementedType == null || implementedType.isBlank())
                    continue;
                if (!context.isInterfaceType(implementedType))
                    reporter.report(typeRef, "type cannot implement non-interface '%s'".formatted(context.simpleTypeName(implementedType)));
            }
        }
    }

    private static void reportMissingImplementation(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode declarationNode : localTypeDeclarationNodes(context)) {
            Symbol declared = context.declaredSymbol(declarationNode).orElse(null);
            if (declared == null || !isConcreteImplementingType(declared, declarationNode, context))
                continue;

            String ownerQualifiedName = declared.qualifiedName().orElse(null);
            if (ownerQualifiedName == null)
                continue;

            Map<String, JavaRuleContext.MethodDescriptor> required = new LinkedHashMap<>();
            for (JavaRuleContext.MethodDescriptor inherited : context.inheritedMethodDescriptors(ownerQualifiedName)) {
                if (inherited.isAbstract()
                    && !Modifier.isStatic(inherited.modifiers())
                    && !Modifier.isPrivate(inherited.modifiers())) {
                    required.putIfAbsent(inherited.signatureKey(), inherited);
                }
            }

            if (required.isEmpty())
                continue;

            Set<String> implemented = new LinkedHashSet<>();
            for (JavaRuleContext.MethodDescriptor declaredMethod : context.declaredMethodDescriptors(ownerQualifiedName)) {
                if (!declaredMethod.isAbstract() && !Modifier.isStatic(declaredMethod.modifiers()))
                    implemented.add(declaredMethod.signatureKey());
            }
            for (JavaRuleContext.MethodDescriptor inheritedMethod : context.inheritedMethodDescriptors(ownerQualifiedName)) {
                if (!inheritedMethod.isAbstract() && !Modifier.isStatic(inheritedMethod.modifiers()))
                    implemented.add(inheritedMethod.signatureKey());
            }

            for (JavaRuleContext.MethodDescriptor missing : required.values()) {
                if (!implemented.contains(missing.signatureKey()))
                    reporter.report(declarationNode, missing.signatureKey());
            }
        }
    }

    private static void reportInvalidOverrides(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode declarationNode : localTypeDeclarationNodes(context)) {
            Symbol declared = context.declaredSymbol(declarationNode).orElse(null);
            if (declared == null)
                continue;

            String ownerQualifiedName = declared.qualifiedName().orElse(null);
            if (ownerQualifiedName == null)
                continue;

            Map<String, List<JavaRuleContext.MethodDescriptor>> inheritedBySignature = new LinkedHashMap<>();
            for (JavaRuleContext.MethodDescriptor inherited : context.inheritedMethodDescriptors(ownerQualifiedName)) {
                if (Modifier.isPrivate(inherited.modifiers()))
                    continue;
                inheritedBySignature.computeIfAbsent(inherited.signatureKey(), ignored -> new ArrayList<>()).add(inherited);
            }

            for (List<JavaRuleContext.MethodDescriptor> inheritedGroup : inheritedBySignature.values()) {
                if (hasConflictingInheritedMethods(context, inheritedGroup))
                    reporter.report(declarationNode, inheritedGroup.getFirst().name());
            }

            for (JavaRuleContext.MethodDescriptor declaredMethod : context.declaredMethodDescriptors(ownerQualifiedName)) {
                List<JavaRuleContext.MethodDescriptor> overridden = inheritedBySignature.get(declaredMethod.signatureKey());
                if (overridden == null || overridden.isEmpty())
                    continue;

                for (JavaRuleContext.MethodDescriptor inherited : overridden) {
                    if (Modifier.isFinal(inherited.modifiers())) {
                        reporter.report(nodeFor(declaredMethod, declarationNode), declaredMethod.name());
                        break;
                    }

                    if (Modifier.isStatic(declaredMethod.modifiers()) != Modifier.isStatic(inherited.modifiers())) {
                        reporter.report(nodeFor(declaredMethod, declarationNode), declaredMethod.name());
                        break;
                    }

                    if (accessRank(declaredMethod.modifiers()) < accessRank(inherited.modifiers())) {
                        reporter.report(nodeFor(declaredMethod, declarationNode), declaredMethod.name());
                        break;
                    }

                    if (!isReturnTypeCompatible(context, inherited.returnType(), declaredMethod.returnType())) {
                        reporter.report(nodeFor(declaredMethod, declarationNode), declaredMethod.name());
                        break;
                    }

                    if (!areThrownTypesCompatible(context, inherited, declaredMethod)) {
                        reporter.report(nodeFor(declaredMethod, declarationNode), declaredMethod.name());
                        break;
                    }
                }
            }
        }
    }

    private static void reportInterfaceMethodClashesWithObject(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode declarationNode : localTypeDeclarationNodes(context)) {
            if (!JAVA_INTERFACE_DECLARATION.equals(declarationNode.kind().id()))
                continue;

            Symbol declared = context.declaredSymbol(declarationNode).orElse(null);
            if (declared == null)
                continue;

            String ownerQualifiedName = declared.qualifiedName().orElse(null);
            if (ownerQualifiedName == null)
                continue;

            for (JavaRuleContext.MethodDescriptor method : context.declaredMethodDescriptors(ownerQualifiedName)) {
                if (Modifier.isStatic(method.modifiers()) || Modifier.isPrivate(method.modifiers()))
                    continue;

                if (!method.parameterTypes().isEmpty())
                    continue;

                if ("clone".equals(method.name())) {
                    if (!isCloneCompatibleWithObjectClone(context, method))
                        reporter.report(nodeFor(method, declarationNode), method.signatureKey());
                } else if ("finalize".equals(method.name())) {
                    if (!isFinalizeCompatibleWithObjectFinalize(method))
                        reporter.report(nodeFor(method, declarationNode), method.signatureKey());
                }
            }
        }
    }

    private static void reportPublicMethodNotExposedByInterface(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode declarationNode : localTypeDeclarationNodes(context)) {
            if (!JAVA_CLASS_DECLARATION.equals(declarationNode.kind().id()))
                continue;

            Symbol declared = context.declaredSymbol(declarationNode).orElse(null);
            if (declared == null)
                continue;

            String ownerQualifiedName = declared.qualifiedName().orElse(null);
            if (ownerQualifiedName == null)
                continue;

            if (context.isAbstractType(ownerQualifiedName))
                continue;

            if (extendsNonObjectSuperclass(context, ownerQualifiedName))
                continue;

            Set<String> interfaceMethodSignatures = exposedInterfaceMethodSignatures(context, ownerQualifiedName);
            if (interfaceMethodSignatures.isEmpty())
                continue;

            for (JavaRuleContext.MethodDescriptor method : context.declaredMethodDescriptors(ownerQualifiedName)) {
                if (!Modifier.isPublic(method.modifiers()) || Modifier.isStatic(method.modifiers()))
                    continue;

                if (isObjectMethod(context, method))
                    continue;

                if (interfaceMethodSignatures.contains(method.signatureKey()))
                    continue;

                reporter.report(nodeFor(method, declarationNode), method.signatureKey());
            }
        }
    }

    private static boolean isConcreteImplementingType(Symbol symbol, SyntaxNode declarationNode, JavaRuleContext context) {
        String kindId = declarationNode.kind().id();
        if (!JAVA_CLASS_DECLARATION.equals(kindId) && !JAVA_RECORD_DECLARATION.equals(kindId))
            return false;
        String qualifiedName = symbol.qualifiedName().orElse(null);
        return qualifiedName != null && !context.isAbstractType(qualifiedName);
    }

    private static List<SyntaxNode> localTypeDeclarationNodes(JavaRuleContext context) {
        List<SyntaxNode> nodes = new ArrayList<>();
        context.traverse(node -> {
            String kindId = node.kind().id();
            if (!JAVA_CLASS_DECLARATION.equals(kindId)
                && !JAVA_INTERFACE_DECLARATION.equals(kindId)
                && !JAVA_ENUM_DECLARATION.equals(kindId)
                && !JAVA_ANNOTATION_TYPE_DECLARATION.equals(kindId)
                && !JAVA_RECORD_DECLARATION.equals(kindId)) {
                return;
            }
            if (context.declaredSymbol(node).isPresent())
                nodes.add(node);
        });
        return List.copyOf(nodes);
    }

    private static List<SyntaxNode> directTypeReferences(SyntaxNode clauseNode) {
        List<SyntaxNode> refs = new ArrayList<>();
        for (SyntaxNode child : clauseNode.children()) {
            if (JAVA_TYPE_REFERENCE.equals(child.kind().id()))
                refs.add(child);
        }
        return List.copyOf(refs);
    }

    private static int accessRank(int modifiers) {
        if (Modifier.isPublic(modifiers))
            return 3;
        if (Modifier.isProtected(modifiers))
            return 2;
        if (Modifier.isPrivate(modifiers))
            return 0;
        return 1;
    }

    private static boolean isReturnTypeCompatible(JavaRuleContext context, Type inherited, Type declared) {
        if (Objects.equals(inherited.displayName(), declared.displayName()))
            return true;
        if (inherited.kind() == Type.Kind.DECLARED && declared.kind() == Type.Kind.DECLARED)
            return context.isSubtype(declared.displayName(), inherited.displayName());
        return context.isAssignable(inherited, declared);
    }

    private static boolean areThrownTypesCompatible(
        JavaRuleContext context,
        JavaRuleContext.MethodDescriptor inherited,
        JavaRuleContext.MethodDescriptor declared
    ) {
        for (String declaredThrownType : declared.thrownTypes()) {
            if (!context.isCheckedExceptionType(declaredThrownType))
                continue;

            boolean compatible = false;
            for (String inheritedThrownType : inherited.thrownTypes()) {
                if (!context.isCheckedExceptionType(inheritedThrownType))
                    continue;
                if (declaredThrownType.equals(inheritedThrownType) || context.isSubtype(declaredThrownType, inheritedThrownType)) {
                    compatible = true;
                    break;
                }
            }

            if (!compatible)
                return false;
        }
        return true;
    }

    private static boolean hasConflictingInheritedMethods(
        JavaRuleContext context,
        List<JavaRuleContext.MethodDescriptor> inheritedGroup
    ) {
        for (int i = 0; i < inheritedGroup.size(); i++) {
            JavaRuleContext.MethodDescriptor left = inheritedGroup.get(i);
            if (Modifier.isStatic(left.modifiers()))
                continue;
            for (int j = i + 1; j < inheritedGroup.size(); j++) {
                JavaRuleContext.MethodDescriptor right = inheritedGroup.get(j);
                if (Modifier.isStatic(right.modifiers()))
                    continue;
                if (!areReturnTypesMutuallyCompatible(context, left.returnType(), right.returnType()))
                    return true;
            }
        }
        return false;
    }

    private static boolean areReturnTypesMutuallyCompatible(JavaRuleContext context, Type left, Type right) {
        return isReturnTypeCompatible(context, left, right) || isReturnTypeCompatible(context, right, left);
    }

    private static SyntaxNode nodeFor(JavaRuleContext.MethodDescriptor descriptor, SyntaxNode fallback) {
        return descriptor.declaration() == null ? fallback : descriptor.declaration();
    }

    private static boolean isCloneCompatibleWithObjectClone(JavaRuleContext context, JavaRuleContext.MethodDescriptor method) {
        Type returnType = method.returnType();
        if (returnType.kind() != Type.Kind.DECLARED)
            return false;

        if (!context.isSubtype(returnType.displayName(), "java.lang.Object") && !"java.lang.Object".equals(returnType.displayName()))
            return false;

        for (String thrownType : method.thrownTypes()) {
            if (!context.isCheckedExceptionType(thrownType))
                continue;

            if (!"java.lang.CloneNotSupportedException".equals(thrownType) && !context.isSubtype(thrownType, "java.lang.CloneNotSupportedException"))
                return false;
        }

        return true;
    }

    private static boolean isFinalizeCompatibleWithObjectFinalize(JavaRuleContext.MethodDescriptor method) {
        if (method.returnType().kind() != Type.Kind.VOID)
            return false;

        for (String thrownType : method.thrownTypes()) {
            if (!"java.lang.Throwable".equals(thrownType))
                return false;
        }

        return true;
    }

    private static boolean extendsNonObjectSuperclass(JavaRuleContext context, String qualifiedName) {
        for (String superType : context.directSuperTypeNames(qualifiedName)) {
            if (!context.isInterfaceType(superType) && !"java.lang.Object".equals(superType))
                return true;
        }

        return false;
    }

    private static Set<String> exposedInterfaceMethodSignatures(JavaRuleContext context, String qualifiedName) {
        Set<String> interfaceTypes = new LinkedHashSet<>();
        collectImplementedInterfaces(context, qualifiedName, interfaceTypes, new HashSet<>());

        Set<String> signatures = new LinkedHashSet<>();
        for (String interfaceType : interfaceTypes) {
            for (JavaRuleContext.MethodDescriptor method : context.declaredMethodDescriptors(interfaceType)) {
                if (!Modifier.isStatic(method.modifiers()) && !Modifier.isPrivate(method.modifiers())) {
                    signatures.add(method.signatureKey());
                }
            }

            for (JavaRuleContext.MethodDescriptor method : context.inheritedMethodDescriptors(interfaceType)) {
                if (!Modifier.isStatic(method.modifiers()) && !Modifier.isPrivate(method.modifiers())) {
                    signatures.add(method.signatureKey());
                }
            }
        }

        return Set.copyOf(signatures);
    }

    private static void collectImplementedInterfaces(JavaRuleContext context, String qualifiedName, Set<String> signatures, Set<String> visited) {
        if (!visited.add(qualifiedName))
            return;

        for (String superType : context.directSuperTypeNames(qualifiedName)) {
            if (context.isInterfaceType(superType)) {
                signatures.add(superType);
                collectImplementedInterfaces(context, superType, signatures, visited);
            }
        }
    }

    private static boolean isObjectMethod(JavaRuleContext context, JavaRuleContext.MethodDescriptor method) {
        for (JavaRuleContext.MethodDescriptor objectMethod : context.declaredMethodDescriptors("java.lang.Object")) {
            if (method.signatureKey().equals(objectMethod.signatureKey()))
                return true;
        }

        return false;
    }
}
