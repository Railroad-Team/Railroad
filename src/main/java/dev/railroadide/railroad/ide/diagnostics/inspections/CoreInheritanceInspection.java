package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.java.api.JavaTypes;
import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaDeclarationAnalysis;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Provides built-in Java inspections for {@link JavaSemanticRules#INVALID_INHERITANCE},
 * {@link JavaSemanticRules#MISSING_IMPLEMENTATION}, {@link JavaSemanticRules#INVALID_OVERRIDE},
 * {@link JavaSemanticRules#INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD},
 * {@link JavaSemanticRules#PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE}.
 */
@RegisteredInspection
public final class CoreInheritanceInspection implements JavaInspectionRuleProvider {
    /**
     * Stable identifier used to register this inspection provider.
     */
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
            CoreInheritanceInspection::reportInvalidInheritance),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.MISSING_IMPLEMENTATION.id(),
            JavaSemanticRules.MISSING_IMPLEMENTATION.defaultSeverity(),
            JavaSemanticRules.MISSING_IMPLEMENTATION.messageTemplate(),
            Set.of("core", "inheritance", "implementation"),
            CoreInheritanceInspection::reportMissingImplementation),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INVALID_OVERRIDE.id(),
            JavaSemanticRules.INVALID_OVERRIDE.defaultSeverity(),
            JavaSemanticRules.INVALID_OVERRIDE.messageTemplate(),
            Set.of("core", "inheritance", "override"),
            CoreInheritanceInspection::reportInvalidOverrides),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD.id(),
            JavaSemanticRules.INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD.defaultSeverity(),
            JavaSemanticRules.INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD.messageTemplate(),
            Set.of("core", "interface", "method-clash"),
            CoreInheritanceInspection::reportInterfaceMethodClashesWithObject),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE.id(),
            JavaSemanticRules.PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE.defaultSeverity(),
            JavaSemanticRules.PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE.messageTemplate(),
            Set.of("core", "api"),
            CoreInheritanceInspection::reportPublicMethodNotExposedByInterface));

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
                    if (!hasKnownSupertype(context, typeRef, inheritedType))
                        continue;

                    if (JAVA_CLASS_DECLARATION.equals(declarationNode.kind().id())
                        && context.isInterfaceType(inheritedType)) {
                        reporter.report(typeRef,
                            "class cannot extend interface '%s'".formatted(context.simpleTypeName(inheritedType)));
                    } else if ((JAVA_INTERFACE_DECLARATION.equals(declarationNode.kind().id())
                        || JAVA_ANNOTATION_TYPE_DECLARATION.equals(declarationNode.kind().id()))
                        && !context.isInterfaceType(inheritedType)) {
                        reporter.report(typeRef, "interface cannot extend non-interface '%s'"
                            .formatted(context.simpleTypeName(inheritedType)));
                    } else if (context.isFinalType(inheritedType)) {
                        reporter.report(typeRef,
                            "cannot extend final type '%s'".formatted(context.simpleTypeName(inheritedType)));
                    }
                }
            }

            SyntaxNode implementsClause = context.directChild(declarationNode, JAVA_IMPLEMENTS_CLAUSE);
            if (implementsClause == null)
                continue;

            for (SyntaxNode typeRef : directTypeReferences(implementsClause)) {
                String implementedType = context.resolveQualifiedTypeName(typeRef);
                if (!hasKnownSupertype(context, typeRef, implementedType))
                    continue;
                if (!context.isInterfaceType(implementedType)) {
                    reporter.report(typeRef,
                        "type cannot implement non-interface '%s'".formatted(context.simpleTypeName(implementedType)));
                }
            }
        }
    }

    private static boolean hasKnownSupertype(JavaRuleContext context, SyntaxNode reference, String name) {
        if (name == null || name.isBlank())
            return false;
        return context.isResolvableType(name) || context.inferredType(reference).map(type -> switch (type.kind()) {
            case JavaTypes.Kind.PRIMITIVE, JavaTypes.Kind.ARRAY, JavaTypes.Kind.TYPE_VARIABLE -> true;
            default -> false;
        }).orElse(false);
    }

    private static void reportMissingImplementation(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode declarationNode : localTypeDeclarationNodes(context)) {
            Symbol declared = context.declaredSymbol(declarationNode).orElse(null);
            if (declared == null)
                continue;
            if (JAVA_ENUM_DECLARATION.equals(declarationNode.kind().id())) {
                declared.qualifiedName()
                    .ifPresent(owner -> reportMissingEnumImplementations(context, reporter, declarationNode, owner));
                continue;
            }
            if (!isConcreteImplementingType(declared, declarationNode, context))
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
            for (JavaRuleContext.MethodDescriptor declaredMethod : context
                .declaredMethodDescriptors(ownerQualifiedName)) {
                if (!declaredMethod.isAbstract() && !Modifier.isStatic(declaredMethod.modifiers())) {
                    implemented.add(declaredMethod.signatureKey());
                }
            }
            for (JavaRuleContext.MethodDescriptor inheritedMethod : context
                .inheritedMethodDescriptors(ownerQualifiedName)) {
                if (!inheritedMethod.isAbstract() && !Modifier.isStatic(inheritedMethod.modifiers())) {
                    implemented.add(inheritedMethod.signatureKey());
                }
            }

            for (JavaRuleContext.MethodDescriptor missing : required.values()) {
                if (!implemented.contains(missing.signatureKey())) {
                    reporter.report(declarationNode, missing.signatureKey());
                }
            }
        }
    }

    private static void reportMissingEnumImplementations(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter,
        SyntaxNode declaration,
        String owner
    ) {
        List<JavaRuleContext.MethodDescriptor> required = context.declaredMethodDescriptors(owner).stream()
            .filter(JavaRuleContext.MethodDescriptor::isAbstract).toList();
        SyntaxNode body = context.directChild(declaration, "JAVA_ENUM_BODY");
        if (required.isEmpty() || body == null)
            return;
        List<SyntaxNode> constants = body.children().stream()
            .filter(child -> "JAVA_ENUM_CONSTANT".equals(child.kind().id())).toList();
        if (constants.isEmpty()) {
            required.forEach(method -> reporter.report(nodeFor(method, declaration), method.signatureKey()));
            return;
        }
        for (SyntaxNode constant : constants) {
            SyntaxNode classBody = context.directChild(constant, "JAVA_ANONYMOUS_CLASS_BODY");
            List<JavaRuleContext.MethodDescriptor> methods = classBody == null
                ? List.of()
                : context.declaredSymbol(classBody).flatMap(Symbol::qualifiedName)
                    .map(context::declaredMethodDescriptors).orElse(List.of());
            Set<String> implemented = new HashSet<>();
            for (var method : methods) {
                if (!method.isAbstract() && !Modifier.isStatic(method.modifiers())) {
                    implemented.add(erasedMethodSignature(method));
                }
            }
            for (var method : required) {
                if (!implemented.contains(erasedMethodSignature(method))) {
                    reporter.report(constant, method.signatureKey());
                }
            }
        }
    }

    private static String erasedMethodSignature(JavaRuleContext.MethodDescriptor method) {
        if (method.symbol() == null)
            return method.signatureKey();
        return method.symbol().id().map(JavaSymbolIds::components).map(List::getLast)
            .filter(component -> component.kind().equals("parameters"))
            .map(parameters -> method.name() + "(" + parameters.value() + ")")
            .orElseGet(method::signatureKey);
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
                inheritedBySignature.computeIfAbsent(inherited.signatureKey(), _ -> new ArrayList<>()).add(inherited);
            }

            Set<String> declaredSignatures = new HashSet<>();
            for (JavaRuleContext.MethodDescriptor declaredMethod : context
                .declaredMethodDescriptors(ownerQualifiedName)) {
                declaredSignatures.add(declaredMethod.signatureKey());
                List<JavaRuleContext.MethodDescriptor> overridden = inheritedBySignature
                    .get(declaredMethod.signatureKey());
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

                    if (!isReturnTypeCompatible(context, declarationNode, inherited, declaredMethod)) {
                        reporter.report(nodeFor(declaredMethod, declarationNode), declaredMethod.name());
                        break;
                    }

                    if (!areThrownTypesCompatible(context, inherited, declaredMethod)) {
                        reporter.report(nodeFor(declaredMethod, declarationNode), declaredMethod.name());
                        break;
                    }
                }
            }

            for (var entry : inheritedBySignature.entrySet()) {
                if (!declaredSignatures.contains(entry.getKey())
                    && hasConflictingInheritedMethods(context, declarationNode, entry.getValue())) {
                    reporter.report(declarationNode, entry.getValue().getFirst().name());
                }
            }
        }
    }

    private static void reportInterfaceMethodClashesWithObject(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter
    ) {
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
                    if (!isCloneCompatibleWithObjectClone(context, method)) {
                        reporter.report(nodeFor(method, declarationNode), method.signatureKey());
                    }
                } else if ("finalize".equals(method.name())) {
                    if (!isFinalizeCompatibleWithObjectFinalize(method)) {
                        reporter.report(nodeFor(method, declarationNode), method.signatureKey());
                    }
                }
            }
        }
    }

    private static void reportPublicMethodNotExposedByInterface(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter
    ) {
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

    private static boolean isConcreteImplementingType(
        Symbol symbol,
        SyntaxNode declarationNode,
        JavaRuleContext context
    ) {
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
                && !JAVA_RECORD_DECLARATION.equals(kindId) && !JavaDeclarationAnalysis.isEnumConstantBody(node))
                return;
            if (context.declaredSymbol(node).isPresent()) {
                nodes.add(node);
            }
        });
        return List.copyOf(nodes);
    }

    private static List<SyntaxNode> directTypeReferences(SyntaxNode clauseNode) {
        List<SyntaxNode> refs = new ArrayList<>();
        for (SyntaxNode child : clauseNode.children()) {
            if (JAVA_TYPE_REFERENCE.equals(child.kind().id())) {
                refs.add(child);
            }
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

    private static boolean isReturnTypeCompatible(
        JavaRuleContext context,
        SyntaxNode ownerDeclaration,
        JavaRuleContext.MethodDescriptor base,
        JavaRuleContext.MethodDescriptor candidate
    ) {
        if (!candidate.typeParameters().isEmpty() && !candidate.hasEquivalentTypeParameters(base))
            return false;
        Type inherited = base.overrideReturnType();
        Type declared = candidate.overrideReturnType();
        while (inherited instanceof JavaTypes.ArrayType baseArray
            && declared instanceof JavaTypes.ArrayType candidateArray) {
            inherited = baseArray.componentType();
            declared = candidateArray.componentType();
        }
        if (Objects.equals(inherited.displayName(), declared.displayName()))
            return true;
        if (inherited.kind() == JavaTypes.Kind.PRIMITIVE || declared.kind() == JavaTypes.Kind.PRIMITIVE)
            return false;
        if (inherited.kind() == JavaTypes.Kind.DECLARED && declared.kind() == JavaTypes.Kind.DECLARED)
            return context.isSubtype(declared.displayName(), inherited.displayName());
        return context.isAssignable(inherited, declared, ownerDeclaration);
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
                if (declaredThrownType.equals(inheritedThrownType)
                    || context.isSubtype(declaredThrownType, inheritedThrownType)) {
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
        SyntaxNode ownerDeclaration,
        List<JavaRuleContext.MethodDescriptor> inheritedGroup
    ) {
        if (inheritedGroup.size() < 2)
            return false;
        var methods = inheritedGroup.stream().filter(method -> !Modifier.isStatic(method.modifiers())).toList();
        return methods.size() > 1 && methods.stream().noneMatch(candidate -> methods.stream()
            .allMatch(base -> base == candidate || isReturnTypeCompatible(context, ownerDeclaration, base, candidate)));
    }

    private static SyntaxNode nodeFor(JavaRuleContext.MethodDescriptor descriptor, SyntaxNode fallback) {
        return descriptor.declaration() == null ? fallback : descriptor.declaration();
    }

    private static boolean isCloneCompatibleWithObjectClone(
        JavaRuleContext context,
        JavaRuleContext.MethodDescriptor method
    ) {
        Type returnType = method.returnType();
        if (returnType.kind() != JavaTypes.Kind.DECLARED)
            return false;

        if (!context.isSubtype(returnType.displayName(), "java.lang.Object")
            && !"java.lang.Object".equals(returnType.displayName()))
            return false;

        for (String thrownType : method.thrownTypes()) {
            if (!context.isCheckedExceptionType(thrownType))
                continue;

            if (!"java.lang.CloneNotSupportedException".equals(thrownType)
                && !context.isSubtype(thrownType, "java.lang.CloneNotSupportedException"))
                return false;
        }

        return true;
    }

    private static boolean isFinalizeCompatibleWithObjectFinalize(JavaRuleContext.MethodDescriptor method) {
        if (method.returnType().kind() != JavaTypes.Kind.VOID)
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

    private static void collectImplementedInterfaces(
        JavaRuleContext context,
        String qualifiedName,
        Set<String> signatures,
        Set<String> visited
    ) {
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
